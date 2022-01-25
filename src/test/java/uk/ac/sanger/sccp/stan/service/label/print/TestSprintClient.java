package uk.ac.sanger.sccp.stan.service.label.print;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.config.SprintConfig;
import uk.ac.sanger.sccp.stan.model.LabelType;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;
import uk.ac.sanger.sccp.utils.StringTemplate;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * @author dr6
 */
public class TestSprintClient {
    private SprintConfig mockSprintConfig;
    private SprintClient sprintClient;

    @BeforeEach
    void setup() {
        mockSprintConfig = mock(SprintConfig.class);
        sprintClient = spy(new SprintClient(mockSprintConfig));
    }

    @Test
    public void testToJson() throws IOException {
        final LabelType labelType = EntityFactory.getLabelType();
        LabelPrintRequest request = new LabelPrintRequest(labelType,
                List.of(new LabwareLabelData("STAN-1", "None", "2021-03-17",
                        List.of(new LabelContent("DONOR1", "TISSUE1", "1", 2),
                                new LabelContent("DONOR2", "TISSUE2", "3", 4))),
                        new LabwareLabelData("STAN-2", "None", "2021-03-16",
                                List.of(new LabelContent("DONOR3", "TISSUE3", "5"))))
        );
        StringTemplate template = new StringTemplate("{\"barcode\":\"#barcode#\", " +
                "\"date\":\"#date#\", " +
                "\"contents\":[\"#donor[0]#\", \"#tissue[0]#\", \"#replicate[0]#\", \"#state[0]#\"," +
                "\"#donor[1]#\", \"#tissue[1]#\", \"#replicate[1]#\", \"#state[1]#\"]}", "#", "#");
        when(mockSprintConfig.getTemplate(eq(labelType.getName()), anyInt())).thenReturn(template);

        JsonNode result = sprintClient.toJson("printer1", request);
        String expected = "{\"query\":\"mutation ($printer:String!, $printRequest:PrintRequest!) " +
                "{  print(printer: $printer, printRequest: $printRequest) {    jobId  }}\"," +
                "\"variables\":{\"printer\":\"printer1\"," +
                "\"printRequest\":{\"layouts\":[{\"barcode\":\"STAN-1\"," +
                "\"date\":\"2021-03-17\"," +
                "\"contents\":[\"DONOR1\",\"TISSUE1\",\"R:1\",\"S002\",\"DONOR2\",\"TISSUE2\",\"R:3\",\"S004\"]}," +
                "{\"barcode\":\"STAN-2\"," +
                "\"date\":\"2021-03-16\"," +
                "\"contents\":[\"DONOR3\",\"TISSUE3\",\"R:5\",\"\",\"\",\"\",\"\",\"\"]}]}}}";

        assertEquals(expected, result.toString());
    }

    @Test
    public void testPrintSuccess() throws IOException {
        when(mockSprintConfig.getHost()).thenReturn("http://sprint.psd.sanger.ac.uk");
        JsonNode requestJson = mock(JsonNode.class);
        doReturn(requestJson).when(sprintClient).toJson(anyString(), any());
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("data", "OK");
        doReturn(responseNode).when(sprintClient).postJson(any(), any(), any());
        LabelPrintRequest request = new LabelPrintRequest(EntityFactory.getLabelType(), List.of());

        sprintClient.print("printer1", request);

        verify(sprintClient).toJson("printer1", request);
        verify(sprintClient).postJson(new URL(mockSprintConfig.getHost()), requestJson, ObjectNode.class);
    }

    @Test
    public void testPrintError() throws IOException {
        when(mockSprintConfig.getHost()).thenReturn("http://sprint.psd.sanger.ac.uk");
        JsonNode requestJson = mock(JsonNode.class);
        doReturn(requestJson).when(sprintClient).toJson(anyString(), any());
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode errors = objectMapper.createArrayNode();
        ObjectNode error = objectMapper.createObjectNode();
        final String errorMessage = "Spilled the Jam.";
        error.put("message", errorMessage);
        errors.add(error);
        responseNode.set("errors", errors);
        doReturn(responseNode).when(sprintClient).postJson(any(), any(), any());
        LabelPrintRequest request = new LabelPrintRequest(EntityFactory.getLabelType(), List.of());

        assertThat(assertThrows(IOException.class, () -> sprintClient.print("printer1", request)))
                .hasMessage(errorMessage);

        verify(sprintClient).toJson("printer1", request);
        verify(sprintClient).postJson(new URL(mockSprintConfig.getHost()), requestJson, ObjectNode.class);
    }
}
