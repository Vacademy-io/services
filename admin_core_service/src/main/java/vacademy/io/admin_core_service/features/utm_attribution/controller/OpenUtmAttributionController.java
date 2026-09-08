package vacademy.io.admin_core_service.features.utm_attribution.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vacademy.io.admin_core_service.features.audience.service.PublicLeadRateLimiter;
import vacademy.io.admin_core_service.features.utm_attribution.dto.UtmTrackRequest;
import vacademy.io.admin_core_service.features.utm_attribution.service.UtmAttributionService;

/**
 * Capture endpoint for campaign attribution, called by the learner app right
 * after a successful submission (audience form, live-session registration,
 * assessment registration, enrolment invite, catalogue lead).
 *
 * Unauthenticated by necessity — the person submitting a public form has no
 * token yet. Consequences, handled here:
 *  - rate limited per IP and per institute, reusing the LEAD limiter (this
 *    fires once per successful submission, exactly the shape that limiter is
 *    tuned for) rather than the far looser page-view beacon limiter.
 *  - always answers 204. Whether a touch was recorded is not the caller's
 *    business, and a failure here must never surface as an error on a form the
 *    learner has already successfully submitted.
 *  - identity fields are accepted but only ever ATTACH a touch to a person; the
 *    service never creates or modifies a user from this payload.
 *
 * Lives under {@code /admin-core-service/open/**}, the established permitAll
 * prefix in ApplicationSecurityConfig.
 */
@RestController
@RequestMapping("/admin-core-service/open/v1/utm")
public class OpenUtmAttributionController {

    @Autowired
    private UtmAttributionService service;

    @Autowired
    private PublicLeadRateLimiter rateLimiter;

    @PostMapping("/track")
    public ResponseEntity<Void> track(@RequestBody UtmTrackRequest body, HttpServletRequest request) {
        if (body != null && rateLimiter.tryAcquire(clientIp(request), body.getInstituteId())) {
            service.record(body);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /** Real client IP behind the ingress/CDN — XFF is a chain, take the first. */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        return (real != null && !real.isBlank()) ? real.trim() : request.getRemoteAddr();
    }
}
