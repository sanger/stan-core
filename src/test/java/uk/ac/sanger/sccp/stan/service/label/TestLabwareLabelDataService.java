package uk.ac.sanger.sccp.stan.service.label;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelData.LabelContent;
import uk.ac.sanger.sccp.stan.service.label.LabwareLabelDataService.SimpleContent;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests {@link LabwareLabelDataService}
 * @author dr6
 */
public class TestLabwareLabelDataService {
    private PlanActionRepo mockPlanActionRepo;
    private LabwareLabelDataService service;
    private Species species;

    @BeforeEach
    void setup() {
        mockPlanActionRepo = mock(PlanActionRepo.class);
        species = new Species(1, "Human");
        service = spy(new LabwareLabelDataService(mockPlanActionRepo));
    }

    private LabwareType makeAdhLabwareType() {
        LabelType labelType = new LabelType(6, "adh");
        return new LabwareType(10, "Visium ADH", 4, 2, labelType, false);
    }

    @Test
    public void testLabwareData() {
        TissueType ttype = new TissueType(null, "Skellington", "SKE");
        SpatialLocation sl = new SpatialLocation(null, "SL4", 4, ttype);
        Tissue tissue = EntityFactory.makeTissue(EntityFactory.getDonor(), sl);
        BioState bioState = EntityFactory.getBioState();
        Sample sample1 = new Sample(null, null, tissue, bioState);
        Sample sample2 = new Sample(null, 5, tissue, bioState);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1, 2));
        lw.setCreated(LocalDateTime.of(2021,3,17,15,44));
        lw.getSlots().get(1).getSamples().addAll(List.of(sample1, sample2));
        lw.setExternalBarcode("123456");

        LabwareLabelData actual = service.getLabelData(lw);

        List<LabelContent> expectedContents = Stream.of(sample1, sample2)
                .map(sam -> new LabelContent(sam.getTissue().getDonor().getDonorName(), sam.getTissue().getExternalName(),
                        tissueString(sam.getTissue()), sam.getTissue().getReplicate(), sam.getSection()))
                .collect(toList());
        LabwareLabelData expected = new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), tissue.getMedium().getName(), "2021-03-17", expectedContents,
                Map.of("externalName", tissue.getExternalName()));
        assertEquals(expected, actual);

        Labware emptyLabware = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        emptyLabware.setCreated(LocalDateTime.of(2021,3,17,12,0));
        when(mockPlanActionRepo.findAllByDestinationLabwareId(emptyLabware.getId())).thenReturn(List.of());
        assertEquals(new LabwareLabelData(emptyLabware.getBarcode(), emptyLabware.getExternalBarcode(), null, "2021-03-17", List.of()), service.getLabelData(emptyLabware));
    }

    @Test
    public void testLabwareDataPlannedContents() {
        Donor donor1 = new Donor(null, "DONOR1", LifeStage.adult, species);
        Donor donor2 = new Donor(null, "DONOR2", LifeStage.fetal, species);
        TissueType ttype1 = new TissueType(null, "Skellington", "SKE");
        SpatialLocation sl1 = new SpatialLocation(null, "SL4", 4, ttype1);
        TissueType ttype2 = new TissueType(null, "Bananas", "BNN");
        SpatialLocation sl2 = new SpatialLocation(null, "SL7", 7, ttype2);
        Tissue tissue1 = EntityFactory.makeTissue(donor1, sl1);
        Tissue tissue2 = EntityFactory.makeTissue(donor2, sl2);
        BioState bioState = EntityFactory.getBioState();
        Sample sample1 = new Sample(null, null, tissue1, bioState);
        Sample sample2 = new Sample(null, 5, tissue2, bioState);
        Labware labware = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1, 4));
        labware.setCreated(LocalDateTime.of(2021,3,17,15,45));
        labware.setExternalBarcode("123456");
        List<Slot> slots = labware.getSlots();
        final int planId = 400;
        List<PlanAction> planActions = List.of(
                new PlanAction(404, planId, slots.get(3), slots.get(3), sample2, 14, null, null),
                new PlanAction(403, planId, slots.get(2), slots.get(2), sample2, null, null, null),
                new PlanAction(401, planId, slots.get(0), slots.get(0), sample1, null, null, null),
                new PlanAction(402, planId, slots.get(1), slots.get(1), sample1, 7, null, null)
        );
        when(mockPlanActionRepo.findAllByDestinationLabwareId(labware.getId())).thenReturn(planActions);

        LabwareLabelData actual = service.getLabelData(labware);
        List<LabelContent> expectedContents = List.of(
                new LabelContent(donor1.getDonorName(), tissueString(tissue1), tissue1.getReplicate()),
                new LabelContent(donor1.getDonorName(), tissueString(tissue1), tissue1.getReplicate(), 7),
                new LabelContent(donor2.getDonorName(), tissueString(tissue2), tissue2.getReplicate(), 5),
                new LabelContent(donor2.getDonorName(), tissueString(tissue2), tissue2.getReplicate(), 14)
        );
        assertEquals(new LabwareLabelData(labware.getBarcode(), labware.getExternalBarcode(), tissue1.getMedium().getName(), "2021-03-17", expectedContents), actual);
    }

    @ParameterizedTest
    @CsvSource({"plate,false", "xenium,true"})
    public void testSlotOrderForLabwareType(String ltName, boolean columnMajor) {
        LabwareType lt = new LabwareType(null, ltName, 2, 3, null, false);
        List<Slot> slots = Address.stream(2, 3)
                .map(ad -> new Slot(null, 100, ad, null, null, null))
                .sorted(service.slotOrderForLabwareType(lt))
                .toList();
        if (columnMajor) {
            for (int i = 0; i < slots.size(); ++i) {
                Address ad = slots.get(i).getAddress();
                int x = i/2;
                int y = i%2;
                assertEquals(x+1, ad.getColumn());
                assertEquals(y+1, ad.getRow());
            }
        } else {
            for (int i = 0; i < slots.size(); ++i) {
                Address ad = slots.get(i).getAddress();
                int x = i%3;
                int y = i/3;
                assertEquals(x+1, ad.getColumn());
                assertEquals(y+1, ad.getRow());
            }
        }
    }

    /**
     * Xenium labware content must be listed in column-major order
     */
    @Test
    public void testXeniumPlannedContents() {
        Donor donor1 = new Donor(null, "DONOR1", LifeStage.adult, species);
        Donor donor2 = new Donor(null, "DONOR2", LifeStage.fetal, species);
        Donor donor3 = new Donor(null, "DONOR3", LifeStage.paediatric, species);
        TissueType ttype1 = new TissueType(null, "Skellington", "SKE");
        SpatialLocation sl1 = new SpatialLocation(null, "SL4", 4, ttype1);
        TissueType ttype2 = new TissueType(null, "Bananas", "BNN");
        SpatialLocation sl2 = new SpatialLocation(null, "SL7", 7, ttype2);
        TissueType ttype3 = new TissueType(null, "Custard", "CTD");
        SpatialLocation sl3 = new SpatialLocation(null, "SL9", 9, ttype3);
        Tissue tissue1 = EntityFactory.makeTissue(donor1, sl1);
        Tissue tissue2 = EntityFactory.makeTissue(donor2, sl2);
        Tissue tissue3 = EntityFactory.makeTissue(donor3, sl3);
        BioState bs = EntityFactory.getBioState();
        Sample sample1 = new Sample(null, null, tissue1, bs);
        Sample sample2 = new Sample(null, 12, tissue2, bs);
        Sample sample3 = new Sample(null, null, tissue3, bs);
        Labware lw = EntityFactory.makeEmptyLabware(new LabwareType(100, LabwareType.XENIUM_NAME,
                5, 3, EntityFactory.getLabelType(), false));
        lw.setExternalBarcode("123456");
        lw.setCreated(LocalDateTime.of(2023,7,13, 18, 0));
        final int planId = 400;
        Slot A1 = lw.getFirstSlot();
        Slot A2 = lw.getSlot(new Address(1,2));
        Slot B1 = lw.getSlot(new Address(2,1));
        List<PlanAction> planActions = List.of(
                new PlanAction(403, planId, B1, B1, sample3, 21, null, null),
                new PlanAction(402, planId, A2, A2, sample2, null, null, null),
                new PlanAction(401, planId, A1, A1, sample1, 11, null, null)
        );
        when(mockPlanActionRepo.findAllByDestinationLabwareId(lw.getId())).thenReturn(planActions);
        LabwareLabelData actual = service.getLabelData(lw);
        List<LabelContent> expectedContents = List.of(
                new LabelContent("DONOR1", tissueString(tissue1), tissue1.getReplicate(), 11),
                new LabelContent("DONOR3", tissueString(tissue3), tissue3.getReplicate(), 21),
                new LabelContent("DONOR2", tissueString(tissue2), tissue2.getReplicate(), 12)
        );
        assertEquals(new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), tissue1.getMedium().getName(),
                "2023-07-13", expectedContents), actual);
    }

    @Test
    public void testLabelDataProviasette() {
        Medium medium = new Medium(1, "Sosostris");
        Fixative fix = new Fixative(2, "Bananas");
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setMedium(medium);
        tissue.setFixative(fix);
        Sample sample = new Sample(1, null, tissue, EntityFactory.getBioState());
        LabwareType provType = EntityFactory.makeLabwareType(1, 1, "Proviasette");
        Labware lw = EntityFactory.makeLabware(provType, sample);

        LabwareLabelData actual = service.getLabelData(lw);

        assertEquals(lw.getBarcode(), actual.getBarcode());
        assertEquals(medium.getName(), actual.getMedium());
        assertNotNull(actual.getDate());
        LabelContent content = new LabelContent(donor.getDonorName(), null,
                sl.getTissueType().getCode()+"-"+sl.getCode(), tissue.getReplicate(), fix.getName());
        assertThat(actual.getContents()).containsExactly(content);
    }

    @Test
    public void testAddressToSimpleContent_populated() {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeLabware(lt);
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        BioState bs = EntityFactory.getBioState();
        Tissue[] tissues = IntStream.range(0,3).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Sample[] samples = IntStream.range(0,3).mapToObj(i -> new Sample(10+i, 20+i, tissues[i], bs)).toArray(Sample[]::new);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        lw.getSlot(A1).getSamples().addAll(List.of(samples[0], samples[1]));
        lw.getSlot(A2).getSamples().add(samples[2]);

        Map<Address, List<SimpleContent>> expected = Map.of(
                A1, List.of(new SimpleContent(tissues[0], 20), new SimpleContent(tissues[1], 21)),
                A2, List.of(new SimpleContent(tissues[2], 22))
        );
        assertEquals(expected, service.addressToSimpleContent(lw));
    }

    @Test
    public void testAddressToSimpleContent_unplanned() {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeLabware(lt);
        when(mockPlanActionRepo.findAllByDestinationLabwareId(anyInt())).thenReturn(List.of());
        assertThat(service.addressToSimpleContent(lw)).isEmpty();
    }

    @Test
    public void testAddressToSimpleContent_planned() {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeLabware(lt);
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        BioState bs = EntityFactory.getBioState();
        Tissue[] tissues = IntStream.range(0, 3).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Sample[] samples = IntStream.range(0, 3).mapToObj(i -> new Sample(10 + i, 20 + i, tissues[i], bs)).toArray(Sample[]::new);
        final Address A1 = new Address(1, 1), A2 = new Address(1, 2);
        Slot source = lw.getFirstSlot();
        List<PlanAction> planActions = List.of(
                new PlanAction(20, 1, source, lw.getSlot(A1), samples[0]),
                new PlanAction(21, 1, source, lw.getSlot(A1), samples[1], 400, null, null),
                new PlanAction(22, 1, source, lw.getSlot(A2), samples[2])
        );
        when(mockPlanActionRepo.findAllByDestinationLabwareId(lw.getId())).thenReturn(planActions);
        Map<Address, List<SimpleContent>> expected = Map.of(
                A1, List.of(new SimpleContent(tissues[0], 20), new SimpleContent(tissues[1], 400)),
                A2, List.of(new SimpleContent(tissues[2], 22))
        );
        assertEquals(expected, service.addressToSimpleContent(lw));
    }

    @ParameterizedTest
    @MethodSource("sectionRangeArgs")
    public void testSectionRange(List<SimpleContent> scs, Integer min, Integer max) {
        Integer[] result = service.sectionRange(scs);
        assertThat(result).hasSize(2);
        assertEquals(min, result[0]);
        assertEquals(max, result[1]);
    }

    static Stream<Arguments> sectionRangeArgs() {
        Tissue tissue = EntityFactory.getTissue();
        return Arrays.stream(new Integer[][] {
                { null, null },
                { null, null, null },
                { 1, 1, 1 },
                { 1, null, 1, 1 },
                { 3, 2, 6, 4, 2, 6 },
        }).map(arr -> {
            final int len = arr.length;
            List<SimpleContent> scs = Arrays.stream(arr, 0, len-2)
                    .map(n -> new SimpleContent(tissue, n))
                    .collect(toList());
            return Arguments.of(scs, arr[len-2], arr[len-1]);
        });
    }

    @Test
    public void testCheckRowBasedLayout_valid() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Labware lw = EntityFactory.makeEmptyLabware(makeAdhLabwareType());
        Tissue[] tissues = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Map<Address, List<SimpleContent>> scs = Map.of(
                new Address(1,1), List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                new Address(2,2), List.of(new SimpleContent(tissues[1], 3)),
                new Address(3,1), List.of(new SimpleContent(tissues[2], 4)),
                new Address(4,2), List.of(new SimpleContent(tissues[3], 5))
        );
        assertArrayEquals(tissues, service.checkRowBasedLayout(lw, scs));
    }

    @Test
    public void testRowBasedLayout_invalid() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Labware lw = EntityFactory.makeEmptyLabware(makeAdhLabwareType());
        Tissue[] tissues = IntStream.range(0,4).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Map<Address, List<SimpleContent>> scs = Map.of(
                new Address(1,1), List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                new Address(2,2), List.of(new SimpleContent(tissues[1], 3)),
                new Address(3,1), List.of(new SimpleContent(tissues[2], 4)),
                new Address(4,2), List.of(new SimpleContent(tissues[3], 5), new SimpleContent(tissues[2], 6))
        );

        assertThat(assertThrows(IllegalArgumentException.class, () -> service.checkRowBasedLayout(lw, scs)))
                .hasMessage("The specified label template is only suitable for " +
                        "labware which has one tissue per row.");
    }

    @Test
    public void testCheckDividedLayout_valid() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Labware lw = EntityFactory.makeEmptyLabware(makeAdhLabwareType());
        Tissue[] tissues = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Map<Address, List<SimpleContent>> scs = Map.of(
                new Address(1,1), List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                new Address(2,2), List.of(new SimpleContent(tissues[0], 3)),
                new Address(3,1), List.of(new SimpleContent(tissues[1], 4)),
                new Address(4,2), List.of(new SimpleContent(tissues[1], 5))
        );
        assertArrayEquals(tissues, service.checkDividedLayout(lw, scs));
    }

    @Test
    public void testCheckDividedLayout_invalid() {
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Labware lw = EntityFactory.makeEmptyLabware(makeAdhLabwareType());
        Tissue[] tissues = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        Map<Address, List<SimpleContent>> scs = Map.of(
                new Address(1,1), List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                new Address(2,2), List.of(new SimpleContent(tissues[0], 3)),
                new Address(3,1), List.of(new SimpleContent(tissues[1], 4)),
                new Address(4,2), List.of(new SimpleContent(tissues[0], 5))
        );

        assertThat(assertThrows(IllegalArgumentException.class, () -> service.checkDividedLayout(lw, scs)))
                .hasMessage("The specified label template is only suitable for " +
                        "labware which has one tissue in the top half and one tissue in the bottom half.");
    }

    @Test
    public void testGetRowBasedLabelData_empty() {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setCreated(LocalDateTime.of(2022, 1, 12, 10, 0, 0));
        doReturn(List.of()).when(mockPlanActionRepo).findAllByDestinationLabwareId(lw.getId());
        LabwareLabelData ld = service.getRowBasedLabelData(lw);
        assertEquals(lw.getBarcode(), ld.getBarcode());
        assertNull(ld.getMedium());
        assertEquals("2022-01-12", ld.getDate());
        assertThat(ld.getContents()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    public void testGetRowBasedLabelData_valid(boolean sameMedium) {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setCreated(LocalDateTime.of(2022, 1, 12, 10, 0, 0));
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        Address B1 = new Address(2,1);
        Address D2 = new Address(4,2);
        Species species = EntityFactory.getHuman();
        Donor[] donors = { new Donor(1, "DONOR1", LifeStage.adult, species),
                new Donor(2, "DONOR2", LifeStage.paediatric, species)};
        TissueType[] tts = { new TissueType(11, "Heart", "HEA"),
                new TissueType(12, "Brain", "BRA")};
        SpatialLocation[] sls = Arrays.stream(tts)
                .map(tt -> new SpatialLocation(tt.getId()+10, "SL"+(tt.getId()+10), (tt.getId()%10)+1, tt))
                .toArray(SpatialLocation[]::new);
        Tissue[] tissues = IntStream.range(0,4)
                .mapToObj(i -> (i==2 ? null : EntityFactory.makeTissue(donors[i/2], sls[i%2])))
                .toArray(Tissue[]::new);
        tissues[0].setReplicate("100");
        tissues[1].setReplicate("101b");
        // tissues[2] is null, because that is allowed
        tissues[3].setReplicate("102");
        Medium medium = EntityFactory.getMedium();
        for (Tissue t : tissues) {
            if (t!=null) {
                t.setMedium(medium);
            }
        }
        if (!sameMedium) {
            tissues[1].setMedium(new Medium(50, "bananas"));
        }
        Map<Address, List<SimpleContent>> scs = Map.of(
                A1, List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                A2, List.of(new SimpleContent(tissues[0], 1)),
                B1, List.of(new SimpleContent(tissues[1], 3), new SimpleContent(tissues[1], 3)),
                D2, List.of(new SimpleContent(tissues[3], 6), new SimpleContent(tissues[3], 5))
        );
        doReturn(scs).when(service).addressToSimpleContent(lw);
        doReturn(tissues).when(service).checkRowBasedLayout(lw, scs);

        LabwareLabelData ld = service.getRowBasedLabelData(lw);
        assertEquals(lw.getBarcode(), ld.getBarcode());
        assertEquals("2022-01-12", ld.getDate());
        assertEquals(sameMedium ? medium.getName() : null, ld.getMedium());
        String[] donorNames = { donors[0].getDonorName(), donors[1].getDonorName() };
        String[] tissueStrings = { tissueString(tissues[0]), tissueString(tissues[1]), null, tissueString(tissues[3]) };
        String[] reps = { tissues[0].getReplicate(), tissues[1].getReplicate(), null, tissues[3].getReplicate() };

        List<LabelContent> expectedContents = List.of(
                new LabelContent(donorNames[0], null, tissueStrings[0], reps[0], "S001+"),
                new LabelContent(donorNames[0], null, tissueStrings[1], reps[1], "S003"),
                new LabelContent(null, null,null, null, (String) null),
                new LabelContent(donorNames[1], null, tissueStrings[3], reps[3], "S005+")
        );
        assertEquals(expectedContents, ld.getContents());
    }

    @Test
    public void testGetDividedLabelData_invalid() {
        LabwareType lt = new LabwareType(1, "lt", 3, 2, EntityFactory.getLabelType(), false);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        assertThat(assertThrows(IllegalArgumentException.class, () -> service.getDividedLabelData(lw)))
                .hasMessage("The specified label template is only suitable for labware with 4 rows.");
    }

    @Test
    public void testGetDividedLabelData_empty() {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setCreated(LocalDateTime.of(2022, 1, 12, 10, 0, 0));
        doReturn(List.of()).when(mockPlanActionRepo).findAllByDestinationLabwareId(lw.getId());
        LabwareLabelData ld = service.getDividedLabelData(lw);
        assertEquals(lw.getBarcode(), ld.getBarcode());
        assertNull(ld.getMedium());
        assertEquals("2022-01-12", ld.getDate());
        assertThat(ld.getContents()).isEmpty();
    }



    @ParameterizedTest
    @ValueSource(booleans={true,false})
    public void testGetDividedLabelData_valid(boolean sameMedium) {
        LabwareType lt = makeAdhLabwareType();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setCreated(LocalDateTime.of(2022, 1, 12, 10, 0, 0));
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        Address B1 = new Address(2,1);
        Address C2 = new Address(3,2);
        Species species = EntityFactory.getHuman();
        Donor[] donors = { new Donor(1, "DONOR1", LifeStage.adult, species),
                new Donor(2, "DONOR2", LifeStage.paediatric, species)};
        TissueType[] tts = { new TissueType(11, "Heart", "HEA"),
                new TissueType(12, "Brain", "BRA")};
        SpatialLocation[] sls = Arrays.stream(tts)
                .map(tt -> new SpatialLocation(tt.getId()+10, "SL"+(tt.getId()+10), (tt.getId()%10)+1, tt))
                .toArray(SpatialLocation[]::new);
        Tissue[] tissues = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeTissue(donors[i], sls[i]))
                .toArray(Tissue[]::new);
        tissues[0].setReplicate("100");
        tissues[1].setReplicate("101b");
        Medium medium = EntityFactory.getMedium();
        tissues[0].setMedium(medium);
        if (sameMedium) {
            tissues[1].setMedium(medium);
        } else {
            tissues[1].setMedium(new Medium(50, "bananas"));
        }
        Map<Address, List<SimpleContent>> scs = Map.of(
                A1, List.of(new SimpleContent(tissues[0], 1), new SimpleContent(tissues[0], 2)),
                A2, List.of(new SimpleContent(tissues[0], 1)),
                B1, List.of(new SimpleContent(tissues[0], 7), new SimpleContent(tissues[0], 3)),
                C2, List.of(new SimpleContent(tissues[1], 4), new SimpleContent(tissues[1], 5))
        );
        doReturn(scs).when(service).addressToSimpleContent(lw);
        doReturn(tissues).when(service).checkDividedLayout(lw, scs);

        LabwareLabelData ld = service.getDividedLabelData(lw);
        assertEquals(lw.getBarcode(), ld.getBarcode());
        assertEquals("2022-01-12", ld.getDate());
        assertEquals(sameMedium ? medium.getName() : null, ld.getMedium());
        String[] donorNames = { donors[0].getDonorName(), donors[1].getDonorName() };
        String[] tissueStrings = { tissueString(tissues[0]), tissueString(tissues[1]) };
        String[] reps = { tissues[0].getReplicate(), tissues[1].getReplicate() };

        List<LabelContent> expectedContents = List.of(
                new LabelContent(donorNames[0], null, tissueStrings[0], reps[0], "S001+"),
                new LabelContent(donorNames[0], null, tissueStrings[0], reps[0], "S001"),
                new LabelContent(donorNames[0], null, tissueStrings[0], reps[0], "S003+"),
                new LabelContent(donorNames[0], tissueStrings[0], reps[0]),
                new LabelContent(donorNames[1], tissueStrings[1], reps[1]),
                new LabelContent(donorNames[1], null, tissueStrings[1], reps[1], "S004+"),
                new LabelContent(donorNames[1], tissueStrings[1], reps[1]),
                new LabelContent(donorNames[1], tissueStrings[1], reps[1])
        );
        assertEquals(expectedContents, ld.getContents());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetSplitLabelData(boolean hasLp) {
        String lp = (hasLp ? "LP1" : null);
        LabwareType lt = EntityFactory.makeLabwareType(3,1);
        Sample[] samples = EntityFactory.makeSamples(2);
        Tissue tissue = samples[0].getTissue();
        Donor donor = tissue.getDonor();
        Labware lw = EntityFactory.makeLabware(lt, samples);
        String state = samples[0].getBioState().getName();
        Map<SlotIdSampleId, String> slotWork = Map.of(
                new SlotIdSampleId(lw.getFirstSlot(), samples[0]), "SGP1",
                new SlotIdSampleId(lw.getFirstSlot(), samples[1]), "SGP2"
        );
        List<LabwareLabelData> expectedLds = List.of(
                new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), null, null,
                        List.of(new LabelContent(donor.getDonorName(), null, tissue.getExternalName(), null, state)),
                        hasLp ? Map.of("lp", "LP1", "address", "B1")
                                : Map.of("address", "B1")),
                new LabwareLabelData(lw.getBarcode(), lw.getExternalBarcode(), null, null,
                        List.of(new LabelContent(donor.getDonorName(), null, tissue.getExternalName(), null, state)),
                        hasLp ? Map.of("lp", "LP1", "address", "A1", "work", "SGP1")
                                : Map.of("address", "A1", "work", "SGP1"))
        );
        assertThat(service.getSplitLabelData(lw, slotWork, lp)).containsExactlyElementsOf(expectedLds);
    }

    @ParameterizedTest
    @EnumSource(LifeStage.class)
    public void testGetTissueDesc(LifeStage lifeStage) {
        Donor donor = new Donor(null, "DONOR", lifeStage, species);
        TissueType tt = new TissueType(null, "Xylophone", "XYL");
        SpatialLocation sl = new SpatialLocation(null, "SL-6", 6, tt);
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        assertEquals(tissueString(tissue), service.getTissueDesc(tissue));
    }

    @ParameterizedTest
    @MethodSource("getContentArgs")
    public void testGetContent(Sample sample, LabelContent expected) {
        assertEquals(expected, service.getContent(sample));
    }

    static Stream<Arguments> getContentArgs() {
        final String donorName = "DONOR";
        final String rep = "15b";
        Donor donor = new Donor(1, donorName, LifeStage.adult, EntityFactory.getHuman());
        TissueType tissueType = new TissueType(1, "Heart", "HEA");
        SpatialLocation sl = new SpatialLocation(1, "SL1", 1, tissueType);
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setReplicate(rep);
        BioState tissueState = new BioState(1, "Tissue");
        BioState originalState = new BioState(2, "Original sample");
        BioState custardState = new BioState(3, "Custard");
        BioState fwState = new BioState(4, "Fetal waste");

        Sample sam1 = new Sample(1, 5, tissue, tissueState);
        Sample sam2 = new Sample(2, null, tissue, originalState);
        Sample sam3 = new Sample(3, 7, tissue, custardState);
        Sample sam4 = new Sample(4, 8, tissue, fwState);

        String tissueDesc = "HEA-1";

        return Arrays.stream(new Object[][] {
                {sam1, new LabelContent(donorName, tissue.getExternalName(), tissueDesc, rep, "S005")},
                {sam2, new LabelContent(donorName, tissue.getExternalName(), tissueDesc, rep, "Original")},
                {sam3, new LabelContent(donorName, tissue.getExternalName(), tissueDesc, rep, custardState.getName())},
                {sam4, new LabelContent(donorName, tissue.getExternalName(), tissueDesc, rep, "F waste")},
        }).map(Arguments::of);
    }

    private String tissueString(Tissue tissue) {
        String prefix = switch (tissue.getDonor().getLifeStage()) {
            case paediatric -> "P";
            case fetal -> "F";
            default -> "";
        };
        return prefix + tissue.getTissueType().getCode() + "-" + tissue.getSpatialLocation().getCode();
    }

}
