package vacademy.io.admin_core_service.features.learner_tracking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vacademy.io.admin_core_service.features.learner_tracking.dto.DocumentActivityLogDTO;

import java.sql.Timestamp;

@Entity
@Table(name = "document_tracked")
@Getter
@Setter
@NoArgsConstructor
public class DocumentTracked {

    @Id
    @Column(length = 255, nullable = false)
    private String id;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private ActivityLog activityLog;

    @Column(name = "start_time")
    private Timestamp startTime;

    @Column(name = "end_time")
    private Timestamp endTime;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;

    public DocumentTracked(DocumentActivityLogDTO documentActivityLogDTO, ActivityLog activityLog) {
        this.id = documentActivityLogDTO.getId();
        this.activityLog = activityLog;
        if (documentActivityLogDTO.getStartTimeInMillis() != null) {
            this.startTime = new Timestamp(documentActivityLogDTO.getStartTimeInMillis());
        }
        if (documentActivityLogDTO.getEndTimeInMillis() != null) {
            this.endTime = new Timestamp(documentActivityLogDTO.getEndTimeInMillis());
        }
        this.pageNumber = documentActivityLogDTO.getPageNumber();
    }

    public DocumentActivityLogDTO documentActivityLogDTO() {
        DocumentActivityLogDTO documentActivityLogDTO = new DocumentActivityLogDTO();
        documentActivityLogDTO.setId(id);
        documentActivityLogDTO.setStartTimeInMillis(startTime != null ? startTime.getTime() : null);
        documentActivityLogDTO.setEndTimeInMillis(endTime != null ? endTime.getTime() : null);
        documentActivityLogDTO.setPageNumber(pageNumber);
        return documentActivityLogDTO;
    }
}