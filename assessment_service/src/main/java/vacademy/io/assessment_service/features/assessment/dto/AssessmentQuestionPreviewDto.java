package vacademy.io.assessment_service.features.assessment.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vacademy.io.assessment_service.features.assessment.entity.QuestionAssessmentSectionMapping;
import vacademy.io.assessment_service.features.question_core.entity.Question;
import vacademy.io.assessment_service.features.rich_text.dto.AssessmentRichTextDataDTO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AssessmentQuestionPreviewDto {
    private String questionId;
    private AssessmentRichTextDataDTO question;
    private String sectionId;
    private Integer questionDuration;
    private Integer questionOrder;
    private String markingJson;
    private String questionType;

    public AssessmentQuestionPreviewDto(Question question, QuestionAssessmentSectionMapping questionAssessmentSectionMapping) {
        this.questionId = question.getId();
        this.question = question.getTextData() == null ? null : question.getTextData().toDTO();
        this.sectionId = questionAssessmentSectionMapping.getSection().getId();
        this.questionDuration = questionAssessmentSectionMapping.getQuestionDurationInMin();
        this.questionOrder = questionAssessmentSectionMapping.getQuestionOrder();
        this.markingJson = questionAssessmentSectionMapping.getMarkingJson();
        this.questionType = question.getQuestionType();
    }
}