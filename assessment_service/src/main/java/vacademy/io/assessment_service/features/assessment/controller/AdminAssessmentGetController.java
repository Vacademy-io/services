package vacademy.io.assessment_service.features.assessment.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.AdminAssessmentFilter;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.AllAdminAssessmentResponse;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.AssessmentAdminListInitDto;
import vacademy.io.assessment_service.features.assessment.manager.AdminAssessmentGetManager;
import vacademy.io.common.auth.model.CustomUserDetails;

import static vacademy.io.common.core.constants.PageConstants.DEFAULT_PAGE_NUMBER;
import static vacademy.io.common.core.constants.PageConstants.DEFAULT_PAGE_SIZE;

@RestController
@RequestMapping("/assessment-service/assessment/add-participants/create/v1")
public class AdminAssessmentGetController {

    @Autowired
    AdminAssessmentGetManager adminAssessmentGetManager;

    @GetMapping("/assessment-admin-list-init")
    public ResponseEntity<AssessmentAdminListInitDto> assessmentAdminListInit(@RequestAttribute("user") CustomUserDetails user,
                                                                              @RequestParam(name = "instituteId", required = false) String instituteId) {
        return adminAssessmentGetManager.assessmentAdminListInit(user, instituteId);
    }

    @PostMapping("/assessment-admin-list-filter")
    public ResponseEntity<AllAdminAssessmentResponse> assessmentAdminListFilter(@RequestAttribute("user") CustomUserDetails user,
                                                                                @RequestBody AdminAssessmentFilter adminAssessmentFilter,
                                                                                @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER, required = false) int pageNo,
                                                                                @RequestParam(value = "pageSize", defaultValue = DEFAULT_PAGE_SIZE, required = false) int pageSize,
                                                                                @RequestParam(name = "instituteId", required = false) String instituteId) {
        return adminAssessmentGetManager.assessmentAdminListFilter(user, adminAssessmentFilter, instituteId, pageNo, pageSize);
    }
}
