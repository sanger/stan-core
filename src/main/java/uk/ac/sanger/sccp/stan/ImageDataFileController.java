package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.imagedatafile.ImageDataFileService;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import javax.persistence.EntityNotFoundException;

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
    public TsvFile<?> getImageDataFile(@RequestParam(name="id") Integer id) {
        Operation op = opRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("No operation found with id " + id+"."));
        if (!op.getOperationType().getName().equalsIgnoreCase(IMAGING_QC_OP_NAME)) {
            throw new IllegalArgumentException("Operation " + id + " is not an imaging QC operation");
        }
        return imageDataFileService.generateFile(op);
        // TsvFileConverter will convert the data to an excel file
    }
}
