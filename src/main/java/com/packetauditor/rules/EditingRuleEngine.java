package com.packetauditor.rules;

import com.packetauditor.codec.DecoderService;
import com.packetauditor.codec.PacketEditAdapter;
import com.packetauditor.codec.ValueParser;
import com.packetauditor.match.MatchMode;
import com.packetauditor.model.CapturedPacket;
import com.packetauditor.model.DecodedPacket;
import com.packetauditor.model.PacketField;
import com.packetauditor.model.PacketMeta;
import com.packetauditor.protect.OperationType;
import com.packetauditor.protect.ProtectionDecision;
import com.packetauditor.protect.ProtectionPolicy;
import com.packetauditor.util.AuditorLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Stores editing rules and applies edits — both automatically (matching rules)
 * and manually (structured editor). Enforces protection ({@link ProtectionPolicy})
 * and records every change into the modification-history sink (spec §5).
 *
 * <p>All editing is <b>crash-safe</b>: every edit is actually applied inside a
 * guarded block and only reported as applied once a new native packet has been
 * produced, so a faulty adapter can never silently misreport, corrupt a packet,
 * or throw into the networking thread. A {@link #previewEdit} dry-run lets the UI
 * warn before saving.</p>
 */
public final class EditingRuleEngine {

    private static final AuditorLog LOG = AuditorLog.of(EditingRuleEngine.class);

    private final DecoderService decoder;
    private final ProtectionPolicy protection;
    private final List<EditingRule> rules = new CopyOnWriteArrayList<>();
    private volatile MatchMode matchMode = MatchMode.ADVANCED;
    private volatile Consumer<ModificationRecord> historySink = r -> { };

    public EditingRuleEngine(DecoderService decoder, ProtectionPolicy protection) {
        this.decoder = decoder;
        this.protection = protection;
    }

    public void setMatchMode(MatchMode mode) {
        this.matchMode = mode == null ? MatchMode.ADVANCED : mode;
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public void setHistorySink(Consumer<ModificationRecord> sink) {
        this.historySink = sink == null ? r -> { } : sink;
    }

    public void addRule(EditingRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    public void removeRule(String ruleId) {
        rules.removeIf(r -> r.id().equals(ruleId));
    }

    public List<EditingRule> rules() {
        return rules;
    }

    /** True if any rule is enabled and valid under the current match mode. */
    public boolean hasActiveRules() {
        for (EditingRule r : rules) {
            if (r.isActiveUnder(matchMode)) {
                return true;
            }
        }
        return false;
    }

    public List<EditingRule> activeRulesSortedByPriority() {
        List<EditingRule> active = new ArrayList<>();
        for (EditingRule r : rules) {
            if (r.isActiveUnder(matchMode)) {
                active.add(r);
            }
        }
        active.sort(Comparator.comparingInt(EditingRule::priority).reversed());
        return active;
    }

    /**
     * Apply all matching active rules to a packet in priority order. Any single
     * failing replacement is skipped (logged) without aborting the others or
     * throwing.
     */
    public EditResult applyMatchingRules(CapturedPacket packet) {
        try {
            DecodedPacket decoded = decoder.decode(packet);
            PacketEditAdapter adapter = decoder.adapterFor(packet.nativePacket());
            Object current = packet.nativePacket();
            List<ModificationRecord> mods = new ArrayList<>();

            if (adapter == null) {
                return EditResult.unchanged(current, "No editable adapter for this packet type.");
            }

            for (EditingRule rule : activeRulesSortedByPriority()) {
                if (!rule.matchSpec().matches(packet, decoded)) {
                    continue;
                }
                ProtectionDecision decision = protection.evaluate(packet.meta(), OperationType.EDIT);
                if (decision.blocked()) {
                    LOG.debug("Rule '" + rule.name() + "' blocked: " + decision.reason());
                    continue;
                }
                for (FieldReplacement rep : rule.replacements()) {
                    EditOutcome outcome = applyFieldTo(adapter, packet.meta(), current,
                            rep.fieldPath(), rep.valueText(), rule.id(), rule.name());
                    if (outcome.success) {
                        current = outcome.newNative;
                        mods.add(outcome.record);
                        historySink.accept(outcome.record);
                    } else {
                        LOG.debug("Rule '" + rule.name() + "' replacement " + rep.fieldPath()
                                + " skipped: " + outcome.message);
                    }
                }
            }

            if (mods.isEmpty()) {
                return EditResult.unchanged(current, "No rules applied.");
            }
            return EditResult.applied(current, mods);
        } catch (Throwable t) {
            // Never let rule application throw into the pipeline / networking thread.
            LOG.warn("applyMatchingRules failed; forwarding original: " + t, t);
            return EditResult.unchanged(packet.nativePacket(),
                    "Edit error: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Manually edit a single field (structured editor). Applies the edit, records
     * history on success, and returns an accurate {@link EditResult} with a clear
     * message on any failure (never throws).
     */
    public EditResult editField(CapturedPacket packet, String fieldPath, String valueText) {
        return doManualEdit(packet, fieldPath, valueText, true);
    }

    /**
     * Dry-run an edit without recording history or side effects, so the UI can
     * warn the user <i>before</i> they save a change that would fail or risk a
     * crash. Returns applied=true if the edit would succeed.
     */
    public EditResult previewEdit(CapturedPacket packet, String fieldPath, String valueText) {
        return doManualEdit(packet, fieldPath, valueText, false);
    }

    private EditResult doManualEdit(CapturedPacket packet, String fieldPath, String valueText,
                                    boolean commit) {
        Object original = packet.nativePacket();
        ProtectionDecision decision = protection.evaluate(packet.meta(), OperationType.EDIT);
        if (decision.blocked()) {
            return EditResult.blocked(original, decision.reason());
        }
        PacketEditAdapter adapter = decoder.adapterFor(original);
        if (adapter == null) {
            return EditResult.blocked(original,
                    "This packet type has no editable adapter; it is inspect-only.");
        }
        EditOutcome outcome = applyFieldTo(adapter, packet.meta(), original, fieldPath, valueText,
                null, "(manual)");
        if (!outcome.success) {
            return EditResult.blocked(original, outcome.message);
        }
        if (commit) {
            historySink.accept(outcome.record);
        }
        List<ModificationRecord> mods = new ArrayList<>();
        mods.add(outcome.record);
        return EditResult.applied(outcome.newNative, mods);
    }

    /**
     * Apply one field edit to {@code current}, returning the new native packet +
     * a modification record on success, or a failure reason. Fully guarded.
     */
    private EditOutcome applyFieldTo(PacketEditAdapter adapter, PacketMeta meta, Object current,
                                     String fieldPath, String valueText, String ruleId,
                                     String ruleName) {
        try {
            DecodedPacket decoded = adapter.decode(current);
            Optional<PacketField> field = decoded.field(fieldPath);
            if (field.isEmpty()) {
                return EditOutcome.fail("No such field: " + fieldPath);
            }
            if (!field.get().editable()) {
                String why = field.get().notEditableReason();
                return EditOutcome.fail("Field '" + fieldPath + "' is not editable"
                        + (why == null ? "." : ": " + why));
            }
            String originalValue = String.valueOf(field.get().value());

            Object parsed;
            try {
                parsed = ValueParser.parse(field.get().type(), valueText);
            } catch (ValueParser.ParseException e) {
                return EditOutcome.fail(e.getMessage());
            }

            String validation = adapter.validate(current, field.get(), parsed);
            if (validation != null) {
                return EditOutcome.fail(validation);
            }

            Object newNative = adapter.applyEdit(current, fieldPath, parsed);
            if (newNative == null) {
                return EditOutcome.fail("The edit produced no packet (adapter returned null).");
            }
            ModificationRecord record = new ModificationRecord(System.currentTimeMillis(), ruleId,
                    ruleName, meta.packetName(), meta.direction(), fieldPath, originalValue, valueText);
            return EditOutcome.ok(newNative, record);
        } catch (PacketEditAdapter.EditException e) {
            return EditOutcome.fail("Edit failed: " + e.getMessage());
        } catch (Throwable t) {
            // Catch everything (ClassCastException/NPE from a faulty adapter, etc.).
            LOG.warn("Unexpected error editing '" + fieldPath + "'", t);
            return EditOutcome.fail("Edit failed unexpectedly ("
                    + t.getClass().getSimpleName() + "); the packet was left unchanged.");
        }
    }

    /** Result of applying one field edit. */
    private static final class EditOutcome {
        final boolean success;
        final Object newNative;
        final ModificationRecord record;
        final String message;

        private EditOutcome(boolean success, Object newNative, ModificationRecord record,
                            String message) {
            this.success = success;
            this.newNative = newNative;
            this.record = record;
            this.message = message;
        }

        static EditOutcome ok(Object newNative, ModificationRecord record) {
            return new EditOutcome(true, newNative, record, "ok");
        }

        static EditOutcome fail(String message) {
            return new EditOutcome(false, null, null, message);
        }
    }
}
