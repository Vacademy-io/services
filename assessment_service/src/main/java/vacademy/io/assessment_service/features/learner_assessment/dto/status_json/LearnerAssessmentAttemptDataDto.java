package vacademy.io.assessment_service.features.learner_assessment.dto.status_json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LearnerAssessmentAttemptDataDto {
    private String attemptId;
    private String clientLastSync;
    private AssessmentAttemptData assessment;
    private List<SectionAttemptData> sections;
}
