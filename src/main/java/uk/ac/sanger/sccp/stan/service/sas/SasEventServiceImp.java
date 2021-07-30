package uk.ac.sanger.sccp.stan.service.sas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.stan.repo.SasEventRepo;

import java.util.Objects;

/**
 * Service for dealing with {@link SasEvent SAS events}
 * @author dr6
 */
@Service
public class SasEventServiceImp implements SasEventService {
    private final SasEventRepo sasEventRepo;
    private final CommentRepo commentRepo;

    @Autowired
    public SasEventServiceImp(SasEventRepo sasEventRepo, CommentRepo commentRepo) {
        this.sasEventRepo = sasEventRepo;
        this.commentRepo = commentRepo;
    }

    @Override
    public SasEvent recordEvent(User user, SasNumber sasNumber, SasEvent.Type type, Comment comment) {
        SasEvent event = new SasEvent(sasNumber, type, user, comment);
        return sasEventRepo.save(event);
    }

    /**
     * Gets the event type for putting a sas number in the given status
     * @param newStatus the new status
     * @return the event type that represents putting the sas number in the given status
     */
    public SasEvent.Type findEventType(Status newStatus) {
        if (newStatus != null) {
            switch (newStatus) {
                case active: return SasEvent.Type.resume;
                case paused: return SasEvent.Type.pause;
                case failed: return SasEvent.Type.fail;
                case completed: return SasEvent.Type.complete;
            }
        }
        throw new IllegalArgumentException("Cannot determine event type for status "+newStatus);
    }

    @Override
    public SasEvent recordStatusChange(User user, SasNumber sas, Status newStatus, Integer commentId) {
        Objects.requireNonNull(newStatus, "Cannot set SAS number status to null.");
        Status oldStatus = sas.getStatus();
        if (oldStatus == newStatus) {
            throw new IllegalArgumentException(String.format("SAS number %s status is already %s.", sas.getSasNumber(), newStatus));
        }
        if (sas.isClosed()) {
            throw new IllegalArgumentException(String.format("Cannot alter status of %s SAS number: %s", oldStatus, sas.getSasNumber()));
        }
        SasEvent.Type eventType = findEventType(newStatus);
        boolean needReason = (eventType==SasEvent.Type.pause || eventType==SasEvent.Type.fail);
        if (needReason && commentId==null) {
            throw new IllegalArgumentException("A reason is required to "+eventType+" an SAS number.");
        }
        if (!needReason && commentId!=null) {
            throw new IllegalArgumentException("A reason is not required to "+eventType+" an SAS number.");
        }
        Comment comment = (commentId==null ? null : commentRepo.getById(commentId));
        return recordEvent(user, sas, eventType, comment);
    }
}
