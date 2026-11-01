package com.packetauditor.rules;

import com.packetauditor.match.MatchMode;
import com.packetauditor.match.MatchSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An automatic editing rule (spec §5): when a packet matches {@link #matchSpec()},
 * apply the {@link #replacements()}. Rules have a priority (higher wins), an
 * enable/disable flag, and a validity flag relative to the current match mode.
 */
public final class EditingRule {

    private final String id;
    private String name;
    private MatchSpec matchSpec;
    private final List<FieldReplacement> replacements;
    private boolean enabled;
    private int priority;
    private final long createdAtMillis;

    public EditingRule(String name, MatchSpec matchSpec) {
        this(UUID.randomUUID().toString(), name, matchSpec, new ArrayList<>(), true, 0,
                System.currentTimeMillis());
    }

    public EditingRule(String id, String name, MatchSpec matchSpec,
                       List<FieldReplacement> replacements, boolean enabled, int priority,
                       long createdAtMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? "Rule" : name;
        this.matchSpec = Objects.requireNonNull(matchSpec, "matchSpec");
        this.replacements = new ArrayList<>(replacements == null ? List.of() : replacements);
        this.enabled = enabled;
        this.priority = priority;
        this.createdAtMillis = createdAtMillis;
    }

    public String id()                        { return id; }
    public String name()                      { return name; }
    public MatchSpec matchSpec()              { return matchSpec; }
    public List<FieldReplacement> replacements() { return Collections.unmodifiableList(replacements); }
    public boolean enabled()                  { return enabled; }
    public int priority()                     { return priority; }
    public long createdAtMillis()             { return createdAtMillis; }

    public void setName(String name)          { this.name = name == null ? "Rule" : name; }
    public void setMatchSpec(MatchSpec spec)  { this.matchSpec = Objects.requireNonNull(spec); }
    public void setEnabled(boolean enabled)   { this.enabled = enabled; }
    public void setPriority(int priority)     { this.priority = priority; }

    public void addReplacement(FieldReplacement r) {
        if (r != null) {
            replacements.add(r);
        }
    }

    public void clearReplacements() {
        replacements.clear();
    }

    /** Whether the rule can run under the given match mode (spec §7). */
    public boolean isActiveUnder(MatchMode mode) {
        return enabled && matchSpec.isValidUnder(mode);
    }

    @Override
    public String toString() {
        return "EditingRule[" + name + " pri=" + priority + (enabled ? "" : " (disabled)") + "]";
    }
}
