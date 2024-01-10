package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Service for validating a {@link SlotCopyRequest} (e.g. Transfer op)
 */
public interface SlotCopyValidationService {
    /**
     * Data built up and tracked during verification
     */
    class Data {
        SlotCopyRequest request;
        final Collection<String> problems = new LinkedHashSet<>();
        OperationType opType;
        Work work;
        UCMap<Labware> sourceLabware;
        UCMap<Labware> destLabware;
        UCMap<BioState> bioStates;
        UCMap<LabwareType> lwTypes;

        public Data(SlotCopyRequest request) {
            this.request = request;
        }

        public void addProblem(String problem) {
            this.problems.add(problem);
        }
    }

    /**
     * Validates the given input. Records loaded information and problems found inside the parameter object.
     * @param user the user responsible for the request
     * @param data the validation data object
     */
    void validate(User user, Data data);

    /**
     * Creates a data object; validates it using {@link #validate}, and returns it.
     * Loaded information and problems are recorded inside the data object.
     * @param user the user responsible for the request
     * @param request the request object
     * @return the validation data object
     */
    default Data validateRequest(User user, SlotCopyRequest request) {
        Data data = new Data(request);
        validate(user, data);
        return data;
    }
}
