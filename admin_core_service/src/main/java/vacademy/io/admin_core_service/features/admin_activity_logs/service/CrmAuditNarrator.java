package vacademy.io.admin_core_service.features.admin_activity_logs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vacademy.io.admin_core_service.features.audience.entity.Audience;
import vacademy.io.admin_core_service.features.audience.entity.AudienceResponse;
import vacademy.io.admin_core_service.features.audience.entity.LeadFollowup;
import vacademy.io.admin_core_service.features.audience.entity.LeadStatus;
import vacademy.io.admin_core_service.features.audience.repository.AudienceRepository;
import vacademy.io.admin_core_service.features.audience.repository.AudienceResponseRepository;
import vacademy.io.admin_core_service.features.audience.repository.LeadFollowupRepository;
import vacademy.io.admin_core_service.features.audience.repository.LeadStatusRepository;
import vacademy.io.admin_core_service.features.audience.repository.UserLeadProfileRepository;
import vacademy.io.common.auth.entity.User;
import vacademy.io.common.auth.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CRM-side twin of {@link AuditNarrator}: turns the ids a CRM request carries
 * into the phrases stored in {@code admin_activity_log.description}.
 *
 * <p>Kept separate from {@link AuditNarrator} so the learner/course lookups and
 * the audience/lead lookups don't drag each other's repositories into one bean.
 * Annotations reach it as {@code @crmAuditNarrator.<method>(...)}.
 *
 * <p>Conventions carried over from {@link AuditNarrator}, and they matter:
 * <ul>
 *   <li><b>Never throw.</b> Every method degrades to an id, a count, or null —
 *       an audit lookup must not be able to fail a customer mutation.</li>
 *   <li><b>No actor in the phrase</b> — the read UI prepends {@code actor_name}.</li>
 *   <li><b>One name, many counted.</b> Listing 200 leads makes a row unreadable;
 *       the count is what an admin scanning the log needs.</li>
 *   <li><b>Distinct method names, no overloads</b> — SpEL resolves overloads
 *       poorly when an argument is null.</li>
 * </ul>
 */
@Component
public class CrmAuditNarrator {

    private static final Logger logger = LoggerFactory.getLogger(CrmAuditNarrator.class);

    @Autowired
    private AudienceRepository audienceRepository;

    @Autowired
    private AudienceResponseRepository audienceResponseRepository;

    @Autowired
    private LeadStatusRepository leadStatusRepository;

    @Autowired
    private LeadFollowupRepository leadFollowupRepository;

    @Autowired
    private UserLeadProfileRepository userLeadProfileRepository;

    @Autowired
    private UserRepository userRepository;

    // ── Audiences (campaigns) ─────────────────────────────────────────────

    /** Campaign name for an audience id, falling back to the raw id. */
    public String audienceFor(String audienceId) {
        if (isBlank(audienceId)) {
            return null;
        }
        try {
            return audienceRepository.findById(audienceId)
                    .map(Audience::getCampaignName)
                    .filter(name -> !isBlank(name))
                    .map(String::trim)
                    .orElse(audienceId);
        } catch (Exception e) {
            logger.warn("Could not resolve audience name for {}: {}", audienceId, e.getMessage());
            return audienceId;
        }
    }

    /**
     * Snapshot of an audience for {@code captureBefore}. Returned as a map
     * rather than the entity so the JSON in {@code before_payload} stays small
     * and stable, and so {@code descriptionExpr} can read the name back as
     * {@code #before['name']} <em>after</em> the row is gone (a DELETE cannot
     * look the name up again).
     */
    public Map<String, Object> audienceSnapshot(String audienceId) {
        if (isBlank(audienceId)) {
            return null;
        }
        try {
            return audienceRepository.findById(audienceId)
                    .map(audience -> {
                        Map<String, Object> snapshot = new LinkedHashMap<>();
                        snapshot.put("id", audience.getId());
                        snapshot.put("name", audience.getCampaignName());
                        snapshot.put("type", audience.getCampaignType());
                        snapshot.put("objective", audience.getCampaignObjective());
                        snapshot.put("status", audience.getStatus());
                        snapshot.put("description", audience.getDescription());
                        snapshot.put("to_notify", audience.getToNotify());
                        snapshot.put("start_date", String.valueOf(audience.getStartDate()));
                        snapshot.put("end_date", String.valueOf(audience.getEndDate()));
                        return snapshot;
                    })
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not snapshot audience {}: {}", audienceId, e.getMessage());
            return null;
        }
    }

