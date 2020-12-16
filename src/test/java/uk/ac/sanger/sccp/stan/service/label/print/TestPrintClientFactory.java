package uk.ac.sanger.sccp.stan.service.label.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.Printer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link PrintClientFactory}
 * @author dr6
 */
public class TestPrintClientFactory {
    private PrintClientFactory printClientFactory;
    private SprintClient mockSprintClient;

    @BeforeEach
    void setup() {
        mockSprintClient = mock(SprintClient.class);
        printClientFactory = new PrintClientFactory(mockSprintClient);
    }

    @Test
    public void testGetClient() {
        assertSame(mockSprintClient, printClientFactory.getClient(Printer.Service.sprint));
        assertThrows(IllegalArgumentException.class, () -> printClientFactory.getClient(null));
    }
}
