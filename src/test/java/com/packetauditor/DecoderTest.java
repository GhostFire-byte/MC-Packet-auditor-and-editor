package com.packetauditor;

import com.packetauditor.codec.DecoderService;
import com.packetauditor.codec.ReflectiveDecoder;
import com.packetauditor.codec.ValueParser;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.Direction;
import com.packetauditor.model.FieldType;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.model.ProtocolPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoderTest {

    @Test
    void reflectiveDecodeRecord() {
        var decoded = new ReflectiveDecoder().decode(new TestFixtures.FakeMovePacket(1, 2, 3, true));
        assertEquals(DecodedPacket.DecodeSource.REFLECTIVE, decoded.source());
        assertTrue(decoded.field("x").isPresent());
        assertEquals(2.0, decoded.field("y").get().value());
        assertEquals(FieldType.BOOLEAN, decoded.field("onGround").get().type());
    }

    @Test
    void reflectiveDecodePojo() {
        var decoded = new ReflectiveDecoder().decode(new TestFixtures.FakeChatPojo("hi", 5));
        assertTrue(decoded.field("message").isPresent());
        assertEquals("hi", decoded.field("message").get().value());
        assertEquals(5, decoded.field("count").get().value());
    }

    @Test
    void reflectiveDecodeNullIsRawOnly() {
        var decoded = new ReflectiveDecoder().decode(null);
        assertEquals(DecodedPacket.DecodeSource.RAW_ONLY, decoded.source());
    }

    @Test
    void decoderServicePrefersCuratedAdapter() {
        DecoderService svc = new DecoderService();
        svc.registerAdapter(new TestFixtures.FakeMoveAdapter());
        PacketMeta meta = PacketMeta.builder().sequence(1).direction(Direction.OUTBOUND)
                .phase(ProtocolPhase.PLAY).packetName("FakeMovePacket").build();
        CapturedPacket p = new CapturedPacket(meta, new TestFixtures.FakeMovePacket(1, 2, 3, true), null);
        DecodedPacket decoded = svc.decode(p);
        assertEquals(DecodedPacket.DecodeSource.CURATED_ADAPTER, decoded.source());
        assertTrue(decoded.editable());
        assertTrue(decoded.field("x").get().editable());
    }

    @Test
    void valueParserParsesTypes() {
        assertEquals(42, ValueParser.parse(FieldType.INT, "42"));
        assertEquals(1.5, ValueParser.parse(FieldType.DOUBLE, "1.5"));
        assertEquals(Boolean.TRUE, ValueParser.parse(FieldType.BOOLEAN, "true"));
        assertEquals("abc", ValueParser.parse(FieldType.STRING, "abc"));
    }

    @Test
    void valueParserRejectsBadInput() {
        assertNotNull(ValueParser.validate(FieldType.INT, "notanumber"));
        assertNull(ValueParser.validate(FieldType.INT, "7"));
    }

    @Test
    void recordRebuildAdapterEditsViaConstructor() throws Exception {
        var adapter = com.packetauditor.codec.RecordRebuildAdapter.forClass(
                TestFixtures.FakeMovePacket.class);
        TestFixtures.FakeMovePacket p = new TestFixtures.FakeMovePacket(1, 2, 3, true);
        assertTrue(adapter.supports(p));
        DecodedPacket decoded = adapter.decode(p);
        assertTrue(decoded.field("y").get().editable());
        Object edited = adapter.applyEdit(p, "y", 99.0);
        assertEquals(99.0, ((TestFixtures.FakeMovePacket) edited).y());
        // other fields preserved
        assertEquals(1.0, ((TestFixtures.FakeMovePacket) edited).x());
        assertTrue(((TestFixtures.FakeMovePacket) edited).onGround());
    }

    @Test
    void recordRebuildAdapterRestrictsEditableFields() {
        var adapter = com.packetauditor.codec.RecordRebuildAdapter.forClass(
                TestFixtures.FakeMovePacket.class, "x");
        DecodedPacket decoded = adapter.decode(new TestFixtures.FakeMovePacket(1, 2, 3, true));
        assertTrue(decoded.field("x").get().editable());
        assertFalse(decoded.field("y").get().editable());
    }
}
