package uk.ac.sanger.sccp.stan.service.label.print;

import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.Printer;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;

/**
 * @author dr6
 */
@Component
public class PrintClientFactory {
    private final SprintClient sprintClient;

    public PrintClientFactory(SprintClient sprintClient) {
        this.sprintClient = sprintClient;
    }

    public PrintClient<LabelPrintRequest> getClient(Printer.Service service) {
        if (service == Printer.Service.sprint) {
            return sprintClient;
        }
        throw new IllegalArgumentException("Unsupported printer service: "+service);
    }
}
