package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Tests {@link RecentOpServiceImp}
 */
public class RecentOpServiceTest {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationRepo mockOpRepo;

    @InjectMocks
    private RecentOpServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    public void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2})
    public void testFindLatestOp(int numOpsFound) {
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("Fry", null);
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        List<Operation> ops = IntStream.rangeClosed(1, numOpsFound)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setPerformed(LocalDateTime.of(2023,8,14,12,i));
                    op.setId(i);
                    return op;
                })
                .collect(toList());
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()))).thenReturn(ops);

        Operation op = service.findLatestOp(lw.getBarcode(), opType.getName());
        assertSame(numOpsFound==0 ? null : ops.get(numOpsFound-1), op);
    }
}