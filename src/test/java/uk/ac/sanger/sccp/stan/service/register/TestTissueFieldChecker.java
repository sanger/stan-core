package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.Tissue;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link TissueFieldChecker}
 * @author dr6
 */
public class TestTissueFieldChecker {
    TissueFieldChecker checker;

    @BeforeEach
    void setup() {
        checker = new TissueFieldChecker();
    }

    @ParameterizedTest
    @MethodSource("matchArgs")
    public void testMatch(Object a, Object b, boolean expected) {
        assertEquals(expected, checker.match(a, b));
    }

    static Stream<Arguments> matchArgs() {
        return Stream.of(
                Arguments.of("Alpha", "alpha", true),
                Arguments.of(null, null, true),
                Arguments.of(null, "", true),
                Arguments.of(11, 11, true),

                Arguments.of("Alpha", "beta", false),
                Arguments.of("Alpha", null, false),
                Arguments.of("Alpha", 11, false),
                Arguments.of(null, "hi", false),
                Arguments.of(null, 11, false),
                Arguments.of(11, null, false),
                Arguments.of(11, "bananas", false)
        );
    }

    @ParameterizedTest
    @MethodSource("checkArgs")
    public void testCheck(BlockRegisterRequest br, Tissue tissue, Object problemObj) {
        Collection<String> expectedProblems;
        if (problemObj==null) {
            expectedProblems = List.of();
        } else if (problemObj instanceof Collection) {
            //noinspection unchecked
            expectedProblems = (Collection<String>) problemObj;
        } else {
            expectedProblems = List.of((String) problemObj);
        }

        List<String> problems = new ArrayList<>();
        checker.check(problems::add, br, tissue);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkArgs() {
        final Tissue tissue = EntityFactory.getTissue();
        final String forTissue = " for existing tissue "+tissue.getExternalName()+".";

        return Stream.of(
                Arguments.of(toBRR(tissue, null), tissue, null),
                Arguments.of(toBRR(tissue, br -> br.setDonorIdentifier("Foo")), tissue, "Expected donor identifier to be "+tissue.getDonor().getDonorName()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setHmdmc("12/345")), tissue, "Expected HMDMC number to be "+tissue.getHmdmc().getHmdmc()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setTissueType("Plumbus")), tissue, "Expected tissue type to be "+tissue.getTissueType().getName()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setSpatialLocation(18)), tissue, "Expected spatial location to be "+tissue.getSpatialLocation().getCode()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setReplicateNumber("-5")), tissue, "Expected replicate number to be "+tissue.getReplicate()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setMedium("Custard")), tissue, "Expected medium to be "+tissue.getMedium().getName()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setMouldSize("Giant")), tissue, "Expected mould size to be "+tissue.getMouldSize().getName()+forTissue),
                Arguments.of(toBRR(tissue, br -> br.setFixative("Glue")), tissue, "Expected fixative to be "+tissue.getFixative().getName()+forTissue),
                Arguments.of(toBRR(tissue, br -> {
                    br.setDonorIdentifier("Foo");
                    br.setHmdmc("12/345");
                    br.setTissueType("Plumbus");
                }), tissue,
                        List.of("Expected donor identifier to be "+tissue.getDonor().getDonorName()+forTissue,
                                "Expected HMDMC number to be "+tissue.getHmdmc().getHmdmc()+forTissue,
                                "Expected tissue type to be "+tissue.getTissueType().getName()+forTissue)
                )
        );
    }

    private static BlockRegisterRequest toBRR(Tissue tissue, Consumer<BlockRegisterRequest> adjuster) {
        BlockRegisterRequest br = new BlockRegisterRequest();
        br.setExistingTissue(true);
        br.setExternalIdentifier(tissue.getExternalName());
        br.setTissueType(tissue.getTissueType().getName());
        br.setDonorIdentifier(tissue.getDonor().getDonorName());
        br.setHmdmc(tissue.getHmdmc().getHmdmc());
        br.setSpatialLocation(tissue.getSpatialLocation().getCode());
        br.setReplicateNumber(tissue.getReplicate());
        br.setMedium(tissue.getMedium().getName());
        br.setMouldSize(tissue.getMouldSize().getName());
        br.setFixative(tissue.getFixative().getName());
        if (adjuster!=null) {
            adjuster.accept(br);
        }
        return br;
    }
}
