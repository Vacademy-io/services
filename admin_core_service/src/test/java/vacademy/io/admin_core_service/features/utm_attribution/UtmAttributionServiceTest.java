package vacademy.io.admin_core_service.features.utm_attribution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import vacademy.io.admin_core_service.features.utm_attribution.dto.UtmTrackRequest;
import vacademy.io.admin_core_service.features.utm_attribution.entity.UtmAttribution;
import vacademy.io.admin_core_service.features.utm_attribution.repository.UtmAttributionRepository;
import vacademy.io.admin_core_service.features.utm_attribution.service.UtmAttributionService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * What does and does not become an attribution row.
 *
 * <p>The capture endpoint is unauthenticated and called from a public form, so
 * "record whatever arrives" is not an option — and the failure mode of getting
 * this wrong is not a crash but a silently useless report: a table full of rows
 * with no campaign on them, or one submission counted four times.
 */
class UtmAttributionServiceTest {

    private UtmAttributionRepository repository;
    private UtmAttributionService service;

    @BeforeEach
    void setUp() {
        repository = mock(UtmAttributionRepository.class);
        service = new UtmAttributionService();
        ReflectionTestUtils.setField(service, "repository", repository);
        when(repository.save(any(UtmAttribution.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findUnlinkedByContact(anyString(), any(), any())).thenReturn(List.of());
    }

    private UtmTrackRequest.UtmTrackRequestBuilder valid() {
        return UtmTrackRequest.builder()
                .instituteId("inst-1")
                .userId("user-1")
                .sourceType("AUDIENCE")
                .sourceId("aud-9")
                .utmSource("whatsapp")
                .utmMedium("social")
                .utmCampaign("diwali-2026");
    }

    @Test
    void recordsATaggedSubmission() {
        UtmAttribution saved = service.record(valid().build());

        assertNotNull(saved);
        ArgumentCaptor<UtmAttribution> captor = ArgumentCaptor.forClass(UtmAttribution.class);
        verify(repository).save(captor.capture());
        assertEquals("whatsapp", captor.getValue().getUtmSource());
        assertEquals("AUDIENCE", captor.getValue().getSourceType());
        assertEquals("aud-9", captor.getValue().getSourceId());
    }

    /**
     * An untagged arrival is the ABSENCE of attribution, not a data point.
     * Storing those would bury the handful of real campaigns under a wall of
     * blank rows and make "how many people came from a campaign" unanswerable.
     */
    @Test
    void ignoresASubmissionWithNoCampaignAtAll() {
        assertNull(service.record(valid()
                .utmSource(null).utmMedium(null).utmCampaign(null)
                .build()));
        verify(repository, never()).save(any());
    }

    /** Blank strings are the same thing as absent — a form posting "" must not count. */
    @Test
    void treatsBlankValuesAsAbsent() {
        assertNull(service.record(valid()
                .utmSource("  ").utmMedium("").utmCampaign("   ")
                .build()));
        verify(repository, never()).save(any());
    }

    /** Anonymous campaign traffic belongs in catalogue_page_event, not here. */
    @Test
    void ignoresATouchWithNobodyToAttachItTo() {
        assertNull(service.record(valid().userId(null).email(null).mobileNumber(null).build()));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsASourceTypeOutsideTheAllowList() {
        assertNull(service.record(valid().sourceType("ARBITRARY").build()));
        assertNull(service.record(valid().sourceType(null).build()));
        verify(repository, never()).save(any());
    }

    @Test
    void acceptsSourceTypeInAnyCasing() {
        assertNotNull(service.record(valid().sourceType("live_session").build()));
        ArgumentCaptor<UtmAttribution> captor = ArgumentCaptor.forClass(UtmAttribution.class);
        verify(repository).save(captor.capture());
        assertEquals("LIVE_SESSION", captor.getValue().getSourceType());
    }

    /** A double-clicked submit is one touch, not two. */
    @Test
    void dropsADuplicateWithinTheDedupeWindow() {
        when(repository.countRecentDuplicates(anyString(), any(), any(), anyString(), any(),
                any(), any(), any(Timestamp.class))).thenReturn(1L);

        assertNull(service.record(valid().build()));
        verify(repository, never()).save(any());
    }

    /**
     * Only the HOST of the referrer is kept: a referring path routinely carries
     * search terms and occasionally personal data in its query string.
     */
    @Test
    void keepsOnlyTheReferrerHostAndTheLandingPath() {
        service.record(valid()
                .referrer("https://www.google.com/search?q=someone%27s+private+query")
                .landingUrl("https://learn.example.com/audience-response?instituteId=inst-1")
                .build());

        ArgumentCaptor<UtmAttribution> captor = ArgumentCaptor.forClass(UtmAttribution.class);
        verify(repository).save(captor.capture());
        assertEquals("www.google.com", captor.getValue().getReferrerHost());
        assertEquals("/audience-response", captor.getValue().getLandingPath());
    }

    @Test
    void lowercasesTheEmailSoLateMatchingCannotMiss() {
        service.record(valid().userId(null).email("Learner@Example.COM").build());

        ArgumentCaptor<UtmAttribution> captor = ArgumentCaptor.forClass(UtmAttribution.class);
        verify(repository).save(captor.capture());
        assertEquals("learner@example.com", captor.getValue().getEmail());
    }

    /** Rows written before the user id was known get claimed once it is. */
    @Test
    void attachesEarlierAnonymousRowsToTheResolvedUser() {
        UtmAttribution pending = UtmAttribution.builder().id("row-1").build();
        when(repository.findUnlinkedByContact("inst-1", "learner@example.com", null))
                .thenReturn(List.of(pending));

        service.record(valid().email("learner@example.com").build());

        assertEquals("user-1", pending.getUserId());
        verify(repository).saveAll(List.of(pending));
    }

    /** The flat-map overload is what the product-page flow already holds. */
    @Test
    void recordsFromTheFlatUtmParamsMap() {
        UtmAttribution saved = service.record("inst-1", "user-1", "a@b.com", "+911234567890",
                "PRODUCT_PAGE", "page-code",
                Map.of("utm_source", "instagram", "utm_medium", "social"));

        assertNotNull(saved);
        assertEquals("instagram", saved.getUtmSource());
        assertEquals("PRODUCT_PAGE", saved.getSourceType());
    }

    @Test
    void ignoresAnEmptyUtmParamsMapWithoutTouchingTheDatabase() {
        assertNull(service.record("inst-1", "user-1", null, null, "PRODUCT_PAGE", "p", Map.of()));
        assertNull(service.record("inst-1", "user-1", null, null, "PRODUCT_PAGE", "p", null));
        verify(repository, never()).save(any());
    }

    /**
     * Every caller is on a path where the learner's real work already
     * succeeded. A telemetry failure must never propagate into that.
     */
    @Test
    void swallowsARepositoryFailureRatherThanBreakingTheSubmission() {
        when(repository.save(any(UtmAttribution.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> assertNull(service.record(valid().build())));
    }

    @Test
    void readsBackNothingWhenAskedWithoutIds() {
        assertTrue(service.findForUser(null, "user-1").isEmpty());
        assertTrue(service.findForUser("inst-1", "  ").isEmpty());
        verify(repository, never()).findByInstituteIdAndUserIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void readsEveryTouchForALearnerOldestFirst() {
        UtmAttribution row = UtmAttribution.builder()
                .id("row-1").sourceType("AUDIENCE").utmSource("meta").build();
        when(repository.findByInstituteIdAndUserIdOrderByCreatedAtAsc(eq("inst-1"), eq("user-1")))
                .thenReturn(List.of(row));

        var result = service.findForUser("inst-1", "user-1");

        assertEquals(1, result.size());
        assertEquals("meta", result.get(0).getUtmSource());
        assertEquals("AUDIENCE", result.get(0).getSourceType());
    }
}
