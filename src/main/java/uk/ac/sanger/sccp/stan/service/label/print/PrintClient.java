package uk.ac.sanger.sccp.stan.service.label.print;

import java.io.IOException;

/**
 * @author dr6
 */
public interface PrintClient<T> {
    /**
     * Sends the given request to this client's service to print on the specified printer.
     * @param request the specification of what to print
     * @param printerName the identifier for the printer
//     * @exception JSONException there was a problem formulating the JSON for the request or parsing the response
     * @exception IOException there was a problem transmitting the request to the print service
     */
    void print(String printerName, T request) throws IOException;
}

