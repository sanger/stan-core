package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.imagedatafile.ImageDataFileService;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import javax.persistence.EntityNotFoundException;
import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.asList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Controller for delivering image data files (excel).
 * @author dr6
 */
@Controller
public class ImageDataFileController {
    public static final String IMAGING_QC_OP_NAME = "Imaging QC";

    private final OperationRepo opRepo;
    private final ImageDataFileService imageDataFileService;

    @Autowired
    public ImageDataFileController(OperationRepo opRepo, ImageDataFileService imageDataFileService) {
        this.opRepo = opRepo;
        this.imageDataFileService = imageDataFileService;
    }

    @RequestMapping(value="/imageqc", method=RequestMethod.GET,
                    produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @ResponseBody
    public TsvFile<?> getImageDataFile(@RequestParam(name="id") List<Integer> ids) {
        if (nullOrEmpty(ids)) {
            throw new IllegalArgumentException("No operation IDs provided.");
        }
        List<Operation> ops = asList(opRepo.findAllById(ids));
        if (ops.isEmpty()) {
            throw new EntityNotFoundException("No operations found with IDs " + ids);
        }
        List<Integer> wrongOpTypeIds = ops.stream()
                .filter(op -> !op.getOperationType().getName().equalsIgnoreCase(IMAGING_QC_OP_NAME))
                .map(Operation::getId)
                .toList();
        if (!wrongOpTypeIds.isEmpty()) {
            throw new IllegalArgumentException("Not an imaging QC operation: "+wrongOpTypeIds);
        }
        return imageDataFileService.generateFile(ops);
        // TsvFileConverter will convert the data to an excel file
    }
}
