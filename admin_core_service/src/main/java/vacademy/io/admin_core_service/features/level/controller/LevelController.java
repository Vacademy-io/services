package vacademy.io.admin_core_service.features.level.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vacademy.io.admin_core_service.features.level.dto.AddLevelDTO;
import vacademy.io.admin_core_service.features.level.service.LevelService;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.institute.dto.LevelDTO;

import java.util.List;

@RestController
@RequestMapping("/admin-core-service/level/v1")
@RequiredArgsConstructor
public class LevelController {
    private final LevelService levelService;

    @PostMapping("/add-level")
    public ResponseEntity<String> addLevel(@RequestBody AddLevelDTO addLevelDTO, @RequestAttribute("user") CustomUserDetails user, @RequestParam("packageId") String packageId, @RequestParam("sessionId") String sessionId) {
        return ResponseEntity.ok(levelService.addLevel(addLevelDTO, packageId, sessionId, user));
    }

    @PutMapping("/update-level/{levelId}")
    public ResponseEntity<LevelDTO> updateLevel(@RequestBody LevelDTO levelDTO, @RequestAttribute("user") CustomUserDetails user, @PathVariable("levelId") String levelId) {
        return ResponseEntity.ok(levelService.updateLevel(levelId, levelDTO, user));
    }

    @DeleteMapping("/delete-level")
    public ResponseEntity<String> deleteLevels(@RequestBody List<String> levelIds, @RequestAttribute("user") CustomUserDetails user) {
        return ResponseEntity.ok(levelService.deleteLevels(levelIds, user));
    }
}
