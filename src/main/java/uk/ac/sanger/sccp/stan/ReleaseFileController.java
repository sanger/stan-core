package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.stan.model.ReleaseFileOption;
import uk.ac.sanger.sccp.stan.service.releasefile.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

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
    public TsvFile<ReleaseEntry> getReleaseFile(@RequestParam(name="id") List<Integer> ids,
                                                @RequestParam(name="groups", required = false) List<String> groupNames) {
        ReleaseFileContent rfc = releaseFileService.getReleaseFileContent(ids, parseOptions(groupNames));
        List<? extends TsvColumn<ReleaseEntry>> columns = releaseFileService.computeColumns(rfc);
        return new TsvFile<>("releases.tsv", rfc.getEntries(), columns);
    }

    /**
     * Converts a list of strings (from query parameters) to ReleaseFileOptions.
     * @param groupNames some parameter-names of release file options.
     * @return a set of the corresponding options
     */
    private static Set<ReleaseFileOption> parseOptions(List<String> groupNames) {
        if (nullOrEmpty(groupNames)) {
            return Set.of();
        }
        EnumSet<ReleaseFileOption> options = EnumSet.noneOf(ReleaseFileOption.class);
        for (String groupName : groupNames) {
            options.add(ReleaseFileOption.forParameterName(groupName));
        }
        return options;
    }
}
