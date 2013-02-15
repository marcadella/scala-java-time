/*
 * Copyright (c) 2007-2013, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.threeten.bp.temporal;

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.threeten.bp.DayOfWeek;
import org.threeten.bp.chrono.Chrono;
import org.threeten.bp.chrono.ChronoLocalDate;
import org.threeten.bp.format.DateTimeBuilder;
import org.threeten.bp.jdk8.Jdk8Methods;

/**
 * Localized definitions of the day-of-week, week-of-month and week-of-year fields.
 * <p>
 * A standard week is seven days long, but cultures have different definitions for some
 * other aspects of a week. This class represents the definition of the week, for the
 * purpose of providing {@link TemporalField} instances.
 * <p>
 * WeekFields provides three fields,
 * {@link #dayOfWeek()}, {@link #weekOfMonth()}, and {@link #weekOfYear()}
 * that provide access to the values from any {@link Temporal temporal object}.
 * <p>
 * The computations for day-of-week, week-of-month, and week-of-year are based
 * on the  {@link ChronoField#YEAR proleptic-year},
 * {@link ChronoField#MONTH_OF_YEAR month-of-year},
 * {@link ChronoField#DAY_OF_MONTH day-of-month}, and
 * {@link ChronoField#DAY_OF_WEEK ISO day-of-week} which are based on the
 * {@link ChronoField#EPOCH_DAY epoch-day} and the chronology.
 * The values may not be aligned with the {@link ChronoField#YEAR_OF_ERA year-of-Era}
 * depending on the Chronology.
 * <p>A week is defined by:
 * <ul>
 * <li>The first day-of-week.
 * For example, the ISO-8601 standard considers Monday to be the first day-of-week.
 * <li>The minimal number of days in the first week.
 * For example, the ISO-08601 standard counts the first week as needing at least 4 days.
 * </ul><p>
 * Together these two values allow a year or month to be divided into weeks.
 * <p>
 * <h3>Week of Month</h3>
 * One field is used: week-of-month.
 * The calculation ensures that weeks never overlap a month boundary.
 * The month is divided into periods where each period starts on the defined first day-of-week.
 * The earliest period is referred to as week 0 if it has less than the minimal number of days
 * and week 1 if it has at least the minimal number of days.
 * <p>
 * <table cellpadding="0" cellspacing="3" border="0" style="text-align: left; width: 50%;">
 * <caption>Examples of WeekFields</caption>
 * <tr><th>Date</th><td>Day-of-week</td>
 *  <td>First day: Monday<br>Minimal days: 4</td><td>First day: Monday<br>Minimal days: 5</td></tr>
 * <tr><th>2008-12-31</th><td>Wednesday</td>
 *  <td>Week 5 of December 2008</td><td>Week 5 of December 2008</td></tr>
 * <tr><th>2009-01-01</th><td>Thursday</td>
 *  <td>Week 1 of January 2009</td><td>Week 0 of January 2009</td></tr>
 * <tr><th>2009-01-04</th><td>Sunday</td>
 *  <td>Week 1 of January 2009</td><td>Week 0 of January 2009</td></tr>
 * <tr><th>2009-01-05</th><td>Monday</td>
 *  <td>Week 2 of January 2009</td><td>Week 1 of January 2009</td></tr>
 * </table>
 * <p>
 * <h3>Week of Year</h3>
 * One field is used: week-of-year.
 * The calculation ensures that weeks never overlap a year boundary.
 * The year is divided into periods where each period starts on the defined first day-of-week.
 * The earliest period is referred to as week 0 if it has less than the minimal number of days
 * and week 1 if it has at least the minimal number of days.
 * <p>
 * This class is immutable and thread-safe.
 */
public final class WeekFields implements Serializable {
    // implementation notes
    // querying week-of-month or week-of-year should return the week value bound within the month/year
    // however, setting the week value should be lenient (use plus/minus weeks)
    // allow week-of-month outer range [0 to 5]
    // allow week-of-year outer range [0 to 53]
    // this is because callers shouldn't be expected to know the details of validity

