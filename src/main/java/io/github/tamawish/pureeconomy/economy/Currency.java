package io.github.tamawish.pureeconomy.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Currency {

    private final String id;
    private final String singular;
    private final String plural;
    private final String symbol;
    private final int decimals;
    private final BigDecimal starting;
    private final BigDecimal max;
    private final boolean payable;
    private final String formatPattern;

    public Currency(String id, String singular, String plural, String symbol,
                    int decimals, BigDecimal starting, BigDecimal max, boolean payable) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.singular = singular;
        this.plural = plural;
        this.symbol = symbol == null ? "" : symbol;
        this.decimals = Math.max(0, decimals);
        this.starting = starting.setScale(this.decimals, RoundingMode.HALF_UP);
        this.max = max;
        this.payable = payable;

        StringBuilder pattern = new StringBuilder("#,##0");
        if (this.decimals > 0) {
            pattern.append('.');
            pattern.append("0".repeat(this.decimals));
        }
        this.formatPattern = pattern.toString();
    }

    public String id() {
        return id;
    }

    public String singular() {
        return singular;
    }

    public String plural() {
        return plural;
    }

    public String symbol() {
        return symbol;
    }

    public int decimals() {
        return decimals;
    }

    public BigDecimal starting() {
        return starting;
    }

    public BigDecimal max() {
        return max;
    }

    public boolean hasMax() {
        return max != null && max.compareTo(BigDecimal.ZERO) >= 0;
    }

    public boolean payable() {
        return payable;
    }

    public BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(decimals, RoundingMode.HALF_UP);
    }

    public boolean exceedsMax(BigDecimal amount) {
        return hasMax() && amount.compareTo(max) > 0;
    }

    public String displayName(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ONE) == 0 ? singular : plural;
    }

    public String format(BigDecimal amount) {
        BigDecimal n = normalize(amount);
        DecimalFormat format = new DecimalFormat(formatPattern, DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        if (symbol.isEmpty()) {
            return format.format(n) + " " + displayName(n);
        }
        return symbol + format.format(n);
    }

    public String name() {
        return plural;
    }
}
