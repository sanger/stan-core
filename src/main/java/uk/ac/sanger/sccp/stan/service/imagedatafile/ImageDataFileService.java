package uk.ac.sanger.sccp.stan.service.imagedatafile;

import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

/** Service for generating file data for image qc ops */
public interface ImageDataFileService {
    /**
     * Generate file data containing the image data for the given operation.
     * @param op the operation
     * @return the file data
     */
    TsvFile<?> generateFile(Operation op);
}