    /**
     * The cache of rules by firstDayOfWeek plus minimalDays.
     * Initialized first to be available for definition of ISO, etc.
     */
    private static final ConcurrentMap<String, WeekFields> CACHE = new ConcurrentHashMap<>(4, 0.75f, 2);

    /**
     * The ISO-8601 definition, where a week starts on Monday and the first week
     * has a minimum of 4 days.
     * <p>
     * The ISO-8601 standard defines a calendar system based on weeks.
     * It uses the week-based-year and week-of-week-based-year concepts to split
     * up the passage of days instead of the standard year/month/day.
     * <p>
     * Note that the first week may start in the previous calendar year.
     * Note also that the first few days of a calendar year may be in the
     * week-based-year corresponding to the previous calendar year.
     */
    public static final WeekFields ISO = new WeekFields(DayOfWeek.MONDAY, 4);

    /**
     * The common definition of a week that starts on Sunday.
     * <p>
     * Defined as starting on Sunday and with a minimum of 1 day in the month.
     * This week definition is in use in the US and other European countries.
     *
     */
    public static final WeekFields SUNDAY_START = WeekFields.of(DayOfWeek.SUNDAY, 1);

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -1177360819670808121L;

    /**
     * The first day-of-week.
     */
    private final DayOfWeek firstDayOfWeek;
    /**
     * The minimal number of days in the first week.
     */
    private final int minimalDays;

    /**
     * The field used to access the computed DayOfWeek.
     */
    private transient final TemporalField dayOfWeek = ComputedDayOfField.ofDayOfWeekField(this);

    /**
     * The field used to access the computed WeekOfMonth.
     */
    private transient final TemporalField weekOfMonth = ComputedDayOfField.ofWeekOfMonthField(this);

    /**
     * The field used to access the computed WeekOfYear.
     */
    private transient final TemporalField weekOfYear = ComputedDayOfField.ofWeekOfYearField(this);

    /**
     * Obtains an instance of {@code WeekFields} appropriate for a locale.
     * <p>
     * This will look up appropriate values from the provider of localization data.
     *
     * @param locale  the locale to use, not null
     * @return the week-definition, not null
     */
    public static WeekFields of(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        locale = new Locale(locale.getLanguage(), locale.getCountry());  // elminate variants

        // obtain these from GregorianCalendar for now
        GregorianCalendar gcal = new GregorianCalendar(locale);
        int calDow = gcal.getFirstDayOfWeek();
        DayOfWeek dow = DayOfWeek.SUNDAY.plus(calDow - 1);
        int minDays = gcal.getMinimalDaysInFirstWeek();
        return WeekFields.of(dow, minDays);
    }

    /**
     * Obtains an instance of {@code WeekFields} from the first day-of-week and minimal days.
     * <p>
     * The first day-of-week defines the ISO {@code DayOfWeek} that is day 1 of the week.
     * The minimal number of days in the first week defines how many days must be present
     * in a month or year, starting from the first day-of-week, before the week is counted
     * as the first week. A value of 1 will count the first day of the month or year as part
     * of the first week, whereas a value of 7 will require the whole seven days to be in
     * the new month or year.
     * <p>
     * WeekFields instances are singletons; for each unique combination
     * of {@code firstDayOfWeek} and {@code minimalDaysInFirstWeek} the
     * the same instance will be returned.
     *
     * @param firstDayOfWeek  the first day of the week, not null
     * @param minimalDaysInFirstWeek  the minimal number of days in the first week, from 1 to 7
     * @return the week-definition, not null
     * @throws IllegalArgumentException if the minimal days value is less than one
     *      or greater than 7
     */
    public static WeekFields of(DayOfWeek firstDayOfWeek, int minimalDaysInFirstWeek) {
        String key = firstDayOfWeek.toString() + minimalDaysInFirstWeek;
        WeekFields rules = CACHE.get(key);
        if (rules == null) {
            rules = new WeekFields(firstDayOfWeek, minimalDaysInFirstWeek);
            CACHE.putIfAbsent(key, rules);
            rules = CACHE.get(key);
        }
        return rules;
    }

