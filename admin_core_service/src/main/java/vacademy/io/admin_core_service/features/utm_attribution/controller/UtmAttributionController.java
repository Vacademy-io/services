package vacademy.io.admin_core_service.features.utm_attribution.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vacademy.io.admin_core_service.core.security.InstituteAccessValidator;
import vacademy.io.admin_core_service.features.utm_attribution.dto.UtmAttributionResponse;
import vacademy.io.admin_core_service.features.utm_attribution.dto.UtmCampaignSummaryResponse;
import vacademy.io.admin_core_service.features.utm_attribution.service.UtmAttributionService;
import vacademy.io.common.auth.model.CustomUserDetails;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Read side of campaign attribution — what the admin dashboard asks.
 *
 * instituteId is a request param rather than being taken from the token because
 * an admin can belong to several institutes and the open screen already knows
 * which one it is on; every endpoint checks it against the caller's own
 * authorities so it cannot be used to read another institute's data.
 */
@RestController
@RequestMapping("/admin-core-service/v1/utm")
public class UtmAttributionController {

    @Autowired
    private UtmAttributionService service;

    @Autowired
    private InstituteAccessValidator accessValidator;

    /**
     * Every recorded touch for one learner, oldest first.
     *
     * Answers with an empty list — never a 404 — when the learner arrived
     * before this feature existed or was never on a tagged link. "No campaign
     * data" is a normal state for most learners, not an error.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UtmAttributionResponse>> forUser(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String userId,
            @RequestParam String instituteId) {
        accessValidator.validateUserAccess(user, instituteId);
        return ResponseEntity.ok(service.findForUser(instituteId, userId));
    }

    /** Campaign roll-up over the last {@code days} days. */
    @GetMapping("/summary")
    public ResponseEntity<List<UtmCampaignSummaryResponse>> summary(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String instituteId,
            @RequestParam(defaultValue = "30") int days) {
        accessValidator.validateUserAccess(user, instituteId);
        int window = Math.max(1, Math.min(days, 365));
        Instant to = Instant.now();
        Instant from = to.minus(window, ChronoUnit.DAYS);
        return ResponseEntity.ok(
                service.summarise(instituteId, Timestamp.from(from), Timestamp.from(to)));
    }
}
