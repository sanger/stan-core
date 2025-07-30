package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareNoteRepo;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.toLinkedHashSet;

/**
 * @author dr6
 */
@Service
public class LabwareNoteServiceImp implements LabwareNoteService {
    private final LabwareRepo lwRepo;
    private final LabwareNoteRepo lwNoteRepo;

    @Autowired
    public LabwareNoteServiceImp(LabwareRepo lwRepo, LabwareNoteRepo lwNoteRepo) {
        this.lwRepo = lwRepo;
        this.lwNoteRepo = lwNoteRepo;
    }

    @Override
    public Set<String> findNoteValuesForBarcode(String barcode, String name) throws EntityNotFoundException {
        Labware lw = lwRepo.getByBarcode(barcode);
        return findNoteValuesForLabware(lw, name);
    }

    @Override
    public List<LabwareNote> findNamedNotesForLabwareAndOperationType(String name, Labware lw, OperationType opType) {
        return lwNoteRepo.findAllByNameAndLabwareIdInAndOperationType(name, List.of(lw.getId()), opType);
    }

    @Override
    public UCMap<Set<String>> findNoteValuesForLabware(Collection<Labware> labware, String name) {
        if (labware.isEmpty()) {
            return new UCMap<>(0);
        }
        Map<Integer, Labware> idToLw = labware.stream().distinct().collect(inMap(Labware::getId));
        UCMap<Set<String>> bcNotes = new UCMap<>(labware.size());
        lwNoteRepo.findAllByLabwareIdInAndName(idToLw.keySet(), name).stream()
                .distinct()
                .forEach(note -> bcNotes.computeIfAbsent(
                        idToLw.get(note.getLabwareId()).getBarcode(), k -> new HashSet<>()
                ).add(note.getValue()));
        return bcNotes;
    }

    @Override
    public Set<String> findNoteValuesForLabware(Labware lw, String name) {
        List<LabwareNote> notes = lwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), name);
        return notes.stream()
                .map(LabwareNote::getValue)
                .collect(toLinkedHashSet());
    }

    @Override
    public Iterable<LabwareNote> createNotes(String name, UCMap<Labware> lwMap, UCMap<Operation> opMap, UCMap<String> values) {
        List<LabwareNote> notes = new ArrayList<>(values.size());
        for (var entry : values.entrySet()) {
            if (entry.getValue()==null) {
                continue;
            }
            String bc = entry.getKey();
            Labware lw = lwMap.get(bc);
            Operation op = opMap.get(bc);
            LabwareNote note = new LabwareNote(null, lw.getId(), op.getId(), name, entry.getValue());
            notes.add(note);
        }
        if (notes.isEmpty()) {
            return List.of();
        }
        return lwNoteRepo.saveAll(notes);
    }

    @Override
    public LabwareNote createNote(String name, Labware lw, Operation op, String value) {
        return lwNoteRepo.save(new LabwareNote(null, lw.getId(), op.getId(), name, value));
    }
}
