package vacademy.io.assessment_service.features.assessment.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import vacademy.io.assessment_service.features.assessment.dto.*;
import vacademy.io.assessment_service.features.assessment.dto.create_assessment.AssessmentRegistrationsDto;
import vacademy.io.assessment_service.features.assessment.entity.Assessment;
import vacademy.io.assessment_service.features.assessment.entity.AssessmentBatchRegistration;
import vacademy.io.assessment_service.features.assessment.entity.AssessmentCustomField;
import vacademy.io.assessment_service.features.assessment.entity.AssessmentUserRegistration;
import vacademy.io.assessment_service.features.assessment.enums.AssessmentVisibility;
import vacademy.io.assessment_service.features.assessment.enums.UserRegistrationFilterEnum;
import vacademy.io.assessment_service.features.assessment.enums.UserRegistrationSources;
import vacademy.io.assessment_service.features.assessment.repository.AssessmentCustomFieldRepository;
import vacademy.io.assessment_service.features.assessment.repository.AssessmentRepository;
import vacademy.io.assessment_service.features.assessment.repository.AssessmentUserRegistrationRepository;
import vacademy.io.assessment_service.features.assessment.service.assessment_get.AssessmentService;
import vacademy.io.assessment_service.features.assessment.service.bulk_entry_services.AssessmentBatchRegistrationService;
import vacademy.io.assessment_service.features.rich_text.entity.AssessmentRichTextData;
import vacademy.io.assessment_service.features.rich_text.enums.TextType;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.core.utils.DateUtil;
import vacademy.io.common.exceptions.VacademyException;
import vacademy.io.common.student.dto.BasicParticipantDTO;

import java.util.*;

import static vacademy.io.common.auth.enums.CompanyStatus.ACTIVE;

@Slf4j
@Component
public class AssessmentParticipantsManager {
    @Autowired
    AssessmentService assessmentService;

    @Autowired
    AssessmentRepository assessmentRepository;

    @Autowired
    AssessmentBatchRegistrationService assessmentBatchRegistrationService;

    @Autowired
    AssessmentUserRegistrationRepository assessmentUserRegistrationRepository;

    @Autowired
    AssessmentCustomFieldRepository assessmentCustomFieldRepository;

    @Transactional
    public ResponseEntity<AssessmentSaveResponseDto> saveParticipantsToAssessment(CustomUserDetails user, AssessmentRegistrationsDto assessmentRegistrationsDto, String assessmentId, String instituteId, String type) {

        Optional<Assessment> assessmentOptional = assessmentService.getAssessmentWithActiveSections(assessmentId, instituteId);

        if (assessmentOptional.isEmpty()) {
            throw new VacademyException("Assessment not found");
        }

        preRegisterBatches(assessmentRegistrationsDto.getAddedPreRegisterBatchesDetails(), instituteId, assessmentOptional.get());
        preRegisterParticipant(user, assessmentRegistrationsDto.getAddedPreRegisterStudentsDetails(), instituteId, assessmentOptional);
        removeBatches(assessmentRegistrationsDto.getDeletedPreRegisterBatchesDetails(), instituteId, assessmentOptional.get());
        removeParticipants(assessmentRegistrationsDto.getDeletedPreRegisterStudentsDetails(), instituteId, assessmentOptional.get());
        handleOpenRegistration(assessmentRegistrationsDto.getOpenTestDetails(), assessmentOptional.get());
        handleJoinUrlChange(assessmentRegistrationsDto.getUpdatedJoinLink(), assessmentOptional.get(), instituteId);
        handleAssessmentParticipantNotification(assessmentRegistrationsDto.getNotifyStudent(), assessmentRegistrationsDto.getNotifyParent(), assessmentOptional.get(), instituteId);
        return ResponseEntity.ok(new AssessmentSaveResponseDto(assessmentOptional.get().getId(), assessmentOptional.get().getStatus()));
    }

    private void handleAssessmentParticipantNotification(AssessmentRegistrationsDto.NotifyStudent notifyStudent, AssessmentRegistrationsDto.NotifyParent notifyParent, Assessment assessment, String instituteId) {
        //TODO: handle notification
    }

    private void handleJoinUrlChange(String updatedJoinLink, Assessment assessment, String instituteId) {
        //TODO: handle join url change
    }

