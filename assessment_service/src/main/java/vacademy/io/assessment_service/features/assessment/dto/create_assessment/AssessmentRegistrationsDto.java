package vacademy.io.assessment_service.features.assessment.dto.create_assessment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vacademy.io.assessment_service.features.assessment.dto.RegistrationFieldDto;
import vacademy.io.common.student.dto.BasicParticipantDTO;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AssessmentRegistrationsDto {
    private boolean closedTest;
    private OpenTestDetails openTestDetails;
    private List<String> addedPreRegisterBatchesDetails = new ArrayList<>();
    private List<String> deletedPreRegisterBatchesDetails = new ArrayList<>();
    private List<BasicParticipantDTO> addedPreRegisterStudentsDetails = new ArrayList<>();
    private List<BasicParticipantDTO> deletedPreRegisterStudentsDetails = new ArrayList<>();
    private String updatedJoinLink;
    private NotifyStudent notifyStudent;
    private NotifyParent notifyParent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class OpenTestDetails {
        private String registrationStartDate;
        private String registrationEndDate;
        private String instructionsHtml;
        private RegistrationFormDetails registrationFormDetails;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class RegistrationFormDetails {
            private List<RegistrationFieldDto> addedCustomAddedFields = new ArrayList<>();
            private List<RegistrationFieldDto> updatedCustomAddedFields = new ArrayList<>();
            private List<RegistrationFieldDto> removedCustomAddedFields = new ArrayList<>();
        }

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NotifyStudent {
        private Boolean whenAssessmentCreated;
        private Boolean showLeaderboard;
        private Integer beforeAssessmentGoesLive;
        private Boolean whenAssessmentLive;
        private Boolean whenAssessmentReportGenerated;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NotifyParent {
        private Boolean whenAssessmentCreated;
        private Integer beforeAssessmentGoesLive;
        private Boolean showLeaderboard;
        private Boolean whenAssessmentLive;
        private Boolean whenStudentAppears;
        private Boolean whenStudentFinishesTest;
        private Boolean whenAssessmentReportGenerated;
    }
}
