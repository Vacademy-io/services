package vacademy.io.admin_core_service.features.slide.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vacademy.io.common.ai.dto.RichTextDataDTO;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class QuestionSlideDTO {

    private String id;

    private RichTextDataDTO parentRichText;

    private RichTextDataDTO textData;

    private RichTextDataDTO explanationTextData;

    private String mediaId;

    private String questionResponseType;

    private String questionType;

    private String accessLevel;

    private String autoEvaluationJson;

    private String evaluationType;

    private Integer defaultQuestionTimeMins;

    private Integer reAttemptCount;

    private Integer points;

    private List<OptionDTO> options; // list of options for the slide
}
