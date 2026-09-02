package io.github.tamawish.pureeconomy.util;

import java.math.BigDecimal;

public final class Amounts {

    private Amounts() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
