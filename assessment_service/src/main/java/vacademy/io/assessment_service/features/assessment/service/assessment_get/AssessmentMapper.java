package vacademy.io.assessment_service.features.assessment.service.assessment_get;

import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.AdminBasicAssessmentListItemDto;

import java.util.Arrays;
import java.util.Date;

public class AssessmentMapper {

    public static AdminBasicAssessmentListItemDto toDto(Object[] assessment) {
        AdminBasicAssessmentListItemDto dto = AdminBasicAssessmentListItemDto.builder()
                .assessmentId((String) assessment[0])
                .name((String) assessment[1])
                .playMode((String) assessment[2])
                .evaluationType((String) assessment[3])
                .submissionType((String) assessment[4])
                .duration((Integer) assessment[5])
                .assessmentVisibility((String) assessment[6])
                .status((String) assessment[7])
                .registrationCloseDate((Date) assessment[8])
                .registrationOpenDate((Date) assessment[9])
                .expectedParticipants((Integer) assessment[10])
                .coverFileId((Integer) assessment[11])
                .boundStartTime((Date) assessment[12])
                .boundEndTime((Date) assessment[13])
                .createdAt((Date) assessment[14])
                .updatedAt((Date) assessment[15])
                .userRegistrations((Long) assessment[16])
                .batchIds(Arrays.asList((String[]) assessment[17]))
                .build();

        return dto;
    }
}