    private void preRegisterParticipant(CustomUserDetails user, List<BasicParticipantDTO> addedParticipants, String instituteId, Optional<Assessment> assessmentOptional) {
        List<AssessmentUserRegistration> userRegistrations = new ArrayList<>();
        for (BasicParticipantDTO participantDTO : addedParticipants) {
            userRegistrations.add(addUserToAssessment(participantDTO, user.getUserId(), instituteId, assessmentOptional.get()));
        }
        assessmentUserRegistrationRepository.saveAll(userRegistrations);
    }

    private void handleOpenRegistration(AssessmentRegistrationsDto.OpenTestDetails openTestDetails, Assessment assessment) {
        if (ObjectUtils.isEmpty(openTestDetails)) return;

        if (!ObjectUtils.isEmpty(openTestDetails.getRegistrationStartDate())) {
            assessment.setRegistrationOpenDate(DateUtil.convertStringToUTCDate(openTestDetails.getRegistrationStartDate()));
        }

        if (!ObjectUtils.isEmpty(openTestDetails.getRegistrationEndDate())) {
            assessment.setRegistrationCloseDate(DateUtil.convertStringToUTCDate(openTestDetails.getRegistrationEndDate()));
        }

        if (!ObjectUtils.isEmpty(openTestDetails.getInstructionsHtml())) {
            assessment.setInstructions(new AssessmentRichTextData(null, TextType.HTML.name(), openTestDetails.getInstructionsHtml()));
            assessmentRepository.save(assessment);
        }

        if (!ObjectUtils.isEmpty(openTestDetails.getRegistrationFormDetails())) {
            addCustomRegistrationFieldsToAsessment(openTestDetails, assessment);
            removeAddedFieldsIfAny(openTestDetails, assessment);
        }

    }

    private void removeAddedFieldsIfAny(AssessmentRegistrationsDto.OpenTestDetails openTestDetails, Assessment assessment) {
        List<String> deletedFieldKeys = openTestDetails.getRegistrationFormDetails().getRemovedCustomAddedFields().stream().map(RegistrationFieldDto::getKey).toList();
        if (!deletedFieldKeys.isEmpty()) {
            assessmentCustomFieldRepository.softDeleteByAssessmentIdAndFieldKeys(assessment.getId(), deletedFieldKeys);
        }
    }

    private void addCustomRegistrationFieldsToAsessment(AssessmentRegistrationsDto.OpenTestDetails openTestDetails, Assessment assessment) {
        List<AssessmentCustomField> customFields = new ArrayList<>();
        for (RegistrationFieldDto registrationFieldDto : openTestDetails.getRegistrationFormDetails().getAddedCustomAddedFields()) {
            customFields.add(createRegistrationField(registrationFieldDto, assessment));
        }
        assessmentCustomFieldRepository.saveAll(customFields);
    }

    private AssessmentCustomField createRegistrationField(RegistrationFieldDto registrationFieldDto, Assessment assessment) {
        AssessmentCustomField assessmentCustomField = new AssessmentCustomField();
        assessmentCustomField.setAssessment(assessment);
        assessmentCustomField.setFieldKey(registrationFieldDto.getName().toLowerCase().trim().replace(" ", "_"));
        assessmentCustomField.setFieldName(registrationFieldDto.getName().trim());
        assessmentCustomField.setFieldType(registrationFieldDto.getType().trim());
        assessmentCustomField.setIsMandatory(registrationFieldDto.getIsMandatory());
        assessmentCustomField.setStatus(ACTIVE.name());
        assessmentCustomField.setCommaSeparatedOptions(registrationFieldDto.getCommaSeparatedOptions());
        return assessmentCustomField;
    }

    private void preRegisterBatches(List<String> addedBatches, String instituteId, Assessment assessment) {
        List<AssessmentBatchRegistration> batchRegistrations = new ArrayList<>();
        for (String batchId : addedBatches) {
            batchRegistrations.add(addBatchToAssessment(instituteId, batchId, assessment));
        }
        assessmentBatchRegistrationService.addMultipleRegistrations(batchRegistrations);
    }

    private void removeBatches(List<String> deletedBatches, String instituteId, Assessment assessment) {
        if (deletedBatches.isEmpty()) return;
        assessmentBatchRegistrationService.softDeleteRegistrationsByIds(deletedBatches, instituteId, assessment.getId());
    }

