package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.service.notify.repository.dao.DlqReplayAuditDao;
import com.github.paicoding.forum.service.notify.repository.entity.DlqReplayAuditDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DlqReplayAuditService {
    private static final int MAX_ERROR_LENGTH = 500;
    private final DlqReplayAuditDao auditDao;

    public void begin(ReplayAuditCommand command) {
        DlqReplayAuditDO audit = new DlqReplayAuditDO();
        audit.setOriginalMsgId(command.originalMsgId());
        audit.setOriginalEventId(command.originalEventId());
        audit.setCorrectionEventId(command.correctionEventId());
        audit.setTopic(command.topic());
        audit.setTag(command.tag());
        audit.setBusinessKey(command.businessKey());
        audit.setReason(command.reason());
        audit.setOperatorId(command.operatorId());
        audit.setStatus("CREATED");
        if (!auditDao.save(audit)) {
            throw new IllegalStateException("DLQ replay audit creation failed");
        }
    }

    public void markSubmitted(String correctionEventId) {
        updateStatus(correctionEventId, "SUBMITTED", null);
    }

    public void markFailed(String correctionEventId, Throwable failure) {
        String message = failure == null ? "unknown failure" : failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure == null ? "unknown failure" : failure.getClass().getSimpleName();
        }
        updateStatus(correctionEventId, "FAILED",
                message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH));
    }

    private void updateStatus(String correctionEventId, String status, String errorSummary) {
        boolean updated = auditDao.lambdaUpdate()
                .eq(DlqReplayAuditDO::getCorrectionEventId, correctionEventId)
                .set(DlqReplayAuditDO::getStatus, status)
                .set(DlqReplayAuditDO::getErrorSummary, errorSummary)
                .update();
        if (!updated) {
            throw new IllegalStateException("DLQ replay audit not found, correctionEventId=" + correctionEventId);
        }
    }

    public record ReplayAuditCommand(String originalMsgId, String originalEventId,
                                     String correctionEventId, String topic, String tag,
                                     String businessKey, String reason, Long operatorId) {
    }
}
