package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecord;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecordNote;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopySource;
import uk.ac.sanger.sccp.stan.request.SlotCopySave;
import uk.ac.sanger.sccp.utils.Zip;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class SlotCopyRecordServiceImp implements SlotCopyRecordService {
    public static final String
            NOTE_BARCODE = "barcode",
            NOTE_LWTYPE = "labware type",
            NOTE_PREBARCODE = "prebarcode",
            NOTE_BIOSTATE = "bio state",
            NOTE_COSTING = "costing",
            NOTE_LOT = "lot",
            NOTE_PROBELOT = "probe lot",
            NOTE_REAGENT_A_LOT = "reagent a lot",
            NOTE_REAGENT_B_LOT = "reagent b lot",
            NOTE_EXECUTION = "execution",
            NOTE_CON_SRCBC = "con source barcode",
            NOTE_CON_SRCADDRESS = "con source address",
            NOTE_CON_DESTADDRESS = "con dest address",
            NOTE_SRC_BARCODE = "source barcode",
            NOTE_SRC_STATE = "source state";

    private final SlotCopyRecordRepo recordRepo;
    private final OperationTypeRepo opTypeRepo;
    private final WorkRepo workRepo;
    private final EntityManager entityManager;

    @Autowired
    public SlotCopyRecordServiceImp(SlotCopyRecordRepo recordRepo, OperationTypeRepo opTypeRepo, WorkRepo workRepo, EntityManager entityManager) {
        this.recordRepo = recordRepo;
        this.opTypeRepo = opTypeRepo;
        this.workRepo = workRepo;
        this.entityManager = entityManager;
    }

    @Override
    public SlotCopyRecord save(SlotCopySave request) throws ValidationException {
        Set<String> problems = new LinkedHashSet<>();
        checkRequiredFields(problems, request);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        Work work = workRepo.getByWorkNumber(request.getWorkNumber());
        OperationType opType = opTypeRepo.getByName(request.getOperationType());
        Optional<SlotCopyRecord> oldRecordOpt = recordRepo.findByOperationTypeAndWorkAndLpNumber(opType, work, request.getLpNumber());
        if (oldRecordOpt.isPresent()) {
            recordRepo.delete(oldRecordOpt.get());
            entityManager.flush();
        }

        SlotCopyRecord record = new SlotCopyRecord();
        record.setOperationType(opType);
        record.setWork(work);
        record.setLpNumber(request.getLpNumber());
        record.setNotes(createNotes(request));

        return recordRepo.save(record);
    }

    @Override
    public SlotCopySave load(String opname, String workNumber, String lpNumber) throws EntityNotFoundException {
        OperationType opType = opTypeRepo.getByName(opname);
        Work work = workRepo.getByWorkNumber(workNumber);
        lpNumber = trimAndRequire(lpNumber, "LP number not supplied.");
        SlotCopyRecord record = recordRepo.findByOperationTypeAndWorkAndLpNumber(opType, work, lpNumber).orElse(null);
        if (record==null) {
            throw new EntityNotFoundException("No such record found.");
        }
        return reassembleSave(record);
    }

    /** Constructs slot copy save data from a slot copy record */
    SlotCopySave reassembleSave(SlotCopyRecord record) {
        Map<String, List<String>> noteMap = loadNoteMap(record.getNotes());
        SlotCopySave save = new SlotCopySave();
        save.setLpNumber(record.getLpNumber());
        save.setWorkNumber(record.getWork().getWorkNumber());
        save.setOperationType(record.getOperationType().getName());
        save.setBarcode(singleNoteValue(noteMap, NOTE_BARCODE));
        save.setLabwareType(singleNoteValue(noteMap, NOTE_LWTYPE));
        save.setPreBarcode(singleNoteValue(noteMap, NOTE_PREBARCODE));
        save.setBioState(singleNoteValue(noteMap, NOTE_BIOSTATE));
        save.setCosting(nullableValueOf(singleNoteValue(noteMap, NOTE_COSTING), SlideCosting::valueOf));
        save.setLotNumber(singleNoteValue(noteMap, NOTE_LOT));
        save.setProbeLotNumber(singleNoteValue(noteMap, NOTE_PROBELOT));
        save.setReagentALot(singleNoteValue(noteMap, NOTE_REAGENT_A_LOT));
        save.setReagentBLot(singleNoteValue(noteMap, NOTE_REAGENT_B_LOT));
        save.setExecutionType(nullableValueOf(singleNoteValue(noteMap, NOTE_EXECUTION), ExecutionType::valueOf));
        List<String> sourceBarcodes = noteMap.get(NOTE_SRC_BARCODE);
        List<String> sourceStates = noteMap.get(NOTE_SRC_STATE);
        if (!nullOrEmpty(sourceBarcodes) && !nullOrEmpty(sourceStates)) {
            save.setSources(Zip.of(sourceBarcodes.stream(), sourceStates.stream())
                    .map((bc, state) -> new SlotCopySource(bc, nullableValueOf(state, Labware.State::valueOf))
            ).toList());
        }
        List<String> contentSourceBarcodes = noteMap.get(NOTE_CON_SRCBC);
        List<String> contentSourceAddress = noteMap.get(NOTE_CON_SRCADDRESS);
        List<String> contentDestAddress = noteMap.get(NOTE_CON_DESTADDRESS);
        if (!nullOrEmpty(contentSourceBarcodes)) {
            save.setContents(IntStream.range(0, contentSourceBarcodes.size()).mapToObj(
                    i -> new SlotCopyContent(contentSourceBarcodes.get(i),
                            nullableValueOf(contentSourceAddress.get(i), Address::valueOf),
                            nullableValueOf(contentDestAddress.get(i), Address::valueOf))
            ).toList());
        }
        return save;
    }

    /**
     * Returns null of the given string is null or empty; otherwise uses the given function to convert it.
     * @param string string value
     * @param function function to convert the string
     * @return the value converted into; or null if the string is null or empty
     * @param <E> the type of value the string is converted into
     */
    static <E> E nullableValueOf(String string, Function<String, E> function) {
        if (nullOrEmpty(string)) {
            return null;
        }
        return function.apply(string);
    }

    /**
     * Trims the given string; adds a problem if it is null or empty
     * @param problems receptacle for problems
     * @param name the name of the field
     * @param value the value of the field
     * @return the trimmed value of the string; null if the string is empty
     */
    String trimAndCheck(Collection<String> problems, String name, String value) {
        value = trimToNull(value);
        if (value==null) {
            problems.add("Missing "+name+".");
        }
        return value;
    }

    /**
     * Checks required fields are present
     * @param problems receptacle for problems
     * @param request request to validate
     */
    void checkRequiredFields(Collection<String> problems, SlotCopySave request) {
        request.setOperationType(trimAndCheck(problems, "operation type", request.getOperationType()));
        request.setWorkNumber(trimAndCheck(problems, "work number", request.getWorkNumber()));
        request.setLpNumber(trimAndCheck(problems, "LP number", request.getLpNumber()));
    }

    /**
     * Adds a note to the given list of notes, if the given value is non-null and nonempty
     * @param notes list to add note
     * @param name name of note
     * @param value value of note
     */
    void mayAddNote(List<SlotCopyRecordNote> notes, String name, String value) {
        mayAddNote(notes, name, 0, value);
    }

    /**
     * Adds a note to the given list of notes, if the given value is non-null and nonempty
     * @param notes list to add note
     * @param name name of note
     * @param index index of note to add
     * @param value value of note
     */
    void mayAddNote(List<SlotCopyRecordNote> notes, String name, int index, String value) {
        value = trimToNull(value);
        if (value != null) {
            notes.add(new SlotCopyRecordNote(name, index, value));
        }
    }

    /**
     * Compiles a list of notes for the details of the request
     * @param request the request to describe
     * @return a list of notes describing details of the request
     */
    List<SlotCopyRecordNote> createNotes(SlotCopySave request) {
        List<SlotCopyRecordNote> notes = new ArrayList<>();
        mayAddNote(notes, NOTE_BARCODE, request.getBarcode());
        mayAddNote(notes, NOTE_LWTYPE, request.getLabwareType());
        mayAddNote(notes, NOTE_PREBARCODE, request.getPreBarcode());
        mayAddNote(notes, NOTE_BIOSTATE, request.getBioState());
        mayAddNote(notes, NOTE_LOT, request.getLotNumber());
        mayAddNote(notes, NOTE_PROBELOT, request.getProbeLotNumber());
        mayAddNote(notes, NOTE_REAGENT_A_LOT, request.getReagentALot());
        mayAddNote(notes, NOTE_REAGENT_B_LOT, request.getReagentBLot());
        if (request.getExecutionType()!=null) {
            notes.add(new SlotCopyRecordNote(NOTE_EXECUTION, request.getExecutionType().name()));
        }
        if (request.getCosting()!=null) {
            notes.add(new SlotCopyRecordNote(NOTE_COSTING, request.getCosting().name()));
        }

        for (int i = 0; i < request.getSources().size(); ++i) {
            SlotCopySource source = request.getSources().get(i);
            mayAddNote(notes, NOTE_SRC_BARCODE, i, source.getBarcode());
            if (source.getLabwareState()!=null) {
                notes.add(new SlotCopyRecordNote(NOTE_SRC_STATE, i, source.getLabwareState().name()));
            }
        }
        for (int i = 0; i < request.getContents().size(); ++i) {
            SlotCopyContent content = request.getContents().get(i);
            mayAddNote(notes, NOTE_CON_SRCBC, i, content.getSourceBarcode());
            if (content.getSourceAddress()!=null) {
                notes.add(new SlotCopyRecordNote(NOTE_CON_SRCADDRESS, i, content.getSourceAddress().toString()));
            }
            if (content.getDestinationAddress()!=null) {
                notes.add(new SlotCopyRecordNote(NOTE_CON_DESTADDRESS, i, content.getDestinationAddress().toString()));
            }
        }
        return notes;
    }

    /**
     * Loads given note values into a map
     * @param notes notes to load
     * @return a map of note name to list of values
     */
    Map<String, List<String>> loadNoteMap(Collection<SlotCopyRecordNote> notes) {
        Map<String, List<String>> map = new HashMap<>();
        notes = notes.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (SlotCopyRecordNote note : notes) {
            List<String> list = map.computeIfAbsent(note.getName(), k -> new ArrayList<>());
            while (list.size() <= note.getValueIndex()) {
                list.add(null);
            }
            list.set(note.getValueIndex(), note.getValue());
        }
        return map;
    }

    /**
     * Gets the single named value from the given map of notes
     * @param noteMap map of names to note values
     * @param key name of the note
     * @return the value of the note; or null of it is missing or empty
     */
    String singleNoteValue(Map<String, List<String>> noteMap, String key) {
        List<String> values = noteMap.get(key);
        if (nullOrEmpty(values)) {
            return null;
        }
        return values.getFirst();
    }
}
