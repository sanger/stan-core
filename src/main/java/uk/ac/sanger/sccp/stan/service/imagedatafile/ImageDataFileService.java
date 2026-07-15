package uk.ac.sanger.sccp.stan.service.imagedatafile;

import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.Collection;

/** Service for generating file data for image qc ops */
public interface ImageDataFileService {
    /**
     * Generate file data containing the image data for the given operations.
     * @param ops the operations
     * @return the file data
     */
    TsvFile<?> generateFile(Collection<Operation> ops);
}
