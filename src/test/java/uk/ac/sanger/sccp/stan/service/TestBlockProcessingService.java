package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.service.block.BlockMakerFactory;
import uk.ac.sanger.sccp.stan.service.block.BlockValidatorFactory;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import static org.mockito.Mockito.spy;

/**
 * Tests {@link BlockProcessingServiceImp}
 */
public class TestBlockProcessingService {
    @Mock
    private BlockValidatorFactory mockBlockValFactory;
    @Mock
    private BlockMakerFactory mockBlockMakerFactory;
    @Mock
    private StoreService mockStoreService;
    @Mock
    private Transactor mockTransactor;

    private BlockProcessingServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new BlockProcessingServiceImp(mockBlockValFactory, mockBlockMakerFactory,
                mockStoreService, mockTransactor));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }
    // TODO
}