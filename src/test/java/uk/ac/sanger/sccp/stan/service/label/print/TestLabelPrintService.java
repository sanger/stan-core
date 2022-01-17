package uk.ac.sanger.sccp.stan.service.label.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.label.*;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link LabelPrintService}
 * @author dr6
 */
public class TestLabelPrintService {
    private LabwareLabelDataService mockLabwareLabelDataService;
    private PrintClientFactory mockPrintClientFactory;
    private LabwareRepo mockLabwareRepo;
    private PrinterRepo mockPrinterRepo;
    private LabwarePrintRepo mockLabwarePrintRepo;
    private LabelTypeRepo mockLabelTypeRepo;

    private LabelPrintService labelPrintService;
    private User user;
    private Printer printer;
    private List<Labware> labware;

    @BeforeEach
    void setup() {
        mockLabwareLabelDataService = mock(LabwareLabelDataService.class);
        mockPrintClientFactory = mock(PrintClientFactory.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockPrinterRepo = mock(PrinterRepo.class);
        mockLabwarePrintRepo = mock(LabwarePrintRepo.class);
        mockLabelTypeRepo = mock(LabelTypeRepo.class);

        labelPrintService = spy(new LabelPrintService(mockLabwareLabelDataService, mockPrintClientFactory, mockLabwareRepo,
                mockPrinterRepo, mockLabwarePrintRepo, mockLabelTypeRepo));
        user = EntityFactory.getUser();
        printer = EntityFactory.getPrinter();
        LabwareType lt = EntityFactory.getTubeType();
        labware = List.of(EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt));
    }

    @Test
    public void testPrintLabwareBarcodes() throws IOException {
        List<String> barcodes = labware.stream().map(Labware::getBarcode).collect(toList());
        when(mockLabwareRepo.getByBarcodeIn(barcodes)).thenReturn(labware);
        doNothing().when(labelPrintService).printLabware(any(), any(), any());

        labelPrintService.printLabwareBarcodes(user, "printer1", barcodes);
        verify(labelPrintService).printLabware(user, "printer1", labware);

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> labelPrintService.printLabwareBarcodes(user, "printer1", List.of())))
                .hasMessage("No labware barcodes supplied to print.");
    }

    @Test
    public void testPrintLabwareSuccessful() throws IOException {
        when(mockPrinterRepo.getByName(printer.getName())).thenReturn(printer);
        List<LabwareLabelData> labelData = List.of(
                new LabwareLabelData(labware.get(0).getBarcode(), "None", "2021-03-17", List.of(
                        new LabelContent("DONOR1", "TISSUE1", "2", 3),
                        new LabelContent("DONOR2", "TISSUE2", "3", 4)
                )),
                new LabwareLabelData(labware.get(1).getBarcode(), "None", "2021-03-16", List.of(
                        new LabelContent("DONOR3", "TISSUE3", "4")
                ))
        );
        LabelPrintRequest expectedRequest = new LabelPrintRequest(labware.get(0).getLabwareType().getLabelType(), labelData);

        when(mockLabwareLabelDataService.getLabelData(labware.get(0))).thenReturn(labelData.get(0));
        when(mockLabwareLabelDataService.getLabelData(labware.get(1))).thenReturn(labelData.get(1));
        doNothing().when(labelPrintService).print(any(), any());
        doReturn(List.of()).when(labelPrintService).recordPrint(any(), any(), any());

        labelPrintService.printLabware(user, printer.getName(), labware);
        verify(labelPrintService).print(printer, expectedRequest);
        verify(labelPrintService).recordPrint(printer, user, labware);
    }

    @Test
    public void testPrintLabwareErrors() throws IOException {
        assertThat(assertThrows(IllegalArgumentException.class, () -> labelPrintService.printLabware(user, printer.getName(), List.of())))
                .hasMessage("No labware supplied to print.");

        LabwareType unprintableType = new LabwareType(1, "dud", 1, 1, null, true);
        Labware lw = EntityFactory.makeEmptyLabware(unprintableType);
        assertThat(assertThrows(IllegalArgumentException.class, () -> labelPrintService.printLabware(user, printer.getName(), List.of(lw))))
                .hasMessage("Cannot print label for labware without a label type.");

        List<Labware> labware = IntStream.range(0,2)
                .mapToObj(i -> new LabelType(i, "label type "+i))
                .map(labelType -> new LabwareType(10+labelType.getId(), "lw type "+labelType.getId(), 1, 1, labelType, false))
                .map(EntityFactory::makeEmptyLabware)
                .collect(toList());
        assertThat(assertThrows(IllegalArgumentException.class, () -> labelPrintService.printLabware(user, printer.getName(), labware)))
                .hasMessage("Cannot perform a print request incorporating multiple different label types.");
    }

    @Test
    public void testPrint() throws IOException {
        //noinspection unchecked
        PrintClient<LabelPrintRequest> mockPrintClient = mock(PrintClient.class);
        when(mockPrintClientFactory.getClient(printer.getService())).thenReturn(mockPrintClient);
        LabelPrintRequest request = new LabelPrintRequest(EntityFactory.getLabelType(), List.of());
        labelPrintService.print(printer, request);

        verify(mockPrintClient).print(printer.getName(), request);
    }

    @Test
    public void testRecordPrint() {
        LocalDateTime now = LocalDateTime.now();
        List<LabwarePrint> results = List.of(
                new LabwarePrint(10, printer, labware.get(0), user, now),
                new LabwarePrint(11, printer, labware.get(1), user, now)
        );
        when(mockLabwarePrintRepo.saveAll(any())).thenReturn(results);

        assertSame(results, labelPrintService.recordPrint(printer, user, labware));
        verify(mockLabwarePrintRepo).saveAll(List.of(new LabwarePrint(printer, labware.get(0), user),
                new LabwarePrint(printer, labware.get(1), user)));
    }

    @Test
    public void testFindPrinters() {
        LabelType labelType = EntityFactory.getLabelType();
        when(mockLabelTypeRepo.getByName(labelType.getName())).thenReturn(labelType);
        Printer printer2 = new Printer(2, "printer 2", List.of(new LabelType(2, "label type 2")), Printer.Service.sprint);
        List<Printer> allPrinters = List.of(this.printer, printer2);
        when(mockPrinterRepo.findAll()).thenReturn(allPrinters);
        List<Printer> somePrinters = List.of(this.printer);
        when(mockPrinterRepo.findAllByLabelTypes(labelType)).thenReturn(somePrinters);

        assertSame(allPrinters, labelPrintService.findPrinters(null));
        assertSame(somePrinters, labelPrintService.findPrinters(labelType.getName()));
    }

}
