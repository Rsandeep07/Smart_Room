package com.smartroom.backend.domain;

/**
 * How an AC ON/OFF interval was determined (Section 10).
 *
 * <p>{@link #VENT_PROBE} is the primary source: the DS18B20 at the supply vent
 * compared against room temperature (Section 20.3, Option C). {@link #MANUAL} is
 * the retained dashboard override (Option A). Mains current sensing (Option B) is
 * out of scope and has no value here.
 */
public enum AcStatusSource {
    MANUAL,
    VENT_PROBE
}
