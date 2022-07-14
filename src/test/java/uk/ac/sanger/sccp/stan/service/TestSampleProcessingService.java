package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AddExternalIDRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;


import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link SampleProcessingServiceImp}
 * @author bt8
 **/
public class TestSampleProcessingService {
    private TissueRepo mockTissueRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLabwareRepo;
    private OperationService mockOpService;
    private SampleProcessingServiceImp sampleProcessingService;
    private Validator<String> mockExternalNameValidator;

    @BeforeEach
    void setup() {
        mockTissueRepo = mock(TissueRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockOpService = mock(OperationService.class);
        //noinspection unchecked
        mockExternalNameValidator = mock(Validator.class);

        sampleProcessingService = spy(new SampleProcessingServiceImp(mockTissueRepo, mockOpTypeRepo, mockLabwareRepo, mockOpService, mockExternalNameValidator));
    }

    @Test
    public void testValidAddExternalId() {
        User user = EntityFactory.getUser();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setExternalName("");
        Sample sample = new Sample(1, 100, tissue, null);
        LabwareType lt = EntityFactory.makeLabwareType(1,1, "LT1");
        Labware lw = EntityFactory.makeLabware(lt, sample);
        OperationType opType = EntityFactory.makeOperationType("Add External ID", null);
        Operation op = new Operation(200, opType, null, null, user);

        when(mockLabwareRepo.getByBarcode(any())).thenReturn(lw);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);

        AddExternalIDRequest request = new AddExternalIDRequest(lw.getBarcode(), "ExternalName");
        OperationResult opRes = new OperationResult(List.of(op), List.of(lw));
        Assertions.assertEquals(opRes, sampleProcessingService.addExternalID(user, request));

        verify(mockLabwareRepo).getByBarcode(eq(lw.getBarcode()));
        verify(sampleProcessingService).validateSamples(any(), eq(Set.of(sample)));
        verify(sampleProcessingService).validateExternalName(any(), eq("ExternalName"));
        verify(mockTissueRepo).findAllByExternalName(eq("ExternalName"));
    }

    @Test
    public void testInvalidAddExternalId() {
        User user = EntityFactory.getUser();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample = new Sample(1, 100, tissue, null);
        LabwareType lt = EntityFactory.makeLabwareType(1,1, "LT1");
        Labware lw = EntityFactory.makeLabware(lt, sample);

        when(mockLabwareRepo.getByBarcode(any())).thenReturn(lw);

        // Use an existing tissue with external name
        AddExternalIDRequest request = new AddExternalIDRequest(lw.getBarcode(), tissue.getExternalName());
        assertValidationException(() -> sampleProcessingService.addExternalID(user, request), "The request could not be validated.",
                "The associated tissue already has an external identifier: " + tissue.getExternalName()
        );

        verify(mockLabwareRepo).getByBarcode(eq(lw.getBarcode()));
        verify(sampleProcessingService).validateSamples(any(), eq(Set.of(sample)));
        verify(sampleProcessingService).validateExternalName(any(), eq(tissue.getExternalName()));
        verify(mockTissueRepo).findAllByExternalName(eq(tissue.getExternalName()));
        verifyNoInteractions(mockOpTypeRepo);
    }

    @Test
    public void testExternalNameValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);

        when(mockTissueRepo.findAllByExternalName(any())).thenReturn(List.of(tissue));

        sampleProcessingService.validateExternalName(problems, tissue.getExternalName());
        assertThat(problems).contains("External identifier is already associated with another sample: "+tissue.getExternalName());
        verify(mockTissueRepo).findAllByExternalName(eq(tissue.getExternalName()));
        verify(mockExternalNameValidator).validate(eq(tissue.getExternalName()), any());
    }

    @Test
    public void testNoSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        sampleProcessingService.validateSamples(problems, Set.of());
        assertThat(problems).contains("Could not find a sample associated with this labware");
    }

    @Test
    public void testTooManySamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample1 = new Sample(1, 100, tissue, null);
        Sample sample2 = new Sample(2, 100, tissue, null);

        sampleProcessingService.validateSamples(problems, Set.of(sample1, sample2));
        assertThat(problems).contains("There are too many samples associated with this labware");
    }

    @Test
    public void testExistingExternalIdSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample1 = new Sample(1, 100, tissue, null);

        sampleProcessingService.validateSamples(problems, Set.of(sample1));
        assertThat(problems).contains("The associated tissue already has an external identifier: "+tissue.getExternalName());
    }

    @Test
    public void testNoReplicateSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setReplicate("");
        tissue.setExternalName("");
        Sample sample1 = new Sample(1, 100, tissue, null);

        sampleProcessingService.validateSamples(problems, Set.of(sample1));
        assertThat(problems).contains("The associated tissue does not have a replicate number");
    }
}
