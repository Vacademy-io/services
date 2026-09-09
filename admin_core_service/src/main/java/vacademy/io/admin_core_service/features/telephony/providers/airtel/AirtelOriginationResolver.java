package vacademy.io.admin_core_service.features.telephony.providers.airtel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vacademy.io.admin_core_service.features.telephony.enums.ProviderType;
import vacademy.io.admin_core_service.features.telephony.persistence.entity.TelephonyCounsellorEndpoint;
import vacademy.io.admin_core_service.features.telephony.persistence.repository.TelephonyCounsellorEndpointRepository;
import vacademy.io.admin_core_service.features.telephony.spi.OutboundOriginationResolver;
import vacademy.io.admin_core_service.features.telephony.spi.dto.OriginationContext;
import vacademy.io.admin_core_service.features.telephony.spi.dto.OriginationPlan;
import vacademy.io.common.exceptions.VacademyException;

import java.util.Optional;

/**
 * Airtel origination: no number pool. The caller's own VBC extension is the
 * first leg ({@code from}); the person called sees that caller's DID as
 * caller-ID. Both come from {@code telephony_counsellor_endpoint} (the
 * extension map), which holds a row for ANY platform user given an extension —
 * counsellors and admins alike, so an admin can dial a learner from the LMS
 * side-view the same way a counsellor dials a lead.
 */
@Component
public class AirtelOriginationResolver implements OutboundOriginationResolver {

    @Autowired private TelephonyCounsellorEndpointRepository endpointRepo;

    @Override
    public String providerType() {
        return ProviderType.AIRTEL;
    }

    /** Same wording the dial-time failure uses, so the pre-flight message and the
     *  error toast can never drift apart. */
    private static final String NO_ENDPOINT =
            "No Airtel extension is mapped to you — ask an admin to add one under Settings → Calling.";
    private static final String NO_EXTENSION = "Your Airtel extension is not set.";

    @Override
    public Optional<String> callerBlockedReason(String instituteId, String callerUserId) {
        if (callerUserId == null || callerUserId.isBlank()) return Optional.of(NO_ENDPOINT);
        return endpointRepo
                .findByCounsellorUserIdAndProviderType(callerUserId, ProviderType.AIRTEL)
                .filter(e -> Boolean.TRUE.equals(e.getEnabled()))
                .map(e -> (e.getExtension() == null || e.getExtension().isBlank())
                        ? NO_EXTENSION
                        : null)
                .map(Optional::ofNullable)
                .orElse(Optional.of(NO_ENDPOINT));
    }

    @Override
    public OriginationPlan resolve(OriginationContext ctx) {
        TelephonyCounsellorEndpoint ep = endpointRepo
                .findByCounsellorUserIdAndProviderType(ctx.getCounsellorUserId(), ProviderType.AIRTEL)
                .filter(e -> Boolean.TRUE.equals(e.getEnabled()))
                .orElseThrow(() -> new VacademyException(NO_ENDPOINT));
        if (ep.getExtension() == null || ep.getExtension().isBlank()) {
            throw new VacademyException(NO_EXTENSION);
        }
        return OriginationPlan.builder()
                .from(ep.getExtension())
                .callerId(ep.getDid())   // lead sees the counsellor's DID (may be null)
                .providerNumberId(null)  // no pool
                .build();
    }
}
