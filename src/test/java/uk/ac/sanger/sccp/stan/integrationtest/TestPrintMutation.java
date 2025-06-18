package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.BioStateRepo;
import uk.ac.sanger.sccp.stan.repo.LabwarePrintRepo;
import uk.ac.sanger.sccp.stan.service.label.LabelPrintRequest;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData;
import uk.ac.sanger.sccp.stan.service.label.print.PrintClient;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the print mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestPrintMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private BioStateRepo bioStateRepo;
    @Autowired
    private LabwarePrintRepo labwarePrintRepo;

    @Test
    @Transactional
    public void testPrintLabware() throws Exception {
        //noinspection unchecked
        PrintClient<LabelPrintRequest> mockPrintClient = mock(PrintClient.class);
        when(tester.getMockPrintClientFactory().getClient(any())).thenReturn(mockPrintClient);
        tester.setUser(entityCreator.createUser("dr6"));
        BioState rna = bioStateRepo.getByName("RNA");
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1");
        Labware lw = entityCreator.createLabware("STAN-SLIDE", entityCreator.createLabwareType("slide6", 3, 2),
                entityCreator.createSample(tissue, 1), entityCreator.createSample(tissue, 2),
                entityCreator.createSample(tissue, 3), entityCreator.createSample(tissue, 4, rna));
        lw.setCreated(LocalDateTime.of(2021,3,17,15,57));
        lw.setExternalBarcode("12345");
        Printer printer = entityCreator.createPrinter("stub");
        String mutation = "mutation { printLabware(barcodes: [\"STAN-SLIDE\"], printer: \"stub\") }";
        assertThat(tester.<Map<?,?>>post(mutation)).isEqualTo(Map.of("data", Map.of("printLabware", "OK")));
        String donorName = tissue.getDonor().getDonorName();
        String tissueDesc = getTissueDesc(tissue);
        String replicate = tissue.getReplicate();
        verify(mockPrintClient).print("stub", new LabelPrintRequest(
                lw.getLabwareType().getLabelType(),
                List.of(new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), tissue.getMedium().getName(), "2021-03-17",
                        List.of(
                                new LabwareLabelData.LabelContent(donorName, tissueDesc, replicate, 1),
                                new LabwareLabelData.LabelContent(donorName, tissueDesc, replicate, 2),
                                new LabwareLabelData.LabelContent(donorName, tissueDesc, replicate, 3),
                                new LabwareLabelData.LabelContent(donorName, tissue.getExternalName(), tissueDesc, replicate, "RNA")
                        ))
                ))
        );

        Iterator<LabwarePrint> recordIter = labwarePrintRepo.findAll().iterator();
        assertTrue(recordIter.hasNext());
        LabwarePrint record = recordIter.next();
        assertFalse(recordIter.hasNext());

        assertNotNull(record.getPrinted());
        assertNotNull(record.getId());
        assertEquals(record.getLabware().getId(), lw.getId());
        assertEquals(record.getPrinter().getId(), printer.getId());
        assertEquals(record.getUser().getUsername(), "dr6");
    }


    private static String getTissueDesc(Tissue tissue) {
        String prefix = switch (tissue.getDonor().getLifeStage()) {
            case paediatric -> "P";
            case fetal -> "F";
            default -> "";
        };
        return String.format("%s%s-%s", prefix, tissue.getTissueType().getCode(), tissue.getSpatialLocation().getCode());
    }
}
