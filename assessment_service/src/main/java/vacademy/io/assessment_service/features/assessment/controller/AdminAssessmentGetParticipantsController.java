package vacademy.io.assessment_service.features.assessment.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vacademy.io.assessment_service.features.assessment.dto.AssessmentUserFilter;
import vacademy.io.assessment_service.features.assessment.dto.ClosedAssessmentParticipantsResponse;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.request.RespondentFilter;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.response.RespondentListDto;
import vacademy.io.assessment_service.features.assessment.dto.admin_get_dto.response.RespondentListResponse;
import vacademy.io.assessment_service.features.assessment.entity.AssessmentUserRegistration;
import vacademy.io.assessment_service.features.assessment.manager.AssessmentParticipantsManager;
import vacademy.io.common.auth.model.CustomUserDetails;

import java.util.List;

import static vacademy.io.common.core.constants.PageConstants.DEFAULT_PAGE_NUMBER;
import static vacademy.io.common.core.constants.PageConstants.DEFAULT_PAGE_SIZE;

@RestController
@RequestMapping("/assessment-service/assessment/admin-participants")
public class AdminAssessmentGetParticipantsController {

    @Autowired
    AssessmentParticipantsManager assessmentParticipantsManager;

    @GetMapping("/registered-participants")
    public ResponseEntity<List<AssessmentUserRegistration>> assessmentAdminParticipants(@RequestAttribute("user") CustomUserDetails user,
                                                                                        @RequestParam(name = "instituteId", required = false) String instituteId, @RequestParam(name = "assessmentId", required = false) String assessmentId) {
        return assessmentParticipantsManager.assessmentAdminParticipants(user, instituteId, assessmentId);
    }

    @PostMapping("/all/registered-participants")
    public ResponseEntity<ClosedAssessmentParticipantsResponse> closedAssessmentParticipants(@RequestAttribute("user") CustomUserDetails user,
                                                                                             @RequestParam(name = "instituteId") String instituteId,
                                                                                             @RequestParam(name = "assessmentId") String assessmentId,
                                                                                             @RequestBody AssessmentUserFilter filter,
                                                                                             @RequestParam(name = "pageNo", required = false, defaultValue = DEFAULT_PAGE_NUMBER) Integer pageNo,
                                                                                             @RequestParam(name = "pageSize", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize){
        return assessmentParticipantsManager.getAllParticipantsForAssessment(user, instituteId, assessmentId, filter, pageNo, pageSize);
    }

    @PostMapping("/all/respondent-list")
    public ResponseEntity<RespondentListResponse> respondentList(@RequestAttribute("user") CustomUserDetails user,
                                                                 @RequestParam(name = "assessmentId") String assessmentId,
                                                                 @RequestParam(name = "sectionId") String sectionId,
                                                                 @RequestParam(name = "questionId") String questionId,
                                                                 @RequestBody RespondentFilter filter,
                                                                 @RequestParam(name = "pageNo", required = false, defaultValue = DEFAULT_PAGE_NUMBER) Integer pageNo,
                                                                 @RequestParam(name = "pageSize", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize){
        return assessmentParticipantsManager.getRespondentList(user, assessmentId, sectionId, questionId, filter, pageNo, pageSize);
    }

}
