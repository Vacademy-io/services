package vacademy.io.assessment_service.features.learner_assessment.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.SectionDto;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LearnerAssessmentStartPreviewResponse {
    private Integer previewTotalTime;
    private List<SectionDto> sectionDtos = new ArrayList<>();
    private String attemptId;
    private String assessmentUserRegistrationId;
}