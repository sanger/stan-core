package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.service.WorkProgressService;
import uk.ac.sanger.sccp.stan.service.work.WorkProgressColumn;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.Arrays;
import java.util.List;

/**
 * Controller for delivering work progress files (tsv).
 * @author dr6
 */
@Controller
public class WorkProgressFileController {
    private final WorkProgressService workProgressService;

    @Autowired
    public WorkProgressFileController(WorkProgressService workProgressService) {
        this.workProgressService = workProgressService;
    }

    @RequestMapping(value="/work-progress", method=RequestMethod.GET, produces="text/tsv")
    @ResponseBody
    public TsvFile<?> getWorkProgressFile(
            @RequestParam(name="workNumber", required=false) String workNumber,
            @RequestParam(name="workTypes", required=false) List<String> workTypes,
            @RequestParam(name="statuses", required=false) List<Work.Status> statuses) {
        List<WorkProgress> progress = workProgressService.getProgress(workNumber, workTypes, statuses);
        List<WorkProgressColumn> columns = Arrays.asList(WorkProgressColumn.values());
        return new TsvFile<>("work_progress.tsv", progress, columns);
    }
}
