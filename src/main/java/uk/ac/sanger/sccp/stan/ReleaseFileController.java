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
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

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
                                                @RequestParam(name="groups", required=false) List<String> groupNames,
                                                @RequestParam(name="type", required=false) String fileType) {
        final String filename = filenameForType(fileType);
        ReleaseFileContent rfc = releaseFileService.getReleaseFileContent(ids, parseOptions(groupNames));
        List<? extends TsvColumn<ReleaseEntry>> columns = releaseFileService.computeColumns(rfc);
        return new TsvFile<>(filename, rfc.getEntries(), columns);
    }

    protected String filenameForType(String fileType) {
        if (nullOrEmpty(fileType) || fileType.equalsIgnoreCase("tsv")) {
            return "releases.tsv";
        }
        if (fileType.equalsIgnoreCase("xlsx")) {
            return "releases.xlsx";
        }
        throw new IllegalArgumentException("Unsupported file type: " + repr(fileType));
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
            // Handle slightly malformed urls with empty group names
            if (!groupName.isEmpty()) {
                options.add(ReleaseFileOption.forParameterName(groupName));
            }
        }
        return options;
    }
}
