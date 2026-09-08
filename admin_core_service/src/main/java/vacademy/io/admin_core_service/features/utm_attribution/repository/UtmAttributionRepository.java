package vacademy.io.admin_core_service.features.utm_attribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vacademy.io.admin_core_service.features.utm_attribution.entity.UtmAttribution;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface UtmAttributionRepository extends JpaRepository<UtmAttribution, String> {

    /**
     * Every touch for one person, oldest first — first touch gets the credit and
     * the learner side-view reads the tail for "most recent".
     */
    List<UtmAttribution> findByInstituteIdAndUserIdOrderByCreatedAtAsc(String instituteId, String userId);

    /**
     * Rows written before the user id was known, matched back on whatever
     * contact detail the form did capture. Either argument may be null — the
     * comparison is written so a null simply matches nothing rather than
     * matching every row with a null column.
     */
    @Query("""
            SELECT u FROM UtmAttribution u
            WHERE u.instituteId = :instituteId
              AND u.userId IS NULL
              AND ((:email IS NOT NULL AND LOWER(u.email) = LOWER(:email))
                   OR (:mobileNumber IS NOT NULL AND u.mobileNumber = :mobileNumber))
            ORDER BY u.createdAt ASC
            """)
    List<UtmAttribution> findUnlinkedByContact(@Param("instituteId") String instituteId,
                                               @Param("email") String email,
                                               @Param("mobileNumber") String mobileNumber);

    /**
     * Guard against the same submission being recorded twice — a double-clicked
     * form, or a retry after a network blip. Deliberately keyed on the campaign
     * tuple rather than on time alone, so a genuine second touch from a
     * DIFFERENT campaign is still recorded.
     */
    @Query("""
            SELECT COUNT(u) FROM UtmAttribution u
            WHERE u.instituteId = :instituteId
              AND u.sourceType = :sourceType
              AND u.createdAt > :since
              AND ((:userId IS NOT NULL AND u.userId = :userId)
                   OR (:userId IS NULL AND :email IS NOT NULL AND LOWER(u.email) = LOWER(:email)))
              AND (:sourceId IS NULL OR u.sourceId = :sourceId)
              AND (:utmCampaign IS NULL OR u.utmCampaign = :utmCampaign)
              AND (:utmSource IS NULL OR u.utmSource = :utmSource)
            """)
    long countRecentDuplicates(@Param("instituteId") String instituteId,
                               @Param("userId") String userId,
                               @Param("email") String email,
                               @Param("sourceType") String sourceType,
                               @Param("sourceId") String sourceId,
                               @Param("utmCampaign") String utmCampaign,
                               @Param("utmSource") String utmSource,
                               @Param("since") Timestamp since);

    /** Campaign roll-up for the reporting endpoint. */
    @Query("""
            SELECT u.utmSource, u.utmMedium, u.utmCampaign, u.sourceType, COUNT(u)
            FROM UtmAttribution u
            WHERE u.instituteId = :instituteId
              AND u.createdAt >= :from
              AND u.createdAt < :to
            GROUP BY u.utmSource, u.utmMedium, u.utmCampaign, u.sourceType
            ORDER BY COUNT(u) DESC
            """)
    List<Object[]> summarise(@Param("instituteId") String instituteId,
                             @Param("from") Timestamp from,
                             @Param("to") Timestamp to);
}
