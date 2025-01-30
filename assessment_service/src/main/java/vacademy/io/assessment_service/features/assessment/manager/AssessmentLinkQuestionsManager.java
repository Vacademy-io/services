package vacademy.io.assessment_service.features.assessment.manager;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import vacademy.io.assessment_service.features.assessment.dto.AssessmentQuestionPreviewDto;
import vacademy.io.assessment_service.features.assessment.dto.AssessmentSaveResponseDto;
import vacademy.io.assessment_service.features.assessment.dto.SectionAddEditRequestDto;
import vacademy.io.assessment_service.features.assessment.dto.create_assessment.AddQuestionsAssessmentDetailsDTO;
import vacademy.io.assessment_service.features.assessment.entity.Assessment;
import vacademy.io.assessment_service.features.assessment.entity.QuestionAssessmentSectionMapping;
import vacademy.io.assessment_service.features.assessment.entity.Section;
import vacademy.io.assessment_service.features.assessment.enums.ProblemRandomType;
import vacademy.io.assessment_service.features.assessment.repository.SectionRepository;
import vacademy.io.assessment_service.features.assessment.service.assessment_get.AssessmentService;
import vacademy.io.assessment_service.features.assessment.service.bulk_entry_services.QuestionAssessmentSectionMappingService;
import vacademy.io.assessment_service.features.question_core.entity.Question;
import vacademy.io.assessment_service.features.rich_text.entity.AssessmentRichTextData;
import vacademy.io.assessment_service.features.rich_text.enums.TextType;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.exceptions.VacademyException;

import java.util.*;

import static org.hibernate.event.internal.EntityState.DELETED;
import static org.hibernate.resource.transaction.spi.TransactionStatus.ACTIVE;

@Component
public class AssessmentLinkQuestionsManager {

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    AssessmentService assessmentService;

    @Autowired
    QuestionAssessmentSectionMappingService questionAssessmentSectionMappingService;

    @Transactional
    public ResponseEntity<AssessmentSaveResponseDto> saveQuestionsToAssessment(CustomUserDetails user, AddQuestionsAssessmentDetailsDTO addQuestionsAssessmentDetailsDTO, String assessmentId, String instituteId, String type) {

        Optional<Assessment> assessmentOptional = assessmentService.getAssessmentWithActiveSections(assessmentId, instituteId);

        if (assessmentOptional.isEmpty()) {
            throw new VacademyException("Assessment not found");
        }

        for (SectionAddEditRequestDto sectionAddEditRequestDto : addQuestionsAssessmentDetailsDTO.getAddedSections()) {
            addSectionToAssessment(user, sectionAddEditRequestDto, assessmentOptional.get(), instituteId, type);
        }

        for (SectionAddEditRequestDto sectionAddEditRequestDto : addQuestionsAssessmentDetailsDTO.getUpdatedSections()) {
            Optional<Section> thisSection = assessmentOptional.get().getSections().stream().filter((s) -> s.getId().equals(sectionAddEditRequestDto.getSectionId())).findFirst();
            if (thisSection.isEmpty()) continue;
            updateSectionForAssessment(thisSection.get(), sectionAddEditRequestDto, assessmentOptional.get(), instituteId, type);
        }

        for (SectionAddEditRequestDto sectionAddEditRequestDto : addQuestionsAssessmentDetailsDTO.getDeletedSections()) {
            Optional<Section> thisSection = assessmentOptional.get().getSections().stream().filter((s) -> s.getId().equals(sectionAddEditRequestDto.getSectionId())).findFirst();
            if (thisSection.isEmpty()) continue;
            deleteSectionForAssessment(thisSection.get(), sectionAddEditRequestDto, assessmentId, instituteId, type);
        }

        AssessmentSaveResponseDto assessmentSaveResponseDto = new AssessmentSaveResponseDto(assessmentId, assessmentOptional.get().getStatus());
        return ResponseEntity.ok(assessmentSaveResponseDto);
    }

    void validateMarkingScheme(SectionAddEditRequestDto.QuestionAndMarking questionAndMarkings) {
        //Todo: validate marking scheme
    }

    void addSectionToAssessment(CustomUserDetails user, SectionAddEditRequestDto sectionAddEditRequestDto, Assessment
            assessment, String instituteId, String type) {
        Section newSection = createUpdateSection(new Section(), sectionAddEditRequestDto, assessment, ACTIVE.name());
        List<QuestionAssessmentSectionMapping> mappings = new ArrayList<>();
        for (int i = 0; i < sectionAddEditRequestDto.getQuestionAndMarking().size(); i++) {
            mappings.add(createFromQuestionSectionAddEditRequestDto(sectionAddEditRequestDto.getQuestionAndMarking().get(i), newSection, assessment));
        }
        questionAssessmentSectionMappingService.addMultipleMappings(mappings);
    }

    QuestionAssessmentSectionMapping createFromQuestionSectionAddEditRequestDto
            (SectionAddEditRequestDto.QuestionAndMarking questionAndMarking, Section section, Assessment assessment) {
        validateMarkingScheme(questionAndMarking);

        QuestionAssessmentSectionMapping mapping = new QuestionAssessmentSectionMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setSection(section);
        mapping.setStatus(ACTIVE.name());
        mapping.setQuestion(new Question(questionAndMarking.getQuestionId()));
        mapping.setQuestionOrder(questionAndMarking.getQuestionOrder());
        mapping.setQuestionDurationInMin(questionAndMarking.getQuestionDurationInMin());
        mapping.setMarkingJson(questionAndMarking.getMarkingJson());
        return mapping;
    }

