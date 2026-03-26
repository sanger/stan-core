package uk.ac.sanger.sccp.stan.service.imagedatafile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.*;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class ImageDataFileServiceImp implements ImageDataFileService {
    private final WorkRepo workRepo;
    private final OperationCommentRepo opComRepo;
    private final LabwareRepo lwRepo;

    @Autowired
    public ImageDataFileServiceImp(WorkRepo workRepo, OperationCommentRepo opComRepo, LabwareRepo lwRepo) {
        this.workRepo = workRepo;
        this.opComRepo = opComRepo;
        this.lwRepo = lwRepo;
    }

    @Override
    public TsvFile<?> generateFile(Operation op) {
        String filename = getFilename(op);
        List<? extends TsvColumn<ImageDataRow>> columns = getColumns();
        List<ImageDataRow> rows = getRows(op);
        return new TsvFile<>(filename, rows, columns);
    }

    /** Filename for the given operation */
    public String getFilename(Operation op) {
        return "imaging-qc-" + op.getId() + ".xlsx";
    }

    /** The columns in the file */
    public List<ImageDataColumn> getColumns() {
        return Arrays.asList(ImageDataColumn.values());
    }

    /** Gets the comments for the operation, mapped from slot/sample id */
    public Map<Integer, List<Comment>> compileCommentMap(Integer opId) {
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(List.of(opId));
        if (opcoms.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Comment>> map = new HashMap<>();
        for (OperationComment opCom : opcoms) {
            map.computeIfAbsent(opCom.getSampleId(), k -> new ArrayList<>())
                    .add(opCom.getComment());
        }
        map.replaceAll((k, v) -> v.stream().sorted(Comparator.comparingInt(Comment::getId)).distinct().toList());
        return map;
    }

    /** Gets the labware for the operation, mapped from their ids */
    public Map<Integer, Labware> compileLabwareMap(Operation op) {
        Set<Integer> labwareIds = op.getActions().stream()
                .map(ac -> ac.getDestination().getLabwareId())
                .collect(toSet());
        return lwRepo.findAllByIdIn(labwareIds).stream()
                .collect(inMap(Labware::getId));
    }

    /** Gets the rows for the operation */
    public List<ImageDataRow> getRows(Operation op) {
        List<Work> works = workRepo.findWorksForOperationId(op.getId());
        String workNumber = works.stream().map(Work::getWorkNumber).collect(joining(", "));
        String omeroProject = works.stream()
                .map(Work::getOmeroProject)
                .filter(Objects::nonNull)
                .distinct()
                .map(OmeroProject::getName)
                .collect(joining(", "));
        Map<Integer, List<Comment>> sampleComments = compileCommentMap(op.getId());
        Map<Integer, Labware> lwMap = compileLabwareMap(op);
        return compileRows(op, omeroProject, workNumber, op.getUser().getUsername(), lwMap, sampleComments);
    }

    /** Puts together rows given various data. One row per sample */
    public List<ImageDataRow> compileRows(Operation op, String omeroProject, String workNumber, String user,
                                          Map<Integer, Labware> lwMap, Map<Integer, List<Comment>> sampleComments) {
        return op.getActions().stream()
                .filter(distinctBySerial(ac -> ac.getSample().getId()))
                .map(ac -> makeRow(ac, omeroProject, workNumber, user, lwMap, sampleComments))
                .distinct()
                .toList();
    }

    /** Converts an action to a row */
    public ImageDataRow makeRow(Action ac, String omeroProject, String workNumber, String user,
                                Map<Integer, Labware> lwMap, Map<Integer, List<Comment>> slotSampleComments) {
        Slot slot = ac.getDestination();
        Sample sample = ac.getSample();
        Labware lw = lwMap.get(slot.getLabwareId());
        List<Comment> comments = slotSampleComments.get(sample.getId());
        String commentString;
        if (nullOrEmpty(comments)) {
            commentString = null;
        } else {
            commentString = comments.stream()
                    .map(Comment::getText)
                    .map(s -> s.endsWith(".") ? s : (s + "."))
                    .collect(joining(" "));
        }
        return new ImageDataRow(lw.getBarcode(), sample.getTissue().getExternalName(), omeroProject, workNumber, user,
                commentString);
    }

}
