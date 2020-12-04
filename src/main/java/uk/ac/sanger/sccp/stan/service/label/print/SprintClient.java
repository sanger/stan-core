package uk.ac.sanger.sccp.stan.service.label.print;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.SprintConfig;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData;
import uk.ac.sanger.sccp.utils.BaseHttpClient;
import uk.ac.sanger.sccp.utils.StringTemplate;

import java.io.IOException;
import java.net.URL;

/**
 * Client for sending print requests to SPrint
 * @author dr6
 */
@Component
public class SprintClient extends BaseHttpClient implements PrintClient<LabelPrintRequest> {
    Logger log = LoggerFactory.getLogger(SprintClient.class);

    private final SprintConfig config;
    private final ObjectMapper objectMapper;

    @Autowired
    public SprintClient(SprintConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void print(String printerName, LabelPrintRequest request) throws IOException {
        JsonNode jsonData = toJson(printerName, request);
        ObjectNode result = postJson(new URL(config.getHost()), jsonData, ObjectNode.class);
        checkResult(result);
    }

    public JsonNode toJson(String printerName, LabelPrintRequest request) throws IOException {
        String labelTypeName = request.getLabelType().getName();
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("printer", printerName);
        ArrayNode layouts = objectMapper.createArrayNode();
        for (LabwareLabelData lwData : request.getLabwareLabelData()) {
            StringTemplate template = config.getTemplate(labelTypeName, lwData.getContents().size());
            ObjectNode labwareObjectNode = objectMapper.readValue(template.substitute(lwData.getFields()), ObjectNode.class);
            layouts.add(labwareObjectNode);
        }
        ObjectNode printRequest = objectMapper.createObjectNode();
        printRequest.set("layouts", layouts);
        variables.set("printRequest", printRequest);

        ObjectNode printMutation = objectMapper.createObjectNode();
        printMutation.put("query", getQueryText());
        printMutation.set("variables", variables);
        return printMutation;
    }

    // expose for mockery
    protected <T> T postJson(URL url, Object data, Class<T> jsonReturnType) throws IOException {
        return super.postJson(url, data, jsonReturnType);
    }

    private String getQueryText() {
        return "mutation ($printer:String!, $printRequest:PrintRequest!) {" +
                "  print(printer: $printer, printRequest: $printRequest) {" +
                "    jobId" +
                "  }" +
                "}";
    }

    private void checkResult(ObjectNode result) throws IOException {
        if (result.has("errors")) {
            log.error("Error from SPrint");
            log.error(result.toPrettyString());
            throw new IOException(getSprintErrorMessage(result));
        }
    }

    private String getSprintErrorMessage(ObjectNode result) {
        return result.get("errors").get(0).get("message").asText();
    }

}