    //-----------------------------------------------------------------------
    /**
     * Creates an instance of the definition.
     *
     * @param firstDayOfWeek  the first day of the week, not null
     * @param minimalDaysInFirstWeek  the minimal number of days in the first week, from 1 to 7
     * @throws IllegalArgumentException if the minimal days value is invalid
     */
    private WeekFields(DayOfWeek firstDayOfWeek, int minimalDaysInFirstWeek) {
        Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek");
        if (minimalDaysInFirstWeek < 1 || minimalDaysInFirstWeek > 7) {
            throw new IllegalArgumentException("Minimal number of days is invalid");
        }
        this.firstDayOfWeek = firstDayOfWeek;
        this.minimalDays = minimalDaysInFirstWeek;
    }

    /**
     * Ensure valid singleton.
     * 
     * @return the valid week fields instance, not null
     * @throws InvalidObjectException if invalid
     */
    private Object readResolve() throws InvalidObjectException {
        try {
            return WeekFields.of(firstDayOfWeek, minimalDays);
        } catch (IllegalArgumentException ex) {
            throw new InvalidObjectException("Invalid WeekFields" + ex.getMessage());
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the first day-of-week.
     * <p>
     * The first day-of-week varies by culture.
     * For example, the US uses Sunday, while France and the ISO-8601 standard use Monday.
     * This method returns the first day using the standard {@code DayOfWeek} enum.
     *
     * @return the first day-of-week, not null
     */
    public DayOfWeek getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    /**
     * Gets the minimal number of days in the first week.
     * <p>
     * The number of days considered to define the first week of a month or year
     * varies by culture.
     * For example, the ISO-8601 requires 4 days (more than half a week) to
     * be present before counting the first week.
     *
     * @return the minimal number of days in the first week of a month or year, from 1 to 7
     */
    public int getMinimalDaysInFirstWeek() {
        return minimalDays;
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a field to access the day of week,
     * computed based on this WeekFields.
     * <p>
     * The days of week are numbered from 1 to 7.
     * Day number 1 is the {@link #getFirstDayOfWeek() first day-of-week}.
     *
     * @return the field for day-of-week using this week definition, not null
     */
    public TemporalField dayOfWeek() {
        return dayOfWeek;
    }

    /**
     * Returns a field to access the week of month,
     * computed based on this WeekFields.
     * <p>
     * This represents concept of the count of weeks within the month where weeks
     * start on a fixed day-of-week, such as Monday.
     * This field is typically used with {@link WeekFields#dayOfWeek()}.
     * <p>
     * Week one (1) is the week starting on the {@link WeekFields#getFirstDayOfWeek}
     * where there are at least {@link WeekFields#getMinimalDaysInFirstWeek()} days in the month.
     * Thus, week one may start up to {@code minDays} days before the start of the month.
     * If the first week starts after the start of the month then the period before is week zero (0).
     * <p>
     * For example:<br>
     * - if the 1st day of the month is a Monday, week one starts on the 1st and there is no week zero<br>
     * - if the 2nd day of the month is a Monday, week one starts on the 2nd and the 1st is in week zero<br>
     * - if the 4th day of the month is a Monday, week one starts on the 4th and the 1st to 3rd is in week zero<br>
     * - if the 5th day of the month is a Monday, week two starts on the 5th and the 1st to 4th is in week one<br>
     * <p>
     * This field can be used with any calendar system.
     * @return a TemporalField to access the WeekOfMonth, not null
     */
    public TemporalField weekOfMonth() {
        return weekOfMonth;
    }

    /**
     * Returns a field to access the week of year,
     * computed based on this WeekFields.
     * <p>
     * This represents concept of the count of weeks within the year where weeks
     * start on a fixed day-of-week, such as Monday.
     * This field is typically used with {@link WeekFields#dayOfWeek()}.
     * <p>
     * Week one(1) is the week starting on the {@link WeekFields#getFirstDayOfWeek}
     * where there are at least {@link WeekFields#getMinimalDaysInFirstWeek()} days in the month.
     * Thus, week one may start up to {@code minDays} days before the start of the year.
     * If the first week starts after the start of the year then the period before is week zero (0).
     * <p>
     * For example:<br>
     * - if the 1st day of the year is a Monday, week one starts on the 1st and there is no week zero<br>
     * - if the 2nd day of the year is a Monday, week one starts on the 2nd and the 1st is in week zero<br>
     * - if the 4th day of the year is a Monday, week one starts on the 4th and the 1st to 3rd is in week zero<br>
     * - if the 5th day of the year is a Monday, week two starts on the 5th and the 1st to 4th is in week one<br>
     * <p>
     * This field can be used with any calendar system.
     * @return a TemporalField to access the WeekOfYear, not null
     */
    public TemporalField weekOfYear() {
        return weekOfYear;
    }

    /**
     * Checks if these rules are equal to the specified rules.
     * <p>
     * The comparison is based on the entire state of the rules, which is
     * the first day-of-week and minimal days.
     *
     * @param object  the other rules to compare to, null returns false
     * @return true if this is equal to the specified rules
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof WeekFields) {
            return hashCode() == object.hashCode();
        }
        return false;
    }

    /**
     * A hash code for these rules.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return firstDayOfWeek.ordinal() * 7 + minimalDays;
    }

    //-----------------------------------------------------------------------
    /**
     * A string representation of this definition.
     *
     * @return the string representation, not null
     */
    @Override
    public String toString() {
        return "WeekFields[" + firstDayOfWeek + ',' + minimalDays + ']';
    }

    //-----------------------------------------------------------------------
    /**
     * Field type that computes DayOfWeek, WeekOfMonth, and WeekOfYear
     * based on a WeekFields.
     * A separate Field instance is required for each different WeekFields;
     * combination of start of week and minimum number of days.
     * Constructors are provided to create fields for DayOfWeek, WeekOfMonth,
     * and WeekOfYear.
     */
    static class ComputedDayOfField implements TemporalField {

        /**
         * Returns a field to access the day of week,
         * computed based on a WeekFields.
         * <p>
         * The WeekDefintion of the first day of the week is used with
         * the ISO DAY_OF_WEEK field to compute week boundaries.
         */
        static ComputedDayOfField ofDayOfWeekField(WeekFields weekDef) {
            return new ComputedDayOfField("DayOfWeek", weekDef,
                    ChronoUnit.DAYS, ChronoUnit.WEEKS, DAY_OF_WEEK_RANGE);
        }

        /**
         * Returns a field to access the week of month,
         * computed based on a WeekFields.
         * @see WeekFields#weekOfMonth()
         */
        static ComputedDayOfField ofWeekOfMonthField(WeekFields weekDef) {
            return new ComputedDayOfField("WeekOfMonth", weekDef,
                    ChronoUnit.WEEKS, ChronoUnit.MONTHS, WEEK_OF_MONTH_RANGE);
        }

        /**
         * Returns a field to access the week of year,
         * computed based on a WeekFields.
         * @see WeekFields#weekOfYear()
         */
        static ComputedDayOfField ofWeekOfYearField(WeekFields weekDef) {
            return new ComputedDayOfField("WeekOfYear", weekDef,
                    ChronoUnit.WEEKS, ChronoUnit.YEARS, WEEK_OF_YEAR_RANGE);
        }
        private final String name;
        private final WeekFields weekDef;
        private final TemporalUnit baseUnit;
        private final TemporalUnit rangeUnit;
        private final ValueRange range;

        private ComputedDayOfField(String name, WeekFields weekDef, TemporalUnit baseUnit, TemporalUnit rangeUnit, ValueRange range) {
            this.name = name;
            this.weekDef = weekDef;
            this.baseUnit = baseUnit;
            this.rangeUnit = rangeUnit;
            this.range = range;
        }

        private static final ValueRange DAY_OF_WEEK_RANGE = ValueRange.of(1, 7);
        private static final ValueRange WEEK_OF_MONTH_RANGE = ValueRange.of(0, 1, 4, 5);
        private static final ValueRange WEEK_OF_YEAR_RANGE = ValueRange.of(0, 1, 52, 53);

        @Override
        public long doGet(TemporalAccessor temporal) {
            // Offset the ISO DOW by the start of this week
            int sow = weekDef.getFirstDayOfWeek().getValue();
            int isoDow = temporal.get(ChronoField.DAY_OF_WEEK);
            int dow = Jdk8Methods.floorMod(isoDow - sow, 7) + 1;

            if (rangeUnit == ChronoUnit.WEEKS) {
                return dow;
            } else if (rangeUnit == ChronoUnit.MONTHS) {
                int dom = temporal.get(ChronoField.DAY_OF_MONTH);
                int offset = startOfWeekOffset(dom, dow);
                return computeWeek(offset, dom);
            } else if (rangeUnit == ChronoUnit.YEARS) {
                int doy = temporal.get(ChronoField.DAY_OF_YEAR);
                int offset = startOfWeekOffset(doy, dow);
                return computeWeek(offset, doy);
            } else {
                throw new IllegalStateException("unreachable");
            }
        }

        /**
         * Returns an offset to align week start with a day of month or day of year.
         *
         * @param day the day; 1 through infinity
         * @param dow the day of the week of that day; 1 through 7
         * @return an offset in days to align a day with the start of the first 'full' week
         */
        private int startOfWeekOffset(int day, int dow) {
            // offset of first day corresponding to the day of week in first 7 days (zero origin)
            int weekStart = Jdk8Methods.floorMod(day - dow, 7);
            int offset = -weekStart;
            if (weekStart + 1 > weekDef.getMinimalDaysInFirstWeek()) {
                // The previous week has the minimum days in the current month to be a 'week'
                offset = 7 - weekStart;
            }
            return offset;
        }

        /**
         * Returns the week number computed from the reference day and reference dayOfWeek.
         *
         * @param offset the offset to align a date with the start of week
         *     from {@link #startOfWeekOffset}.
         * @param day  the day for which to compute the week number
         * @return the week number where zero is used for a partial week and 1 for the first full week
         */
        private int computeWeek(int offset, int day) {
            return ((7 + offset + (day - 1)) / 7);
        }

        @Override
        public <R extends Temporal> R doWith(R temporal, long newValue) {
            // Check the new value and get the old value of the field
            int newVal = range.checkValidIntValue(newValue, this);
            int currentVal = temporal.get(this);
            if (newVal == currentVal) {
                return temporal;
            }
            // Compute the difference and add that using the base using of the field
            int delta = newVal - currentVal;
            return (R) temporal.plus(delta, baseUnit);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean resolve(DateTimeBuilder builder, long value) {
            int newValue = range.checkValidIntValue(value, this);
            // DOW and YEAR are necessary for all fields; Chrono defaults to ISO if not present
            int sow = weekDef.getFirstDayOfWeek().getValue();
            int dow = builder.get(weekDef.dayOfWeek());
            int year = builder.get(ChronoField.YEAR);
            Chrono chrono = Chrono.from(builder);

            // The WOM and WOY fields are the critical values
            if (rangeUnit == ChronoUnit.MONTHS) {
                // Process WOM value by combining with DOW and MONTH, YEAR
                int month = builder.get(ChronoField.MONTH_OF_YEAR);
                ChronoLocalDate cd = chrono.date(year, month, 1);
                int offset = startOfWeekOffset(1, cd.get(weekDef.dayOfWeek()));
                offset += dow - 1;    // offset to desired day of week
                offset += 7 * (newValue - 1);    // offset by week number
                ChronoLocalDate result = cd.plus(offset, ChronoUnit.DAYS);
                builder.addFieldValue(ChronoField.DAY_OF_MONTH, result.get(ChronoField.DAY_OF_MONTH));
                builder.removeFieldValue(this);
                builder.removeFieldValue(weekDef.dayOfWeek());
                return true;
            } else if (rangeUnit == ChronoUnit.YEARS) {
                // Process WOY
                ChronoLocalDate cd = chrono.date(year, 1, 1);
                int offset = startOfWeekOffset(1, cd.get(weekDef.dayOfWeek()));
                offset += dow - 1;    // offset to desired day of week
                offset += 7 * (newValue - 1);    // offset by week number
                ChronoLocalDate result = cd.plus(offset, ChronoUnit.DAYS);
                builder.addFieldValue(ChronoField.DAY_OF_MONTH, result.get(ChronoField.DAY_OF_MONTH));
                builder.addFieldValue(ChronoField.MONTH_OF_YEAR, result.get(ChronoField.MONTH_OF_YEAR));
                builder.removeFieldValue(this);
                builder.removeFieldValue(weekDef.dayOfWeek());
                return true;
            } else {
                // ignore DOW of WEEK field; the value will be processed by WOM or WOY
                int isoDow = Jdk8Methods.floorMod((sow - 1) + (dow - 1), 7) + 1;
                builder.addFieldValue(ChronoField.DAY_OF_WEEK, isoDow);
                // Not removed, the week-of-xxx fields need this value
                return true;
            }
        }

        //-----------------------------------------------------------------------
        @Override
        public String getName() {
            return name;
        }

        @Override
        public TemporalUnit getBaseUnit() {
            return baseUnit;
        }

        @Override
        public TemporalUnit getRangeUnit() {
            return rangeUnit;
        }

        @Override
        public ValueRange range() {
            return range;
        }

        //-------------------------------------------------------------------------
        @Override
        public int compare(TemporalAccessor temporal1, TemporalAccessor temporal2) {
            return Long.compare(temporal1.getLong(this), temporal2.getLong(this));
        }

        //-----------------------------------------------------------------------
        @Override
        public boolean doIsSupported(TemporalAccessor temporal) {
            if (temporal.isSupported(ChronoField.DAY_OF_WEEK)) {
                if (rangeUnit == ChronoUnit.WEEKS) {
                    return true;
                } else if (rangeUnit == ChronoUnit.MONTHS) {
                    return temporal.isSupported(ChronoField.DAY_OF_MONTH);
                } else if (rangeUnit == ChronoUnit.YEARS) {
                    return temporal.isSupported(ChronoField.DAY_OF_YEAR);
                }
            }
            return false;
        }

        @Override
        public ValueRange doRange(TemporalAccessor temporal) {
            if (rangeUnit == ChronoUnit.WEEKS) {
                return range;
            }

            TemporalField field = null;
            if (rangeUnit == ChronoUnit.MONTHS) {
                field = ChronoField.DAY_OF_MONTH;
            } else if (rangeUnit == ChronoUnit.YEARS) {
                field = ChronoField.DAY_OF_YEAR;
            } else {
                throw new IllegalStateException("unreachable");
            }

            // Offset the ISO DOW by the start of this week
            int sow = weekDef.getFirstDayOfWeek().getValue();
            int isoDow = temporal.get(ChronoField.DAY_OF_WEEK);
            int dow = Jdk8Methods.floorMod(isoDow - sow, 7) + 1;

            int offset = startOfWeekOffset(temporal.get(field), dow);
            ValueRange fieldRange = temporal.range(field);
            return ValueRange.of(computeWeek(offset, (int) fieldRange.getMinimum()),
                    computeWeek(offset, (int) fieldRange.getMaximum()));
        }

        //-----------------------------------------------------------------------
        @Override
        public String toString() {
            return getName() + "[" + weekDef.toString() + "]";
        }
    }
}