    private void removeParticipants(List<BasicParticipantDTO> deletedParticipants, String instituteId, Assessment assessment) {
        if (deletedParticipants.isEmpty()) return;
        assessmentUserRegistrationRepository.softDeleteByAssessmentIdAndUserIdsAndInstituteId(assessment.getId(), deletedParticipants.stream().map(BasicParticipantDTO::getUserId).toList(), instituteId);
    }

    AssessmentBatchRegistration addBatchToAssessment(String instituteId, String batchId, Assessment assessment) {
        AssessmentBatchRegistration assessmentBatchRegistration = new AssessmentBatchRegistration();
        assessmentBatchRegistration.setAssessment(assessment);
        assessmentBatchRegistration.setBatchId(batchId);
        assessmentBatchRegistration.setInstituteId(instituteId);
        assessmentBatchRegistration.setStatus(ACTIVE.name());
        assessmentBatchRegistration.setRegistrationTime(new Date());
        return assessmentBatchRegistration;
    }


    AssessmentUserRegistration addUserToAssessment(BasicParticipantDTO basicParticipantDTO, String adminUserId, String instituteId, Assessment assessment) {
        AssessmentUserRegistration assessmentParticipantRegistration = new AssessmentUserRegistration();
        assessmentParticipantRegistration.setAssessment(assessment);
        assessmentParticipantRegistration.setUserId(basicParticipantDTO.getUserId());
        assessmentParticipantRegistration.setUsername(basicParticipantDTO.getUsername());
        assessmentParticipantRegistration.setParticipantName(basicParticipantDTO.getFullName());
        assessmentParticipantRegistration.setPhoneNumber(basicParticipantDTO.getMobileNumber());
        assessmentParticipantRegistration.setFaceFileId(basicParticipantDTO.getFileId());
        assessmentParticipantRegistration.setUserEmail(basicParticipantDTO.getEmail());
        assessmentParticipantRegistration.setReattemptCount((basicParticipantDTO.getReattemptCount() == null) ? assessment.getReattemptCount() : basicParticipantDTO.getReattemptCount());
        assessmentParticipantRegistration.setInstituteId(instituteId);
        assessmentParticipantRegistration.setStatus(ACTIVE.name());
        assessmentParticipantRegistration.setSource(UserRegistrationSources.ADMIN_PRE_REGISTRATION.name());
        assessmentParticipantRegistration.setSourceId(adminUserId);
        assessmentParticipantRegistration.setRegistrationTime(new Date());
        return assessmentParticipantRegistration;
    }

    public ResponseEntity<List<AssessmentUserRegistration>> assessmentAdminParticipants(CustomUserDetails user, String instituteId, String assessmentId) {

        Optional<Assessment> assessmentOptional = assessmentRepository.findByAssessmentIdAndInstituteId(assessmentId, instituteId);

        if (assessmentOptional.isEmpty()) {
            return ResponseEntity.ok().body(List.of());
        }
        List<AssessmentUserRegistration> assessmentUserRegistrations = assessmentOptional.get().getUserRegistrations().stream().toList();
        return ResponseEntity.ok(assessmentUserRegistrations);
    }

    public ClosedAssessmentParticipantsResponse getAllParticipantsForClosedAssessment(CustomUserDetails user, String instituteId, String assessmentId, AssessmentUserFilter filter, Integer pageNo, Integer pageSize) {
        if(Objects.isNull(filter)) throw new VacademyException("Invalid Filter Request");
        Sort sortingColumns = createSortObject(filter.getSortColumns());

        Pageable pageable = PageRequest.of(pageNo, pageSize, sortingColumns);
        Page<ParticipantsDetailsDto> registeredUserPage = null;

        //Handle Case for BATCH REGISTRATION
        if(filter.getRegistrationSource().equals(UserRegistrationSources.BATCH_PREVIEW_REGISTRATION.name())){
            registeredUserPage = handleCaseForBatchRegistration(assessmentId,instituteId,filter, pageable);
        }
        //Handle Case for ADMIN PRE REGISTRATION
        else if(filter.getRegistrationSource().equals(UserRegistrationSources.ADMIN_PRE_REGISTRATION.name())){
            registeredUserPage = handleCaseForAdminPreRegistration(assessmentId, instituteId, filter, pageable);
        }
        else throw new VacademyException("Invalid Source Request");

        return createAllRegisteredUserForClosedTest(registeredUserPage);
    }


