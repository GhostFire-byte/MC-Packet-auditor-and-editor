package com.packetauditor.match;

/**
 * Comparison operators usable in a {@link FieldCondition}.
 */
public enum ConditionOperator {
    EQUALS("=="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_OR_EQUAL("<="),
    CONTAINS("contains"),
    STARTS_WITH("startsWith"),
    ENDS_WITH("endsWith"),
    MATCHES_REGEX("matches"),
    EXISTS("exists");

    private final String symbol;

    ConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean isNumericComparison() {
        switch (this) {
            case GREATER_THAN: case GREATER_OR_EQUAL:
            case LESS_THAN: case LESS_OR_EQUAL:
                return true;
            default:
                return false;
        }
    }
}
