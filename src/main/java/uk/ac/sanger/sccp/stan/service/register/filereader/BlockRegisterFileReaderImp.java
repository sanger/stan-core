package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterLabware;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.BlockRegisterFileReader.Column;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class BlockRegisterFileReaderImp extends BaseRegisterFileReader<BlockRegisterRequest, Column>
        implements BlockRegisterFileReader {

    protected BlockRegisterFileReaderImp() {
        super(Column.class, 1, 3);
    }

    @Override
    protected BlockRegisterRequest createRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        List<BlockRegisterLabware> brlw = createLabwareRequests(problems, rows);
        Set<String> workNumberSet = getUnique(rows.stream().map(row -> workNumberSet((String) row.get(Column.Work_number))),
                () -> problems.add("All rows must list the same work numbers."));
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        List<String> workNumbers = (nullOrEmpty(workNumberSet) ? List.of() : new ArrayList<>(workNumberSet));
        return new BlockRegisterRequest(workNumbers, brlw);
    }

    /**
     * Gets the set of work numbers specified in a row.
     * Null if none are specified.
     * @param string the string listing zero, one or more work numbers
     * @return a nonempty set of work numbers, or null
     */
    public static Set<String> workNumberSet(String string) {
        if (string == null) {
            return null;
        }
        string = string.trim().toUpperCase();
        if (string.isEmpty()) {
            return null;
        }
        String[] wns = string.replace(',',' ').split("\\s+");
        Set<String> set = Arrays.stream(wns)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
        return (set.isEmpty() ? null : set);
    }

    /**
     * Parses the rows and groups them into labware.
     * @param problems receptacle for problems
     * @param rows rows from the file
     * @return the labware requests
     */
    public List<BlockRegisterLabware> createLabwareRequests(Collection<String> problems, List<Map<Column, Object>> rows) {

    }
}
