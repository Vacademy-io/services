package vacademy.io.assessment_service.features.assessment.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AssessmentRegistrationCustomFieldRequest {

    private String id;

    private String assessmentCustomFieldId;

    private String assessmentCustomFieldKey;

    private String answer;

}