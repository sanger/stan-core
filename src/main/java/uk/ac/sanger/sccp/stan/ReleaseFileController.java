package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.stan.service.releasefile.*;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.List;

/**
 * Controller for delivering release files (tsv).
 * @author dr6
 */
@Controller
public class ReleaseFileController {
    private final ReleaseFileService releaseFileService;

    @Autowired
    public ReleaseFileController(ReleaseFileService releaseFileService) {
        this.releaseFileService = releaseFileService;
    }

    @RequestMapping(value="/release", method = RequestMethod.GET, produces = "text/tsv")
    @ResponseBody
    public TsvFile<ReleaseEntry> getReleaseFile(@RequestParam(name="id") List<Integer> ids) {
        ReleaseFileContent rfc = releaseFileService.getReleaseFileContent(ids);
        return new TsvFile<>("releases.tsv", rfc.getEntries(), ReleaseColumn.forMode(rfc.getMode()));
    }
}
