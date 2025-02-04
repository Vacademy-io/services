package vacademy.io.admin_core_service.features.study_library.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vacademy.io.admin_core_service.features.chapter.dto.ChapterDTO;
import vacademy.io.admin_core_service.features.chapter.dto.ChapterDTOWithDetail;
import vacademy.io.admin_core_service.features.chapter.entity.Chapter;
import vacademy.io.admin_core_service.features.course.dto.CourseDTO;
import vacademy.io.admin_core_service.features.course.dto.CourseDTOWithDetails;
import vacademy.io.admin_core_service.features.level.repository.LevelRepository;
import vacademy.io.admin_core_service.features.module.dto.ModuleDTO;
import vacademy.io.admin_core_service.features.module.repository.ModuleChapterMappingRepository;
import vacademy.io.admin_core_service.features.packages.repository.PackageRepository;
import vacademy.io.admin_core_service.features.slide.repository.SlideRepository;
import vacademy.io.admin_core_service.features.study_library.dto.LevelDTOWithDetails;
import vacademy.io.admin_core_service.features.study_library.dto.ModuleDTOWithDetails;
import vacademy.io.admin_core_service.features.study_library.dto.SessionDTOWithDetails;
import vacademy.io.admin_core_service.features.module.repository.SubjectModuleMappingRepository;
import vacademy.io.admin_core_service.features.subject.repository.SubjectRepository;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.exceptions.VacademyException;
import vacademy.io.common.institute.dto.SessionDTO;
import vacademy.io.common.institute.dto.SubjectDTO;
import vacademy.io.common.institute.entity.Level;
import vacademy.io.common.institute.entity.PackageEntity;
import vacademy.io.common.institute.entity.module.Module;
import vacademy.io.common.institute.entity.session.SessionProjection;
import vacademy.io.common.institute.entity.student.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class StudyLibraryService {

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private SubjectModuleMappingRepository subjectModuleMappingRepository;

    @Autowired
    private ModuleChapterMappingRepository moduleChapterMappingRepository;

    @Autowired
    private SlideRepository slideRepository;

    public List<CourseDTOWithDetails> getStudyLibraryInitDetails(String instituteId) {
        if (Objects.isNull(instituteId)) {
            throw new VacademyException("Please provide instituteId");
        }
        List<CourseDTOWithDetails>courses = new ArrayList<>();
        List<PackageEntity>packages = packageRepository.findDistinctPackagesByInstituteId(instituteId);
        for (PackageEntity course : packages) {
            List<SessionProjection> packageSessions = packageRepository.findDistinctSessionsByPackageId(course.getId());
            List<SessionDTOWithDetails> sessionDTOWithDetails = new ArrayList<>();
            for(SessionProjection sessionProjection : packageSessions) {
                List<LevelDTOWithDetails> levelWithDetails = new ArrayList<>();
                List<Level>levels = levelRepository.findDistinctLevelsByInstituteIdAndSessionId(instituteId, sessionProjection.getId());
                for (Level level: levels) {
                    List<Subject> subjects = subjectRepository.findDistinctSubjectsByLevelId(level.getId());
                    LevelDTOWithDetails levelDTOWithDetails = getLevelDTOWithDetails(subjects, level);
                    levelWithDetails.add(levelDTOWithDetails);
                }
                sessionDTOWithDetails.add(getSessionDTOWithDetails(sessionProjection, levelWithDetails));
            }
            CourseDTOWithDetails courseDTOWithDetails = new CourseDTOWithDetails(new CourseDTO(course), sessionDTOWithDetails);
            courses.add(courseDTOWithDetails);
        }
        return courses;
    }

    public LevelDTOWithDetails getLevelDTOWithDetails(List<Subject> subjects, Level level) {
        List<SubjectDTO> subjectDTOS = new ArrayList<>();
        for (Subject subject : subjects) {
            SubjectDTO subjectDTO = new SubjectDTO();
            subjectDTO.setId(subject.getId());
            subjectDTO.setSubjectName(subject.getSubjectName());
            subjectDTO.setSubjectCode(subject.getSubjectCode());
            subjectDTO.setCredit(subject.getCredit());
            subjectDTO.setThumbnailId(subject.getThumbnailId());
            subjectDTOS.add(subjectDTO);
        }
        LevelDTOWithDetails levelDTOWithDetails = new LevelDTOWithDetails(level, subjectDTOS);
        return levelDTOWithDetails;
    }

    public SessionDTOWithDetails getSessionDTOWithDetails(SessionProjection sessionProjection, List<LevelDTOWithDetails> levelWithDetails) {
        SessionDTOWithDetails sessionDTOWithDetails = new SessionDTOWithDetails();
        SessionDTO sessionDTO = new SessionDTO(sessionProjection);
        sessionDTOWithDetails.setLevelWithDetails(levelWithDetails);
        sessionDTOWithDetails.setSessionDTO(sessionDTO);
        return sessionDTOWithDetails;
    }

    public List<ModuleDTOWithDetails> getModulesDetailsWithChapters(String subjectId,String packageSessionId, CustomUserDetails user) {
        if (Objects.isNull(subjectId)){
            throw new VacademyException("Please provide subjectId");
        }
       List<Module> modules = subjectModuleMappingRepository.findModulesBySubjectIdAndPackageSessionId(subjectId,packageSessionId);
       List<ModuleDTOWithDetails> moduleDTOWithDetails = new ArrayList<>();
       for (Module module: modules) {
           List<Chapter> chapters = moduleChapterMappingRepository.findChaptersByModuleIdAndStatusNotDeleted(module.getId(),packageSessionId);
           List<ChapterDTOWithDetail> chapterDTOS = chapters.stream().map(this::mapToChapterDTOWithDetail).toList();
           ModuleDTOWithDetails moduleDTOWithDetails1 = new ModuleDTOWithDetails(new ModuleDTO(module), chapterDTOS);
           moduleDTOWithDetails.add(moduleDTOWithDetails1);
       }
       return moduleDTOWithDetails;
    }

    public ChapterDTOWithDetail mapToChapterDTOWithDetail(Chapter chapter) {
        ChapterDTOWithDetail chapterDTOWithDetail = new ChapterDTOWithDetail();
        ChapterDTO chapterDTO = chapter.mapToDTO();
        chapterDTOWithDetail.setChapter(chapterDTO);
        chapterDTOWithDetail.setSlidesCount(slideRepository.countSlidesByChapterId(chapter.getId()));
        return chapterDTOWithDetail;
    }
}
