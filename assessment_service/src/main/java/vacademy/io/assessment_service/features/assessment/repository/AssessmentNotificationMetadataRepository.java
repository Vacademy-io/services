package vacademy.io.assessment_service.features.assessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vacademy.io.assessment_service.features.assessment.entity.AssessmentNotificationMetadata;

public interface AssessmentNotificationMetadataRepository extends JpaRepository<AssessmentNotificationMetadata, String> {
}