package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.Tissue;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterLabware;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterSample;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test {@link BlockFieldChecker} */
class TestBlockFieldChecker {

    @ParameterizedTest
    @CsvSource({
            "false,false",
            "false,true",
            "true,true",
    })
    void testCheck_ok(boolean hasOldDate, boolean hasNewDate) {
        BlockFieldChecker checker = new BlockFieldChecker();
        Tissue tissue = EntityFactory.getTissue();
        LocalDate date = LocalDate.of(2020,1,1);
        tissue.setCollectionDate(hasOldDate ? date : null);
        BlockRegisterLabware brl = new BlockRegisterLabware();
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setDonorIdentifier(tissue.getDonor().getDonorName());
        brs.setTissueType(tissue.getTissueType().getName());
        brs.setHmdmc(tissue.getHmdmc().getHmdmc());
        brs.setSpatialLocation(tissue.getSpatialLocation().getCode());
        brs.setSpecies(tissue.getDonor().getSpecies().getName());
        brs.setTissueType(tissue.getTissueType().getName());
        brs.setReplicateNumber(tissue.getReplicate());
        brs.setCellClass(tissue.getCellClass().getName());
        brs.setSampleCollectionDate(hasNewDate ? date : null);

        brl.setMedium(tissue.getMedium().getName());
        brl.setFixative(tissue.getFixative().getName());

        List<String> problems = new ArrayList<>();
        checker.check(problems::add, brl, brs, tissue);
        assertThat(problems).isEmpty();
    }

    @Test
    void testCheck_bad() {
        BlockFieldChecker checker = new BlockFieldChecker();
        Tissue tissue = EntityFactory.getTissue();
        tissue.setCollectionDate(LocalDate.of(2020,1,1));
        BlockRegisterLabware brl = new BlockRegisterLabware();
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setDonorIdentifier("DONORX");
        brs.setTissueType("TYPEX");
        brs.setHmdmc("HMDMCX");
        brs.setSpatialLocation(4);
        brs.setSpecies("SPECIESX");
        brs.setTissueType("TTX");
        brs.setReplicateNumber("RX");
        brs.setCellClass("CCX");
        brs.setSampleCollectionDate(LocalDate.of(2020,1,2));

        brl.setMedium("MEDIUMX");
        brl.setFixative("FIXX");
        final String fxt = " for existing tissue "+tissue.getExternalName()+".";
        List<String> expectedProblems = List.of(
                "Expected donor identifier to be "+tissue.getDonor().getDonorName()+fxt,
                "Expected HuMFre number to be "+tissue.getHmdmc().getHmdmc()+fxt,
                "Expected tissue type to be "+tissue.getTissueType().getName()+fxt,
                "Expected spatial location to be "+tissue.getSpatialLocation().getCode()+fxt,
                "Expected replicate number to be "+tissue.getReplicate()+fxt,
                "Expected medium to be "+tissue.getMedium().getName()+fxt,
                "Expected fixative to be "+tissue.getFixative().getName()+fxt,
                "Expected cellular classification to be "+tissue.getCellClass().getName()+fxt,
                "Expected sample collection date to be "+tissue.getCollectionDate()+fxt
        );

        List<String> problems = new ArrayList<>(expectedProblems.size());
        checker.check(problems::add, brl, brs, tissue);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @CsvSource({
            "a,A,true",
            ",,true",
            "a,,false",
            ",a,false",
            "a,b,false",
            "'','',true",
            ",'',true",
    })
    void testMatch(String oldValue, String newValue, boolean expected) {
        assertEquals(expected, BlockFieldChecker.match(oldValue, newValue));
    }
}