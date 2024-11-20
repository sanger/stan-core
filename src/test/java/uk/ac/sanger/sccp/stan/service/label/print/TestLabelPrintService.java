package uk.ac.sanger.sccp.stan.service.label.print;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.LabwareNoteService;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.label.*;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
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
    private LabwareService mockLabwareService;
    private LabwareNoteService mockNoteService;
    private WorkService mockWorkService;

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
        mockLabwareService = mock(LabwareService.class);
        mockNoteService = mock(LabwareNoteService.class);
        mockWorkService = mock(WorkService.class);

        labelPrintService = spy(new LabelPrintService(mockLabwareLabelDataService, mockPrintClientFactory, mockLabwareRepo,
                mockPrinterRepo, mockLabwarePrintRepo, mockLabelTypeRepo, mockLabwareService, mockNoteService, mockWorkService));
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
                new LabwareLabelData(labware.get(0).getBarcode(), labware.get(0).getExternalBarcode(), "None", "2021-03-17", List.of(
                        new LabelContent("DONOR1", "TISSUE1", "2", 3),
                        new LabelContent("DONOR2", "TISSUE2", "3", 4)
                )),
                new LabwareLabelData(labware.get(1).getBarcode(), labware.get(1).getExternalBarcode(), "None", "2021-03-16", List.of(
                        new LabelContent("DONOR3", "TISSUE3", "4")
                ))
        );
        LabelPrintRequest expectedRequest = new LabelPrintRequest(labware.get(0).getLabwareType().getLabelType(), labelData);

        when(mockLabwareLabelDataService.getLabelData(labware.get(0))).thenReturn(labelData.get(0));
        when(mockLabwareLabelDataService.getLabelData(labware.get(1))).thenReturn(labelData.get(1));
        when(mockLabwareService.calculateLabelType(labware.get(0))).thenReturn(labware.get(0).getLabwareType().getLabelType());
        when(mockLabwareService.calculateLabelType(labware.get(1))).thenReturn(labware.get(1).getLabwareType().getLabelType());
        doNothing().when(labelPrintService).print(any(), any());
        doReturn(List.of()).when(labelPrintService).recordPrint(any(), any(), any());

        labelPrintService.printLabware(user, printer.getName(), labware);
        verify(labelPrintService).print(printer, expectedRequest);
        verify(labelPrintService).recordPrint(printer, user, labware);
    }

    @Test
    public void testPrintLabwareWithRowBasedLabel() throws IOException {
        LabwareType lt = new LabwareType(10, "Visium ADH", 4, 2, new LabelType(6, "adh"), false);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        when(mockPrinterRepo.getByName(printer.getName())).thenReturn(printer);
        List<LabwareLabelData> labelData = List.of(
                new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), "None", "2021-03-17", List.of(
                        new LabelContent("DONOR1", "TISSUE1", "2", 3),
                        new LabelContent("DONOR2", "TISSUE2", "3", 4)
                ))
        );
        LabelPrintRequest expectedRequest = new LabelPrintRequest(lw.getLabwareType().getLabelType(), labelData);

        when(mockLabwareLabelDataService.getRowBasedLabelData(lw)).thenReturn(labelData.getFirst());
        when(mockLabwareService.calculateLabelType(lw)).thenReturn(lw.getLabwareType().getLabelType());
        doNothing().when(labelPrintService).print(any(), any());
        doReturn(List.of()).when(labelPrintService).recordPrint(any(), any(), any());

        labelPrintService.printLabware(user, printer.getName(), List.of(lw));
        verify(labelPrintService).print(printer, expectedRequest);
        verify(labelPrintService).recordPrint(printer, user, List.of(lw));
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
        when(mockLabwareService.calculateLabelType(labware.get(0))).thenReturn(labware.get(0).getLabwareType().getLabelType());
        when(mockLabwareService.calculateLabelType(labware.get(1))).thenReturn(labware.get(1).getLabwareType().getLabelType());
        assertThat(assertThrows(IllegalArgumentException.class, () -> labelPrintService.printLabware(user, printer.getName(), labware)))
                .hasMessage("Cannot perform a print request incorporating multiple different label types.");
    }

    private List<Labware> setupStripTubes() {
        LabelType lbl = new LabelType(50, "strip");
        LabwareType lt = EntityFactory.makeLabwareType(3, 1, "strip tube");
        lt.setLabelType(lbl);
        Sample[] samples = EntityFactory.makeSamples(2);
        Labware lw1 = EntityFactory.makeLabware(lt, samples[0], samples[1]);
        Labware lw2 = EntityFactory.makeLabware(lt, samples[1]);
        List<Labware> lws = List.of(lw1, lw2);
        Map<SlotIdSampleId, Set<Work>> workMap = Map.of(new SlotIdSampleId(lw1.getFirstSlot(), samples[0]),
                Set.of(EntityFactory.makeWork("SGP1")));
        when(mockWorkService.loadWorksForSlotsIn(any())).thenReturn(workMap);
        UCMap<Set<String>> lpMap = new UCMap<>(1);
        lpMap.put(lw1.getBarcode(), Set.of("LP1"));
        when(mockNoteService.findNoteValuesForLabware(anyCollection(), any())).thenReturn(lpMap);
        when(mockLabwareService.calculateLabelType(any())).then(invocation -> {
            Labware lw = invocation.getArgument(0);
            return lw.getLabwareType().getLabelType();
        });
        return lws;
    }

    @Test
    public void testPrintStripLabels() throws IOException {
        when(mockPrinterRepo.getByName(printer.getName())).thenReturn(printer);
        List<Labware> lws = setupStripTubes();
        LabelType lbl = lws.getFirst().getLabwareType().getLabelType();
        doNothing().when(labelPrintService).print(any(), any());
        doReturn(List.of()).when(labelPrintService).recordPrint(any(), any(), any());

        LabwareLabelData ld1 = new LabwareLabelData("LLD1", null, null, null, null);
        LabwareLabelData ld2 = new LabwareLabelData("LLD2", null, null, null, null);
        when(mockLabwareLabelDataService.getSplitLabelData(same(lws.get(0)), any(), any())).thenReturn(List.of(ld1));
        when(mockLabwareLabelDataService.getSplitLabelData(same(lws.get(1)), any(), any())).thenReturn(List.of(ld2));

        labelPrintService.printLabware(user, printer.getName(), lws);
        LabelPrintRequest expectedRequest = new LabelPrintRequest(lbl, List.of(ld1, ld2));
        verify(labelPrintService).print(printer, expectedRequest);
        verify(labelPrintService).recordPrint(printer, user, lws);
        verify(mockWorkService).loadWorksForSlotsIn(lws);
        verify(mockNoteService).findNoteValuesForLabware(lws, "lp number");
        verify(labelPrintService).stripLabwareLabelData(lws);
    }

    @Test
    public void testStripLabwareLabelData() {
        List<Labware> lws = setupStripTubes();
        LabwareLabelData ld1 = new LabwareLabelData("LLD1", null, null, null, null);
        LabwareLabelData ld2 = new LabwareLabelData("LLD2", null, null, null, null);
        when(mockLabwareLabelDataService.getSplitLabelData(same(lws.get(0)), any(), any())).thenReturn(List.of(ld1));
        when(mockLabwareLabelDataService.getSplitLabelData(same(lws.get(1)), any(), any())).thenReturn(List.of(ld2));
        List<LabwareLabelData> lds = labelPrintService.stripLabwareLabelData(lws);
        assertThat(lds).containsExactly(ld1, ld2);
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
