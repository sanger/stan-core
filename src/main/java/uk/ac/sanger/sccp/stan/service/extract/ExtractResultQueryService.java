package uk.ac.sanger.sccp.stan.service.extract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractResult;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Service to get extract result for a given labware barcode
 * @author dr6
 */
@Service
public class ExtractResultQueryService {
    private static final String
            RESULT_OP_NAME = "Record result",
            EXTRACT_OP_NAME = "Extract",
            CONCENTRATION_NAME = "Concentration";

    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final ResultOpRepo resultOpRepo;
    private final MeasurementRepo measurementRepo;

    @Autowired
    public ExtractResultQueryService(LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                                     ResultOpRepo resultOpRepo, MeasurementRepo measurementRepo) {
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.resultOpRepo = resultOpRepo;
        this.measurementRepo = measurementRepo;
    }

    /**
     * Gets the information about the extract result recorded on the specified labware.
     * If the labware barcode is unknown, an error will be thrown.
     * If the labware exists but has no extract result recorded on it, the relevant fields in the
     * result will be null.
     * @param barcode the barcode of a piece of labware
     * @return the labware, pass/fail result and concentration for the result (if found)
     * @exception EntityNotFoundException if the labware barcode is unknown
     */
    public ExtractResult getExtractResult(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        ResultOp resultOp = selectExtractResult(lw);
        if (resultOp==null) {
            return new ExtractResult(lw, null, null);
        }
        return new ExtractResult(lw, resultOp.getResult(), getConcentration(resultOp.getOperationId(), lw));
    }

    /**
     * For a given item of labware, does the following:<ul>
     *     <li>Find the Record Result ops recorded on that labware;</li>
     *     <li>Find the ResultOps for those operations that refer to the indicated labware;</li>
     *     <li>Find the prior operations indicated by those ResultOps;</li>
     *     <li>Filter out the ResultOps whose prior op was not an extract op;</li>
     *     <li>Identify the latest operation in our original list whose ResultOp has not been filtered out;</li>
     *     <li>Return the ResultOp for that specific operation.</li>
     *  </ul>
     *  May return null.
     * @param lw the labware
     * @return the result op found, or null if none was found
     */
    public ResultOp selectExtractResult(Labware lw) {
        OperationType opType = opTypeRepo.getByName(RESULT_OP_NAME);
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        if (ops.isEmpty()) {
            return null;
        }
        Set<Integer> slotIdSet = lw.getSlots().stream().map(Slot::getId).collect(toSet());

        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        List<ResultOp> resultOps = resultOpRepo.findAllByOperationIdIn(opIds).stream()
                .filter(ro -> slotIdSet.contains(ro.getSlotId()))
                .collect(toList());
        if (resultOps.isEmpty()) {
            return null;
        }

        Map<Integer, Operation> referredToOps = Streamable.of(
                        opRepo.findAllById(resultOps.stream().map(ResultOp::getRefersToOpId).collect(toSet()))
                ).stream()
                .filter(op -> op.getOperationType().getName().equalsIgnoreCase(EXTRACT_OP_NAME))
                .collect(BasicUtils.toMap(Operation::getId));

        if (referredToOps.isEmpty()) {
            return null;
        }

        Map<Integer, ResultOp> roMap = resultOps.stream()
                .filter(ro -> referredToOps.get(ro.getRefersToOpId()) !=null)
                .collect(BasicUtils.toMap(ResultOp::getOperationId));

        return ops.stream()
                .filter(op -> roMap.get(op.getId()) !=null)
                .max(Comparator.comparing(Operation::getPerformed))
                .map(op -> roMap.get(op.getId()))
                .orElse(null);
    }

    /**
     * Gets the value from the concentration measurement
     * @param opId the id of the operation for the measurements
     * @param lw the labware on which the measurement was recorded
     * @return the value of the concentration measurement, if one was found; null if not
     */
    public String getConcentration(Integer opId, Labware lw) {
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        Set<Integer> slotIdSet = lw.getSlots().stream().map(Slot::getId).collect(toSet());
        return measurements.stream()
                .filter(measurement -> measurement.getName().equalsIgnoreCase(CONCENTRATION_NAME)
                        && slotIdSet.contains(measurement.getSlotId()))
                .findAny()
                .map(Measurement::getValue)
                .orElse(null);
    }
}
