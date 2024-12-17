package vacademy.io.assessment_service.features.question_bank.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class QuestionPaperFilter {
    private String name;
    private List<String> levelIds = new ArrayList<>();
    private List<String> statuses = new ArrayList<>();
    private List<String> subjectIds = new ArrayList<>();
    private Map<String, String> sortColumns;
}