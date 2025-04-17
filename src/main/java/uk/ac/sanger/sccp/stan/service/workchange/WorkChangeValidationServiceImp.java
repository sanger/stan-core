package uk.ac.sanger.sccp.stan.service.workchange;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;
import uk.ac.sanger.sccp.stan.request.OpWorkRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class WorkChangeValidationServiceImp implements WorkChangeValidationService {
    private final WorkRepo workRepo;
    private final OperationRepo opRepo;

    @Autowired
    public WorkChangeValidationServiceImp(WorkRepo workRepo, OperationRepo opRepo) {
        this.workRepo = workRepo;
        this.opRepo = opRepo;
    }

    @Override
    public WorkChangeData validate(OpWorkRequest request) throws ValidationException {
        if (request==null) {
            throw new ValidationException(List.of("No request supplied."));
        }
        Collection<String> problems = new LinkedHashSet<>();
        Work work = loadWork(problems, request.getWorkNumber());
        List<Operation> ops;
        if (nullOrEmpty(request.getOpIds())) {
            problems.add("No operations specified.");
            ops = List.of();
        } else {
            ops = loadOps(problems, work, request.getOpIds());
        }
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return new WorkChangeData(work, ops);
    }

    /** Loads the indicated work. */
    public Work loadWork(Collection<String> problems, String workNumber) {
        if (nullOrEmpty(workNumber)) {
            problems.add("No work number specified.");
            return null;
        }
        Optional<Work> optWork = workRepo.findByWorkNumber(workNumber);
        if (optWork.isEmpty()) {
            problems.add("No work found with work number: " + repr(workNumber));
            return null;
        }
        return optWork.get();
    }

    /**
     * Loads the indicated operations.
     * Excludes ops already listed against the given work.
     * @param problems receptacle for problems
     * @param work the work under consideration (if successfully loaded)
     * @param givenOpIds the given list of operation ids
     * @return the loaded operations
     */
    public List<Operation> loadOps(Collection<String> problems, Work work, List<Integer> givenOpIds) {
        List<Integer> opIds = dedupe(problems, givenOpIds, "operation ID");
        if (work != null) {
            if (!(opIds instanceof ArrayList)) {
                // Make sure we have a mutable list
                opIds = new ArrayList<>(opIds);
            }
            opIds.removeAll(work.getOperationIds());
            if (opIds.isEmpty()) {
                problems.add(String.format("Specified operations are already linked to work %s.", work.getWorkNumber()));
                return List.of();
            }
        }
        Map<Integer, Operation> opMap = stream(opRepo.findAllById(opIds)).collect(inMap(Operation::getId));
        if (opMap.size() < opIds.size()) {
            Set<Integer> missing = new LinkedHashSet<>(opIds);
            missing.remove(null);
            missing.removeAll(opMap.keySet());
            if (!missing.isEmpty()) {
                problems.add(pluralise("Unknown operation ID{s}: ", missing.size())+missing);
            }
        }
        return opIds.stream().map(opMap::get).filter(Objects::nonNull).toList();
    }

    /**
     * Checks for duplicates in the given collection and returns a deduplicated list.
     * Adds a problem if a duplicate is found.
     * @param problems receptacle for problems
     * @param items the items to deduplicate
     * @param itemName the name of the item to use in problem messages
     * @return the deduplicated items
     * @param <E> the type of item
     */
    public <E> List<E> dedupe(Collection<String> problems, Collection<E> items, String itemName) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<E> seen = new HashSet<>(items.size());
        List<E> newList = new ArrayList<>(items.size());
        boolean anyNull = false;
        for (E item : items) {
            if (item==null) {
                anyNull = true;
            } else if (!seen.add(item)) {
                problems.add("Repeated "+itemName+": "+item);
            } else {
                newList.add(item);
            }
        }
        if (anyNull) {
            problems.add("Missing "+itemName+".");
        }
        return newList;
    }
}