    /**
     * Handles the case for admin pre-registration by fetching the list of registered users
     * based on the given filter conditions.
     */
    private Page<ParticipantsDetailsDto> handleCaseForAdminPreRegistration(
            String assessmentId,
            String instituteId,
            AssessmentUserFilter filter,
            Pageable pageable) {

        Page<ParticipantsDetailsDto> registeredUserPage = null;



        // Check if the attempt type is "PENDING"
        if (isPendingAttempt(filter)) {

            // If a name filter is provided, search for pre-registered and pending users with name filtering
            if (StringUtils.hasText(filter.getName())) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterWithSearchForPreRegistrationAndPending(
                                filter.getName(), assessmentId, instituteId, filter.getStatus(),
                                filter.getRegistrationSource(), pageable);
            }

            // If no results found, search for admin pre-registered and pending users
            if (Objects.isNull(registeredUserPage)) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterAdminPreRegistrationAndPending(
                                assessmentId, instituteId, filter.getStatus(),
                                filter.getRegistrationSource(), pageable);
            }

        } else {
            // If a name filter is provided, search for users with name filtering
            if (StringUtils.hasText(filter.getName())) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterWithSearchForSource(
                                filter.getName(), assessmentId, instituteId, filter.getStatus(),
                                filter.getAttemptType(), filter.getRegistrationSource(), pageable);
            }

            // If no results found, search for users based on batch, attempt type, and registration source
            if (Objects.isNull(registeredUserPage)) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterForSource(
                                assessmentId, instituteId, filter.getBatches(),
                                filter.getAttemptType(), filter.getRegistrationSource(), pageable);
            }
        }

        // Return the filtered list of registered users
        return registeredUserPage;
    }

    private Page<ParticipantsDetailsDto> handleCaseForBatchRegistration(String assessmentId, String instituteId, AssessmentUserFilter filter, Pageable pageable) {
        Page<ParticipantsDetailsDto> registeredUserPage = null;
        if(isPendingAttempt(filter)){
            //TODO: Send request to admin core to get pending list for batch
        }
        else{
            //Handle Case for Attempted case i.e LIVE,PREVIEW,ENDED
            if(StringUtils.hasText(filter.getName())){
                registeredUserPage = assessmentUserRegistrationRepository.findUserRegistrationWithFilterWithSearchForBatch(filter.getName(),assessmentId, instituteId, filter.getBatches(), filter.getStatus(), filter.getAttemptType(),pageable);
            }
            if(Objects.isNull(registeredUserPage)){
                registeredUserPage= assessmentUserRegistrationRepository.findUserRegistrationWithFilterForBatch(assessmentId, instituteId, filter.getBatches(), filter.getStatus(), filter.getAttemptType(), pageable);
            }
        }

        return registeredUserPage;
    }

    /**
     * Retrieves all participants for an open assessment based on the provided filter criteria.
     *
     * @param user         The authenticated user details.
     * @param instituteId  The ID of the institute.
     * @param assessmentId The ID of the assessment.
     * @param filter       The filter criteria for fetching participants.
     * @param pageNo       The page number for pagination.
     * @param pageSize     The size of each page for pagination.
     * @return A {@link ClosedAssessmentParticipantsResponse} containing the list of participants.
     * @throws VacademyException if the filter is null.
     */
    public ClosedAssessmentParticipantsResponse getAllParticipantsForOpenAssessment(
            CustomUserDetails user,
            String instituteId,
            String assessmentId,
            AssessmentUserFilter filter,
            Integer pageNo,
            Integer pageSize) {

        // Validate the filter
        if (Objects.isNull(filter)) {
            throw new VacademyException("Invalid Filter Request");
        }

        // Create sorting object based on filter parameters
        Sort sortingColumns = createSortObject(filter.getSortColumns());

        // Define pagination settings
        Pageable pageable = PageRequest.of(pageNo, pageSize, sortingColumns);
        Page<ParticipantsDetailsDto> registeredUserPage = null;

        // Check if the assessment attempt is pending
        if (isPendingAttempt(filter)) {

            // If a name filter is provided, search with name-based filtering
            if (StringUtils.hasText(filter.getName())) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterWithSearchForPreRegistrationAndPending(
                                filter.getName(), assessmentId, instituteId, filter.getStatus(),
                                filter.getRegistrationSource(), pageable);
            }

            // If no results are found, perform a broader search
            if (Objects.isNull(registeredUserPage)) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterAdminPreRegistrationAndPending(
                                assessmentId, instituteId, filter.getStatus(),
                                filter.getRegistrationSource(), pageable);
            }

        } else {
            // If a name filter is provided, search with name-based filtering
            if (StringUtils.hasText(filter.getName())) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterWithSearchForSource(
                                filter.getName(), assessmentId, instituteId, filter.getStatus(),
                                filter.getAttemptType(), filter.getRegistrationSource(), pageable);
            }

            // If no results are found, perform a broader search
            if (Objects.isNull(registeredUserPage)) {
                registeredUserPage = assessmentUserRegistrationRepository
                        .findUserRegistrationWithFilterForSource(
                                assessmentId, instituteId, filter.getStatus(),
                                filter.getAttemptType(), filter.getRegistrationSource(), pageable);
            }
        }

        // Convert the retrieved data into the required response format
        return createAllRegisteredUserForClosedTest(registeredUserPage);
    }

    private ClosedAssessmentParticipantsResponse createAllRegisteredUserForClosedTest(Page<ParticipantsDetailsDto> registrationPage) {
        if(Objects.isNull(registrationPage)){
            return ClosedAssessmentParticipantsResponse.builder().content(new ArrayList<>())
                    .pageNo(0)
                    .pageSize(0)
                    .last(true)
                    .totalPages(0)
                    .totalElements(0)
                    .build();
        }

        List<ParticipantsDetailsDto> content = registrationPage.getContent();
        return ClosedAssessmentParticipantsResponse.builder().content(content)
                .pageNo(registrationPage.getNumber())
                .pageSize(registrationPage.getSize())
                .last(registrationPage.isLast())
                .totalPages(registrationPage.getTotalPages())
                .totalElements(registrationPage.getTotalElements()).build();
    }

    //Sorting Object to Sort the values
    private Sort createSortObject(Map<String, String> sortColumns) {
        if(sortColumns==null) return Sort.unsorted();

        List<Sort.Order> orders = new ArrayList<>();

        for (Map.Entry<String, String> entry : sortColumns.entrySet()) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(entry.getValue()) ? Sort.Direction.DESC : Sort.Direction.ASC;
            orders.add(new Sort.Order(direction, entry.getKey()));
        }
        return Sort.by(orders);
    }

    /**
     * Retrieves all participants for a given assessment based on the provided filter.
     *
     * @param user          The authenticated user details.
     * @param instituteId   The ID of the institute.
     * @param assessmentId  The ID of the assessment.
     * @param filter        The filter criteria for fetching participants.
     * @param pageNo        The page number for pagination.
     * @param pageSize      The size of each page for pagination.
     * @return ResponseEntity containing a list of participants matching the criteria.
     * @throws VacademyException if the filter is invalid or the assessment type is null.
     */
    public ResponseEntity<ClosedAssessmentParticipantsResponse> getAllParticipantsForAssessment(
            CustomUserDetails user,
            String instituteId,
            String assessmentId,
            AssessmentUserFilter filter,
            Integer pageNo,
            Integer pageSize) {

        // Validate the filter and ensure it contains an assessment type
        if (Objects.isNull(filter) || Objects.isNull(filter.getAssessmentType())) {
            throw new VacademyException("Invalid Filter Request");
        }

        // Determine whether to fetch participants for an open or closed assessment
        ClosedAssessmentParticipantsResponse response = filter.getAssessmentType()
                .equals(AssessmentVisibility.PUBLIC.name())
                ? getAllParticipantsForOpenAssessment(user, instituteId, assessmentId, filter, pageNo, pageSize)
                : getAllParticipantsForClosedAssessment(user, instituteId, assessmentId, filter, pageNo, pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * Checks if the assessment attempt is pending based on the provided filter.
     *
     * @param filter The assessment user filter.
     * @return true if there is only one attempt type and it is "PENDING", otherwise false.
     */
    private boolean isPendingAttempt(AssessmentUserFilter filter) {
        // Return false if the filter is null
        if (Objects.isNull(filter)) {
            return false;
        }

        // Check if the only attempt type in the filter is "PENDING"
        return filter.getAttemptType().size() == 1 &&
                filter.getAttemptType().get(0).equals(UserRegistrationFilterEnum.PENDING.name());
    }

    public Integer getAssessmentCountForUserId(CustomUserDetails user,String instituteId) {
        return assessmentUserRegistrationRepository.countDistinctAssessmentsByUserId(user.getUserId(),instituteId);
    }

}