    /** Reads a name out of an {@code audienceSnapshot}, for post-delete phrasing. */
    public String nameFromSnapshot(Object snapshot, String fallback) {
        if (snapshot instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null && !isBlank(name.toString())) {
                return name.toString().trim();
            }
        }
        return fallback;
    }

    // ── Leads ─────────────────────────────────────────────────────────────

    /**
     * Display name for one lead (an {@code audience_response} row): the lead's
     * own user record first, then the parent/guardian name captured on the
     * form, then the raw id.
     */
    public String leadFor(String audienceResponseId) {
        if (isBlank(audienceResponseId)) {
            return null;
        }
        try {
            AudienceResponse response = audienceResponseRepository.findById(audienceResponseId).orElse(null);
            if (response == null) {
                return audienceResponseId;
            }
            String name = personFor(response.getUserId());
            if (!isBlank(name) && !name.equals(response.getUserId())) {
                return name;
            }
            if (!isBlank(response.getParentName())) {
                return response.getParentName().trim();
            }
            if (!isBlank(response.getParentEmail())) {
                return response.getParentEmail().trim();
            }
            if (!isBlank(response.getParentMobile())) {
                return response.getParentMobile().trim();
            }
            return audienceResponseId;
        } catch (Exception e) {
            logger.warn("Could not resolve lead name for {}: {}", audienceResponseId, e.getMessage());
            return audienceResponseId;
        }
    }

    /** Names one lead, or counts several — "Amit Kumar" vs "5 lead(s)". */
    public String leadsFor(List<String> audienceResponseIds) {
        List<String> ids = distinctNonBlank(audienceResponseIds);
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() > 1) {
            return ids.size() + " lead(s)";
        }
        return leadFor(ids.get(0));
    }

    /** Human label of a lead status id ("Interested"), falling back to the id. */
    public String leadStatusFor(String leadStatusId) {
        if (isBlank(leadStatusId)) {
            return null;
        }
        try {
            return leadStatusRepository.findById(leadStatusId)
                    .map(LeadStatus::getLabel)
                    .filter(label -> !isBlank(label))
                    .map(String::trim)
                    .orElse(leadStatusId);
        } catch (Exception e) {
            logger.warn("Could not resolve lead status label for {}: {}", leadStatusId, e.getMessage());
            return leadStatusId;
        }
    }

    /** Snapshot of a lead status row, for UPDATE/DELETE before-payloads. */
    public Map<String, Object> leadStatusSnapshot(String leadStatusId) {
        if (isBlank(leadStatusId)) {
            return null;
        }
        try {
            return leadStatusRepository.findById(leadStatusId)
                    .map(status -> {
                        Map<String, Object> snapshot = new LinkedHashMap<>();
                        snapshot.put("id", status.getId());
                        snapshot.put("name", status.getLabel());
                        snapshot.put("status_key", status.getStatusKey());
                        snapshot.put("color", status.getColor());
                        snapshot.put("display_order", status.getDisplayOrder());
                        snapshot.put("is_default", status.getIsDefault());
                        snapshot.put("is_active", status.getIsActive());
                        return snapshot;
                    })
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not snapshot lead status {}: {}", leadStatusId, e.getMessage());
            return null;
        }
    }

    // ── Follow-ups ────────────────────────────────────────────────────────

    /** "Amit Kumar" — the lead a follow-up belongs to, for update/close rows. */
    public String followupLeadFor(String followupId) {
        if (isBlank(followupId)) {
            return null;
        }
        try {
            return leadFollowupRepository.findById(followupId)
                    .map(LeadFollowup::getAudienceResponseId)
                    .map(this::leadFor)
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not resolve follow-up {}: {}", followupId, e.getMessage());
            return null;
        }
    }

    // ── People (counsellors, staff) ───────────────────────────────────────

    /** Full name of any platform user (counsellor, staff), falling back to the id. */
    public String personFor(String userId) {
        if (isBlank(userId)) {
            return null;
        }
        try {
            return userRepository.findById(userId)
                    .map(User::getFullName)
                    .filter(name -> !isBlank(name))
                    .map(String::trim)
                    .orElse(userId);
        } catch (Exception e) {
            logger.warn("Could not resolve user name for {}: {}", userId, e.getMessage());
            return userId;
        }
    }

    /** Names one person, or counts several — for bulk assign/target actions. */
    public String peopleFor(List<String> userIds) {
        List<String> ids = distinctNonBlank(userIds);
        if (ids.isEmpty()) {
            return null;
        }
        if (ids.size() > 1) {
            return ids.size() + " user(s)";
        }
        return personFor(ids.get(0));
    }

    // ── Phrase helpers used directly from annotations ─────────────────────

    /**
     * The counsellor currently on a lead, read <em>before</em> a reassignment.
     * Exists so the audit can tell a real removal from a no-op: the assign
     * endpoint returns early, and 200, when asked to unassign a lead that has
     * no counsellor, and logging that would claim an action nobody performed.
     */
    public String assignedCounsellorFor(String leadUserId, String instituteId) {
        if (isBlank(leadUserId) || isBlank(instituteId)) {
            return null;
        }
        try {
            return userLeadProfileRepository.findByUserIdAndInstituteId(leadUserId, instituteId)
                    .map(profile -> profile.getAssignedCounselorId())
                    .filter(id -> !isBlank(id))
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not read the assigned counsellor for {}: {}", leadUserId, e.getMessage());
            return null;
        }
    }

    /**
     * "assigned Amit Kumar to Rahul" / "unassigned Amit Kumar from Rahul".
     * The counsellor name is optional — assignment dialogs send it, other
     * callers only send the id, which we then resolve.
     */
    public String counsellorAssignment(String leadUserId, String counsellorId, String counsellorName) {
        String who = personFor(leadUserId);
        if (isBlank(counsellorId)) {
            return who == null ? null : "unassigned counsellor from lead " + who;
        }
        String counsellor = !isBlank(counsellorName) ? counsellorName.trim() : personFor(counsellorId);
        if (who == null) {
            return counsellor == null ? null : "assigned a lead to " + counsellor;
        }
        return "assigned lead " + who + " to " + (counsellor != null ? counsellor : counsellorId);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> distinctNonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
