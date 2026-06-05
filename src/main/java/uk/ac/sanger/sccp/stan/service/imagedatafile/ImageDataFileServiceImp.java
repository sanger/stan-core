package uk.ac.sanger.sccp.stan.service.imagedatafile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;
import uk.ac.sanger.sccp.utils.tsv.TsvFile;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toMap;
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
    public TsvFile<?> generateFile(Collection<Operation> ops) {
        String filename = getFilename(ops);
        List<? extends TsvColumn<ImageDataRow>> columns = getColumns();
        List<ImageDataRow> rows = getRows(ops);
        return new TsvFile<>(filename, rows, columns);
    }

    /** An op id and a sample id */
    record OpIdSampleId(int opId, int sampleId) {
        OpIdSampleId(OperationComment opcom) {
            this(opcom.getOperationId(), opcom.getSampleId());
        }
    }

    /** Filename for the given operations */
    public String getFilename(Collection<Operation> ops) {
        String idsJoined = ops.stream().map(Operation::getId).map(Object::toString).collect(joining("-"));
        return "imaging-qc-" + idsJoined + ".xlsx";
    }

    /** The columns in the file */
    public List<ImageDataColumn> getColumns() {
        return Arrays.asList(ImageDataColumn.values());
    }

    /** Gets comments from the specified ops, mapped from op id and sample id */
    Map<OpIdSampleId, List<Comment>> compileCommentMap(Collection<Integer> opIds) {
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(opIds);
        if (opcoms.isEmpty()) {
            return Map.of();
        }
        Map<OpIdSampleId, List<Comment>> map = new HashMap<>();
        for (OperationComment opcom : opcoms) {
            map.computeIfAbsent(new OpIdSampleId(opcom), k -> new ArrayList<>())
                    .add(opcom.getComment());
        }
        map.replaceAll((k, v) -> v.stream().sorted(Comparator.comparingInt(Comment::getId)).distinct().toList());
        return map;
    }

    /** Gets the labware for the operations, mapped from their ids */
    public Map<Integer, Labware> compileLabwareMap(Collection<Operation> ops) {
        Set<Integer> labwareIds = ops.stream()
                .flatMap(op -> op.getActions().stream())
                .map(ac -> ac.getDestination().getLabwareId())
                .collect(toSet());
        return lwRepo.findAllByIdIn(labwareIds).stream()
                .collect(inMap(Labware::getId));
    }

    /** Loads the works linked to each operation id */
    public Map<Integer, List<Work>> loadOpWorks(List<Integer> opIds) {
        Map<Integer, List<Integer>> opWorkIds = opIds.stream()
                .collect(toMap(Function.identity(), workRepo::findWorkIdsForOperationId));
        Set<Integer> workIds = opWorkIds.values().stream().flatMap(List::stream).collect(toSet());
        Map<Integer, Work> workIdMap = stream(workRepo.findAllById(workIds)).collect(inMap(Work::getId));
        return opWorkIds.entrySet().stream().collect(toMap(Map.Entry::getKey,
                e -> e.getValue().stream().map(workIdMap::get).toList()));
    }

    /** Gets a string describing the omero project for the given works */
    public String getOmeroString(Collection<Work> works) {
        return works.stream()
                .map(Work::getOmeroProject)
                .filter(Objects::nonNull)
                .distinct()
                .map(OmeroProject::getName)
                .collect(joining(", "));
    }

    /** Gets a string of work numbers from the given works */
    public String getWorkString(Collection<Work> works) {
        return works.stream()
                .map(Work::getWorkNumber)
                .collect(joining(", "));
    }

    /** Gets data rows for all the given operations */
    public List<ImageDataRow> getRows(Collection<Operation> ops) {
        List<Integer> opIds = ops.stream().map(Operation::getId).toList();
        Map<Integer, List<Work>> opWorks = loadOpWorks(opIds);
        Map<Integer, String> opWorkNumber = opWorks.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> getWorkString(opWorks.get(e.getKey()))));
        Map<Integer, String> opOmero = opWorks.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> getOmeroString(opWorks.get(e.getKey()))));
        Map<OpIdSampleId, List<Comment>> opSampleComments = compileCommentMap(opIds);
        Map<Integer, Labware> lwMap = compileLabwareMap(ops);
        return compileAllRows(ops, opOmero, opWorkNumber, lwMap, opSampleComments);
    }

    /** Puts together rows given various data. One row per sample */
    List<ImageDataRow> compileRows(Operation op, String omeroProject, String workNumber, String user,
                                   Map<Integer, Labware> lwMap, Map<OpIdSampleId, List<Comment>> sampleComments) {
        return op.getActions().stream()
                .filter(distinctBySerial(ac -> ac.getSample().getId()))
                .map(ac -> makeRow(ac, omeroProject, workNumber, user, lwMap, sampleComments))
                .distinct()
                .toList();
    }

    /** Compiles rows from multiple operations and various compiled data. One row per sample, per op. */
    List<ImageDataRow> compileAllRows(Collection<Operation> ops, Map<Integer, String> opOmero,
                                          Map<Integer, String> opWorkNumber, Map<Integer, Labware> lwMap,
                                          Map<OpIdSampleId, List<Comment>> opSampleComments) {
        return ops.stream()
                .flatMap(op -> compileRows(op, opOmero.get(op.getId()), opWorkNumber.get(op.getId()),
                        op.getUser().getUsername(), lwMap, opSampleComments).stream())
                .toList();
    }

    /** Converts an action to a row */
    ImageDataRow makeRow(Action ac, String omeroProject, String workNumber, String user,
                                Map<Integer, Labware> lwMap, Map<OpIdSampleId, List<Comment>> slotSampleComments) {
        Slot slot = ac.getDestination();
        Sample sample = ac.getSample();
        Labware lw = lwMap.get(slot.getLabwareId());
        List<Comment> comments = slotSampleComments.get(new OpIdSampleId(ac.getOperationId(), sample.getId()));
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
