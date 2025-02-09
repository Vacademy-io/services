package vacademy.io.admin_core_service.features.learner_tracking.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vacademy.io.admin_core_service.features.learner_tracking.dto.ActivityLogDTO;
import vacademy.io.admin_core_service.features.learner_tracking.entity.ActivityLog;
import vacademy.io.admin_core_service.features.learner_tracking.entity.DocumentTracked;
import vacademy.io.admin_core_service.features.learner_tracking.entity.VideoTracked;
import vacademy.io.admin_core_service.features.learner_tracking.repository.ActivityLogRepository;
import vacademy.io.admin_core_service.features.learner_tracking.repository.DocumentTrackedRepository;
import vacademy.io.admin_core_service.features.learner_tracking.repository.VideoTrackedRepository;
import vacademy.io.common.auth.model.CustomUserDetails;
import vacademy.io.common.exceptions.VacademyException;

import java.sql.Date;
import java.util.List;
import java.util.Objects;

@Service
public class LearnerTrackingService {

    private final ActivityLogRepository activityLogRepository;
    private final DocumentTrackedRepository documentTrackedRepository;
    private final VideoTrackedRepository videoTrackedRepository;

    @Autowired
    public LearnerTrackingService(
            ActivityLogRepository activityLogRepository,
            DocumentTrackedRepository documentTrackedRepository,
            VideoTrackedRepository videoTrackedRepository) {
        this.activityLogRepository = activityLogRepository;
        this.documentTrackedRepository = documentTrackedRepository;
        this.videoTrackedRepository = videoTrackedRepository;
    }

    @Transactional
    public ActivityLogDTO addDocumentActivityLog(ActivityLogDTO activityLogDTO, String slideId, String userId, CustomUserDetails user) {
        validateDocumentActivityLogDTO(activityLogDTO);
        ActivityLog activityLog = saveActivityLog(activityLogDTO, slideId, userId);
        saveDocumentTracking(activityLogDTO, activityLog);
        return activityLog.toActivityLogDTO();
    }

    @Transactional
    public ActivityLogDTO addVideoActivityLog(ActivityLogDTO activityLogDTO, String slideId, String userId, CustomUserDetails user) {
        validateVideoActivityLogDTO(activityLogDTO);
        ActivityLog activityLog = saveActivityLog(activityLogDTO, slideId, userId);
        saveVideoTracking(activityLogDTO, activityLog);
        return activityLog.toActivityLogDTO();
    }

    @Transactional
    public ActivityLogDTO updateDocumentActivityLogs(ActivityLogDTO activityLogDTO, String activityId, CustomUserDetails user) {
        ActivityLog activityLog = activityLogRepository.findById(activityId)
                .orElseThrow(() -> new VacademyException("Activity Log not found"));

        updateActivityFields(activityLog, activityLogDTO);
        activityLogRepository.save(activityLog);
        documentTrackedRepository.deleteByActivityId(activityId);
        saveDocumentTracking(activityLogDTO, activityLog);
        return activityLog.toActivityLogDTO();
    }

    @Transactional
    public ActivityLogDTO updateVideoActivityLogs(ActivityLogDTO activityLogDTO, String activityId, CustomUserDetails user) {
        ActivityLog activityLog = activityLogRepository.findById(activityId)
                .orElseThrow(() -> new VacademyException("Activity Log not found"));

        updateActivityFields(activityLog, activityLogDTO);
        activityLogRepository.save(activityLog);
        videoTrackedRepository.deleteByActivityId(activityId);
        saveVideoTracking(activityLogDTO, activityLog);
        return activityLog.toActivityLogDTO();
    }

    private ActivityLog saveActivityLog(ActivityLogDTO activityLogDTO, String slideId, String userId) {
        return activityLogRepository.save(new ActivityLog(activityLogDTO, userId, slideId));
    }

    private void saveDocumentTracking(ActivityLogDTO activityLogDTO, ActivityLog activityLog) {
        List<DocumentTracked> documentTrackedList = activityLogDTO.getDocuments().stream()
                .map(documentActivityLogDTO -> new DocumentTracked(documentActivityLogDTO, activityLog))
                .toList();
        documentTrackedRepository.saveAll(documentTrackedList);
    }

    private void saveVideoTracking(ActivityLogDTO activityLogDTO, ActivityLog activityLog) {
        List<VideoTracked> videoTrackedList = activityLogDTO.getVideos().stream()
                .map(videoActivityLogDTO -> new VideoTracked(videoActivityLogDTO, activityLog))
                .toList();
        videoTrackedRepository.saveAll(videoTrackedList);
    }

    private void updateActivityFields(ActivityLog activityLog, ActivityLogDTO activityLogDTO) {
        if (activityLogDTO.getStartTimeInMillis() != null) {
            activityLog.setStartTime(new Date(activityLogDTO.getStartTimeInMillis()));
        }
        if (activityLogDTO.getEndTimeInMillis() != null) {
            activityLog.setEndTime(new Date(activityLogDTO.getEndTimeInMillis()));
        }
        if (activityLogDTO.getPercentageWatched() != null) {
            activityLog.setPercentageWatched(activityLogDTO.getPercentageWatched());
        }
    }

    private void validateVideoActivityLogDTO(ActivityLogDTO dto) {
        if (Objects.isNull(dto)) {
            throw new VacademyException("Invalid request. Activity Log cannot be null.");
        }
        if (Objects.isNull(dto.getVideos())) {
            throw new VacademyException("Invalid request. Videos cannot be null.");
        }
    }
    private void validateDocumentActivityLogDTO(ActivityLogDTO dto) {
        if (Objects.isNull(dto)) {
            throw new VacademyException("Invalid request. Activity Log cannot be null.");
        }
        if (Objects.isNull(dto.getDocuments())) {
            throw new VacademyException("Invalid request. Documents cannot be null.");
        }
    }
}