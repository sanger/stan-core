package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;

/**
 * Service helping with {@link uk.ac.sanger.sccp.stan.model.LabwareNote labware notes}
 */
public interface LabwareNoteService {
    /**
     * Gets the distinct values of notes for a specific labware and note name
     * @param barcode the barcode of the labware
     * @param name the name of the note
     * @return the distinct values of matching notes
     * @exception EntityNotFoundException no such labware is found
     */
    Set<String> findNoteValuesForBarcode(String barcode, String name) throws EntityNotFoundException;

    /**
     * Gets the values of notes for multiple labware and one note name
     * @param labware the labware
     * @param name the name of the note
     * @return a map from the barcodes to the note values
     */
    UCMap<Set<String>> findNoteValuesForLabware(Collection<Labware> labware, String name);

    /**
     * Records specified notes
     * @param name the name of the notes
     * @param lwMap map to look up labware by barcode
     * @param opMap map to look up operation by labware barcode
     * @param values of labware barcode to note value
     * @return the created notes
     */
    Iterable<LabwareNote> createNotes(String name, UCMap<Labware> lwMap, UCMap<Operation> opMap, UCMap<String> values);
}
