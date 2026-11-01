package com.packetauditor;

import com.packetauditor.codec.PacketEditAdapter;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.FieldType;
import com.packetauditor.model.PacketField;

import java.util.List;

/**
 * Lightweight test fixtures: a fake "player move" packet modelled as an immutable
 * record (mirroring how real MC 26.2 packets are records) and a curated edit
 * adapter that rebuilds a new record on edit — exactly the D7 approach, but with
 * no Minecraft dependency (D11: tests use lightweight packet-adapter mocks).
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** Immutable record standing in for a real native MC packet. */
    public record FakeMovePacket(double x, double y, double z, boolean onGround) {
    }

    /** A record whose name marks it as an essential (Tier-1) packet. */
    public record FakeKeepAlivePacket(long id) {
    }

    /** A simple non-record POJO to exercise the reflective field decoder. */
    public static final class FakeChatPojo {
        public String message;
        public int count;
        public FakeChatPojo(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    /** Curated editable adapter for {@link FakeMovePacket}. */
    public static final class FakeMoveAdapter implements PacketEditAdapter {

        @Override
        public boolean supports(Object nativePacket) {
            return nativePacket instanceof FakeMovePacket;
        }

        @Override
        public String label() {
            return "Fake Player Move";
        }

        @Override
        public DecodedPacket decode(Object nativePacket) {
            FakeMovePacket p = (FakeMovePacket) nativePacket;
            List<PacketField> fields = List.of(
                    PacketField.scalar("x", FieldType.DOUBLE, p.x(), true),
                    PacketField.scalar("y", FieldType.DOUBLE, p.y(), true),
                    PacketField.scalar("z", FieldType.DOUBLE, p.z(), true),
                    PacketField.scalar("onGround", FieldType.BOOLEAN, p.onGround(), true));
            return DecodedPacket.curated(fields, true, null);
        }

        @Override
        public Object applyEdit(Object nativePacket, String fieldPath, Object newValue)
                throws EditException {
            FakeMovePacket p = (FakeMovePacket) nativePacket;
            switch (fieldPath) {
                case "x": return new FakeMovePacket(toD(newValue), p.y(), p.z(), p.onGround());
                case "y": return new FakeMovePacket(p.x(), toD(newValue), p.z(), p.onGround());
                case "z": return new FakeMovePacket(p.x(), p.y(), toD(newValue), p.onGround());
                case "onGround": return new FakeMovePacket(p.x(), p.y(), p.z(), (Boolean) newValue);
                default: throw new EditException("Unknown field: " + fieldPath);
            }
        }

        private static double toD(Object v) {
            return ((Number) v).doubleValue();
        }
    }
}
