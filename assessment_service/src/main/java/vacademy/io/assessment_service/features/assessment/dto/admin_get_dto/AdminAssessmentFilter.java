package vacademy.io.assessment_service.features.assessment.dto.admin_get_dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdminAssessmentFilter {
    private String name;
    private List<String> batchIds = new ArrayList<>();
    private List<String> subjectsIds = new ArrayList<>();
    private List<String> tagIds = new ArrayList<>();
    private Boolean getLiveAssessments;
    private Boolean getPassedAssessments;
    private Boolean getUpcomingAssessments;
    private List<String> assessmentStatuses = new ArrayList<>();
    private List<String> assessmentModes = new ArrayList<>();
    private Map<String, String> sortColumns = new HashMap<>();
}