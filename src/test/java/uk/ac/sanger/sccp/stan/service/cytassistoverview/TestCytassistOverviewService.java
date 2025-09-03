package uk.ac.sanger.sccp.stan.service.cytassistoverview;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.CytassistOverview;
import uk.ac.sanger.sccp.stan.repo.CytassistOverviewRepo;

import javax.persistence.EntityManager;
import java.util.List;

import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.mockTransactor;

class TestCytassistOverviewService {
    @Mock
    private CytassistOverviewDataCompiler mockDataCompiler;
    @Mock
    private CytassistOverviewRepo mockRepo;
    @Mock
    private EntityManager mockEntityManager;
    @Mock
    private Transactor mockTransactor;
    @InjectMocks
    private CytassistOverviewServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testUpdate() {
        List<CytassistOverview> data = List.of(new CytassistOverview());
        mockTransactor(mockTransactor);
        when(mockDataCompiler.execute()).thenReturn(data);
        service.update();
        InOrder order = inOrder(mockTransactor, mockDataCompiler, mockEntityManager, mockRepo);
        order.verify(mockTransactor).transact(any(), any());
        order.verify(mockDataCompiler).execute();
        order.verify(mockRepo).deleteAllInBatch();
        order.verify(mockEntityManager).flush();
        order.verify(mockRepo).saveAll(same(data));
    }

    @Test
    void testScheduledUpdate() {
        doNothing().when(service).update();
        service.scheduledUpdate();
        verify(service).update();
    }

}