package com.smartroom.backend.web.dto;

import com.smartroom.backend.domain.AcState;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/room/{roomId}/ac} - the retained manual override of
 * Section 10 Option A.
 *
 * <p>The override is not permanent. It outranks the vent probe for
 * {@code smartroom.ac.manual-override-ttl} and then expires, after which the probe
 * resumes. That is deliberate: Section 20.3's objection to Option A is that a
 * receptionist forgets to switch it back, so the system forgets for them.
 *
 * @param status ON or OFF
 * @param note   optional free-text reason, recorded in the event log
 */
public record AcOverrideRequest(

        @NotNull(message = "status must be ON or OFF")
        AcState status,

        String note
) {
}
