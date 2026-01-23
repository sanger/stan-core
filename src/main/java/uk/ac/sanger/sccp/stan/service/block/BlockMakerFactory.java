package uk.ac.sanger.sccp.stan.service.block;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.List;


/**
 * Component for creating block makers.
 * @author dr6
 */
@Component
public class BlockMakerFactory {
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opcomRepo;
    private final LabwareService lwService;
    private final OperationService opService;
    private final WorkService workService;
    private final BioRiskService bioRiskService;

    @Autowired
    public BlockMakerFactory(TissueRepo tissueRepo, SampleRepo sampleRepo, SlotRepo slotRepo, LabwareRepo lwRepo,
                             OperationCommentRepo opcomRepo, LabwareService lwService, OperationService opService,
                             WorkService workService, BioRiskService bioRiskService) {
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.lwRepo = lwRepo;
        this.opcomRepo = opcomRepo;
        this.lwService = lwService;
        this.opService = opService;
        this.workService = workService;
        this.bioRiskService = bioRiskService;
    }

    /** Creates a block maker for the given request with the given data. */
    public BlockMaker createBlockMaker(TissueBlockRequest request, List<BlockLabwareData> lwData,
                                       Medium medium, BioState bioState, Work work, OperationType opType, User user) {
        return new BlockMakerImp(tissueRepo, sampleRepo, slotRepo, lwRepo, opcomRepo,
                lwService, opService, workService, bioRiskService,
                request, lwData, medium, bioState, work, opType, user);
    }
}
