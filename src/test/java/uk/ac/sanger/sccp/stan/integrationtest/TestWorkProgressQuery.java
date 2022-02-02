package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.work.WorkProgressColumn;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.IntFunction;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.tsvToMap;

/**
 * Tests the work progress query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestWorkProgressQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OperationTypeRepo opTypeRepo;
    @Autowired
    private StainTypeRepo stainTypeRepo;
    @Autowired
    private LabwareTypeRepo lwTypeRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private WorkRepo workRepo;

    private final LocalDateTime timeZero = LocalDateTime.of(2021,12,7,12,0);

    @Test
    @Transactional
    public void testWorkProgressQuery() throws Exception {
        OperationType sectionOpType = opTypeRepo.getByName("Section");
        OperationType cdnaOpType = opTypeRepo.getByName("Visium cDNA");
        OperationType extractOpType = opTypeRepo.getByName("Extract");
        OperationType stainOpType = opTypeRepo.getByName("Stain");
        OperationType image = entityCreator.createOpType("Image", null, OperationTypeFlag.IN_PLACE);
        OperationType rinOpType = entityCreator.createOpType("RIN analysis", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.ANALYSIS);

        StainType rnaStainType = stainTypeRepo.save(new StainType(null, "RNAscope"));
        StainType otherStainType = stainTypeRepo.save(new StainType(null, "Varnish"));

        LabwareType visTO = lwTypeRepo.getByName("Visium TO");
        LabwareType visLP = lwTypeRepo.getByName("Visium LP");

        Sample sample = entityCreator.createSample(null, null);
        Labware lw0 = entityCreator.createLabware("STAN-A0", entityCreator.getTubeType(), sample);
        Labware lwTO = entityCreator.createLabware("STAN-A1", visTO, sample);
        Labware lwLP = entityCreator.createLabware("STAN-A2", visLP, sample);
        User user = entityCreator.createUser("user1");

        Operation[] ops = {
                createOp(sectionOpType, user, lw0, lwTO, 0),
                createOp(cdnaOpType, user, lwTO, lwTO, 1),
                createOp(extractOpType, user, lwTO, lwTO, 2),
                createOp(stainOpType, rnaStainType, user, lwTO, lwTO, 3),
                createOp(stainOpType, otherStainType, user, lwLP, lwLP, 4),
                createOp(image, user, lwTO, lwTO, 5),
                createOp(rinOpType, user, lwTO, lwTO, 6),
        };

        Work work = entityCreator.createWork(null, null, null);
        work.setOperationIds(Arrays.stream(ops).map(Operation::getId).collect(toList()));
        work = workRepo.save(work);

        WorkType otherWorkType = entityCreator.createWorkType("Bananas");

        String query = tester.readGraphQL("workprogress.graphql").replace("SGP500", work.getWorkNumber());
        Object result = tester.post(query);
        List<?> workProgresses = chainGet(result, "data", "workProgress");
        assertThat(workProgresses).hasSize(1);
        assertEquals(work.getWorkNumber(), chainGet(workProgresses, 0, "work", "workNumber"));
        List<Map<String,String>> timestamps = chainGet(workProgresses,0, "timestamps");
        // time values are strings
        Map<String,String>[] expected = Arrays.stream(new Object[][] {
                {"Section", 0},
                {"Visium cDNA", 1},
                {"Extract", 2},
                {"Stain Visium TO", 3},
                {"RNAscope/IHC stain", 3},
                {"Stain Visium LP", 4},
                {"Stain", 4},
                {"Image", 5},
                {"Analysis", 6},
        }).map(arr -> Map.of("type", (String) arr[0], "timestamp", timeToString(ops[(int) arr[1]].getPerformed())))
                .toArray((IntFunction<Map<String, String>[]>) Map[]::new);
        assertThat(timestamps).containsExactlyInAnyOrder(expected);


        final WorkProgressColumn[] columns = WorkProgressColumn.values();
        String[] expectedHeadings = Arrays.stream(columns)
                .map(Object::toString)
                .toArray(String[]::new);

        Map<String, String> expectedEntries = new HashMap<>(columns.length);
        expectedEntries.put("Work number", work.getWorkNumber());
        expectedEntries.put("Status", work.getStatus().toString());
        expectedEntries.put("Work type", work.getWorkType().getName());

        expectedEntries.put("Last section", timeToString(ops[0].getPerformed()));
        expectedEntries.put("Last CDNA transfer", timeToString(ops[1].getPerformed()));
        expectedEntries.put("Last RNA extract", timeToString(ops[2].getPerformed()));
        expectedEntries.put("Last stain Visium TO", timeToString(ops[3].getPerformed()));
        expectedEntries.put("Last RNAscope or IHC stain", timeToString(ops[3].getPerformed()));
        expectedEntries.put("Last stain Visium LP", timeToString(ops[4].getPerformed()));
        expectedEntries.put("Last stain", timeToString(ops[4].getPerformed()));
        expectedEntries.put("Last image", timeToString(ops[5].getPerformed()));
        expectedEntries.put("Last RNA analysis", timeToString(ops[6].getPerformed()));

        String[] tsvStrings = {
                getWorkProgressFile(work.getWorkNumber(), null, null),
                getWorkProgressFile(null, List.of(Work.Status.failed, Work.Status.active, Work.Status.completed), null),
                getWorkProgressFile(null, null, List.of(work.getWorkType().getName()))
        };
        for (String tsvString : tsvStrings) {
            List<Map<String, String>> tsvMapList = tsvToMap(tsvString);
            assertThat(tsvMapList).hasSize(1);
            Map<String, String> tsvMap = tsvMapList.get(0);
            assertThat(tsvMap.keySet()).containsExactlyInAnyOrder(expectedHeadings);
            assertThat(tsvMap).containsAllEntriesOf(expectedEntries);
        }

        String[] emptyResults = {
                getWorkProgressFile(work.getWorkNumber(), List.of(Work.Status.failed), null),
                getWorkProgressFile(null, List.of(Work.Status.failed), null),
                getWorkProgressFile(null, null, List.of(otherWorkType.getName())),
        };
        for (String tsvString : emptyResults) {
            List<Map<String, String>> tsvMapList = tsvToMap(tsvString);
            assertThat(tsvMapList).isEmpty();
        }
    }

    private static String timeToString(LocalDateTime time) {
        return GraphQLCustomTypes.DATE_TIME_FORMAT.format(time);
    }

    private LocalDateTime time(int day) {
        return timeZero.plusDays(day);
    }

    private Operation createOp(OperationType opType, User user, Labware src, Labware dst, int day) {
        return createOp(opType, null, user, src, dst, day);
    }

    private Operation createOp(OperationType opType, StainType stainType, User user, Labware src, Labware dst, int day) {
        Operation op = new Operation(null, opType, null, List.of(), user);
        op.setStainType(stainType);
        op = opRepo.save(op);
        op.setPerformed(time(day));
        op = opRepo.save(op);
        Sample sample = dst.getFirstSlot().getSamples().get(0);
        actionRepo.save(new Action(null, op.getId(), src.getFirstSlot(), dst.getFirstSlot(), sample, sample));
        entityManager.flush();
        entityManager.refresh(op);
        return op;
    }


    private String getWorkProgressFile(String workNumber, List<Work.Status> statuses, List<String> workTypeNames)
            throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (workNumber!=null) {
            params.add("workNumber", workNumber);
        }
        if (statuses!=null) {
            statuses.forEach(status -> params.add("statuses", status.name()));
        }
        if (workTypeNames!=null) {
            workTypeNames.forEach(w -> params.add("workTypes", w));
        }
        return tester.getMockMvc().perform(MockMvcRequestBuilders.get("/work-progress")
                        .queryParams(params)).andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
