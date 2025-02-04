package vacademy.io.admin_core_service.features.slide.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SlideCountProjection {

    private Long videoCount;
    private Long pdfCount;
    private Long docCount;
    private Long unknownCount;

    // Constructor to match the aggregation fields
    public SlideCountProjection(Long videoCount, Long pdfCount, Long docCount, Long unknownCount) {
        this.videoCount = videoCount;
        this.pdfCount = pdfCount;
        this.docCount = docCount;
        this.unknownCount = unknownCount;
    }

    // Getters and setters (Lombok generates these automatically with @Data)
}
