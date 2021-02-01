package uk.ac.sanger.sccp.stan;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.List;

/**
 * Controller for delivering release files (tsv).
 * @author dr6
 */
@Controller
public class ReleaseFileController {

    @GetMapping("/release1")
    @ResponseBody
    public String getReleaseData(@RequestParam(name="id") List<Integer> ids) {
        return "RELEASE DATA: "+ids;
    }

    @RequestMapping(value="/release", method = RequestMethod.GET, produces = "text/tsv")
    @ResponseBody
    public TsvFile<Integer> getTsv(@RequestParam(name="id") List<Integer> ids) {
        return new TsvFile<Integer>("release.tsv", ids, List.of(
                new TsvColumn<Integer>() {

                    @Override
                    public String get(Integer entry) {
                        return entry.toString();
                    }

                    @Override
                    public String toString() {
                        return "id";
                    }
                },
                new TsvColumn<Integer>() {
                    @Override
                    public String get(Integer entry) {
                        return "banana "+entry;
                    }

                    @Override
                    public String toString() {
                        return "Bananas";
                    }
                }
        ));
    }
}
