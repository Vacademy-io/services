package vacademy.io.admin_core_service.features.study.library.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import vacademy.io.common.institute.dto.SessionDTO;

import java.util.List;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SessionDTOWithDetails {
    private SessionDTO sessionDTO;
    List<LevelDTOWithDetails> levelWithDetails;
}