    QuestionAssessmentSectionMapping updateFromQuestionSectionAddEditRequestDto
            (SectionAddEditRequestDto.QuestionAndMarking questionAndMarking, Section section, Assessment assessment) {
        validateMarkingScheme(questionAndMarking);
        QuestionAssessmentSectionMapping mapping = questionAssessmentSectionMappingService.getMappingById(questionAndMarking.getQuestionId(), section.getId());
        if (mapping == null) return null;
        mapping.setSection(section);
        mapping.setStatus(ACTIVE.name());
        mapping.setQuestion(new Question(questionAndMarking.getQuestionId()));
        mapping.setQuestionOrder(questionAndMarking.getQuestionOrder());
        mapping.setQuestionDurationInMin(questionAndMarking.getQuestionDurationInMin());
        mapping.setMarkingJson(questionAndMarking.getMarkingJson());
        return mapping;
    }

    void updateSectionForAssessment(Section section, SectionAddEditRequestDto sectionAddEditRequestDto, Assessment
            assessment, String instituteId, String type) {
        Section updatedSection = createUpdateSection(section, sectionAddEditRequestDto, assessment, ACTIVE.name());
        List<String> deletedQuestionIds = new ArrayList<>();
        List<QuestionAssessmentSectionMapping> addedQuestions = new ArrayList<>();
        for (int i = 0; i < sectionAddEditRequestDto.getQuestionAndMarking().size(); i++) {
            if (sectionAddEditRequestDto.getQuestionAndMarking().get(i).getIsDeleted()) {
                deletedQuestionIds.add(sectionAddEditRequestDto.getQuestionAndMarking().get(i).getQuestionId());
            }
            if (sectionAddEditRequestDto.getQuestionAndMarking().get(i).getIsAdded()) {
                addedQuestions.add(createFromQuestionSectionAddEditRequestDto(sectionAddEditRequestDto.getQuestionAndMarking().get(i), updatedSection, assessment));
            }
            if (sectionAddEditRequestDto.getQuestionAndMarking().get(i).getIsUpdated()) {
                var updatedMapping = updateFromQuestionSectionAddEditRequestDto(sectionAddEditRequestDto.getQuestionAndMarking().get(i), updatedSection, assessment);
                if (updatedMapping != null)
                    addedQuestions.add(updatedMapping);
            }
        }
        questionAssessmentSectionMappingService.softDeleteMappingsByQuestionIdsAndSectionId(deletedQuestionIds, section.getId());
        questionAssessmentSectionMappingService.addMultipleMappings(addedQuestions);
    }

    void deleteSectionForAssessment(Section section, SectionAddEditRequestDto sectionAddEditRequestDto, String
            assessmentId, String instituteId, String type) {
        section.setStatus(DELETED.name());
        sectionRepository.save(section);
    }

    public Section createUpdateSection(Section section, SectionAddEditRequestDto
            sectionAddEditRequestDto, Assessment assessment, String status) {
        section.setAssessment(assessment);

        Optional.ofNullable(sectionAddEditRequestDto.getSectionName()).ifPresent(section::setName);
        Optional.ofNullable(sectionAddEditRequestDto.getSectionOrder()).ifPresent(section::setSectionOrder);
        Optional.ofNullable(status).ifPresent(section::setStatus);
        Optional.ofNullable(sectionAddEditRequestDto.getSectionDuration()).ifPresent(section::setDuration);
        Optional.ofNullable(sectionAddEditRequestDto.getTotalMarks()).ifPresent(section::setTotalMarks);
        Optional.ofNullable(sectionAddEditRequestDto.getCutoffMarks()).ifPresent(section::setCutOffMarks);
        if (!ObjectUtils.isEmpty(sectionAddEditRequestDto.getProblemRandomization()) && sectionAddEditRequestDto.getProblemRandomization())
            section.setProblemRandomType(ProblemRandomType.RANDOM.name());
        if (!ObjectUtils.isEmpty(sectionAddEditRequestDto.getSectionDescriptionHtml()))
            section.setDescription(new AssessmentRichTextData(null, TextType.HTML.name(), sectionAddEditRequestDto.getSectionDescriptionHtml()));
        return sectionRepository.save(section);
    }

    public Map<String, List<AssessmentQuestionPreviewDto>> getQuestionsOfSection(CustomUserDetails user, String assessmentId, String sectionIds) {
        Map<String, List<AssessmentQuestionPreviewDto>> response = new HashMap<>();
        List<String> sectionIdList = Arrays.asList(sectionIds.split(","));
        List<QuestionAssessmentSectionMapping> mappings = questionAssessmentSectionMappingService.getQuestionAssessmentSectionMappingBySectionIds(sectionIdList);

        for (QuestionAssessmentSectionMapping mapping : mappings) {
            String sectionId = mapping.getSection().getId();
            if (!response.containsKey(sectionId)) {
                response.put(sectionId, new ArrayList<>());
            }

            AssessmentQuestionPreviewDto fillOptionsExplanationsOfQuestion = new AssessmentQuestionPreviewDto(mapping.getQuestion(), mapping);
            fillOptionsExplanationsOfQuestion.fillOptionsExplanationsOfQuestion(mapping.getQuestion());
            response.get(sectionId).add(fillOptionsExplanationsOfQuestion);
        }
        return response;
    }
}
