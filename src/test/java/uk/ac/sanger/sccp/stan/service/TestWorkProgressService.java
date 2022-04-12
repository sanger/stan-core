package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.request.WorkProgress.WorkProgressTimestamp;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link WorkProgressServiceImp}
 * @author dr6
 */
public class TestWorkProgressService {
    private WorkRepo mockWorkRepo;
    private WorkTypeRepo mockWorkTypeRepo;
    private OperationRepo mockOpRepo;
    private LabwareRepo mockLwRepo;
    private StainTypeRepo mockStainTypeRepo;

    private WorkProgressServiceImp service;

    @BeforeEach
    void setup() {
        mockWorkRepo = mock(WorkRepo.class);
        mockWorkTypeRepo = mock(WorkTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);

        service = spy(new WorkProgressServiceImp(mockWorkRepo, mockWorkTypeRepo, mockOpRepo, mockLwRepo));
    }

    @ParameterizedTest
    @MethodSource("getProgressWithWorkNumberArgs")
    public void testGetProgressWithWorkNumber(String workNumber, Work work, String workTypeName, WorkType workType,
                                             Status status, List<Work> works, String expectedError) {
        if (work==null) {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenThrow(new EntityNotFoundException("Unknown work number: "+workNumber));
        } else {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        }
        List<String> workTypeNames = (workTypeName==null ? null : workTypeName.isEmpty() ? List.of() : List.of(workTypeName));
        List<Status> statuses = (status==null ? null : List.of(status));
        mockWorkType(workTypeName, workType);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.getProgress(workNumber, workTypeNames, statuses)))
                    .hasMessage(expectedError);
            verify(service, never()).getProgressForWork(any(), any(), any(), any(), any(),any());
        } else {
            List<WorkProgress> wps = service.getProgress(workNumber, workTypeNames, statuses);
            verifyProgress(wps, works);
        }
    }

    static Stream<Arguments> getProgressWithWorkNumberArgs() {
        Work work = workWithId(6);
        WorkType wt1 = new WorkType(2, "California");
        String wtn = wt1.getName();
        WorkType wt2 = new WorkType(3, "Colorado");
        work.setWorkType(wt1);
        work.setStatus(Status.active);
        String wn = "SGP1";
        work.setWorkNumber(wn);
        List<Work> works = List.of(work);

        return Arrays.stream(new Object[][] {
                {wn, work, null, null, null, works, null},
                {wn, work, null, null, Status.active, works, null},
                {wn, work, "", null, Status.active, List.of(), null},
                {wn, work, null, null, Status.paused, List.of(), null},
                {wn, work, wtn, wt1, null, works, null},
                {wn, work, wtn, wt1, Status.active, works, null},
                {wn, work, wtn, wt1, Status.paused, List.of(), null},
                {wn, work, "Colorado", wt2, null, List.of(), null},
                {wn, work, "Colorado", wt2, Status.active, List.of(), null},
                {wn, work, "Colorado", wt2, Status.paused, List.of(), null},

                {"SGP404", null, null, null, null,  null, "Unknown work number: SGP404"},
                {wn, work, "France", null, null, null, "Unknown work types: [France]"},
                {wn, work, "France", null, Status.paused, null, "Unknown work types: [France]"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getProgressWithWorkTypeNamesArgs")
    public void testGetProgressWithWorkTypeNames(String workTypeName, WorkType workType, Status status,
                                                List<Work> works, List<Work> expectedWorks, String expectedError) {
        mockWorkType(workTypeName, workType);
        List<String> workTypeNames = workTypeName==null ? null : workTypeName.isEmpty() ? List.of() : List.of(workTypeName);
        List<Status> statuses = status==null ? null : List.of(status);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class,
                    () -> service.getProgress(null, workTypeNames, statuses)))
                    .hasMessage(expectedError);
        } else if (workType!=null) {
            when(mockWorkRepo.findAllByWorkTypeIn(List.of(workType))).thenReturn(works);
            List<WorkProgress> wps = service.getProgress(null, workTypeNames, statuses);
            verifyProgress(wps, expectedWorks);
        } else {
            List<WorkProgress> wps = service.getProgress(null, workTypeNames, statuses);
            assertThat(wps).isEmpty();
        }
    }

    static Stream<Arguments> getProgressWithWorkTypeNamesArgs() {
        WorkType wt = new WorkType(2, "California");
        List<Work> works = List.of(workWithId(1), workWithId(2));
        works.get(0).setStatus(Status.active);
        works.get(1).setStatus(Status.paused);

        return Arrays.stream(new Object[][] {
                {"California", wt, null, works, works, null},
                {"", null, null, works, null, null},
                {"California", wt, Status.active, works, List.of(works.get(0)), null},
                {"France", null, null, null, null, "Unknown work types: [France]"},
        }).map(Arguments::of);
    }

    @Test
    public void testGetProgressWithStatuses() {
        List<Work> works = List.of(workWithId(1), workWithId(2));
        when(mockWorkRepo.findAllByStatusIn(List.of(Status.active, Status.unstarted))).thenReturn(works);
        List<WorkProgress> wps = service.getProgress(null, null, List.of(Status.active, Status.unstarted));
        verifyProgress(wps, works);
    }

    @Test
    public void testGetProgressWithEmptyListOfStatuses() {
        List<WorkProgress> wps = service.getProgress(null, null, List.of());
        assertThat(wps).isEmpty();
    }

    @Test
    public void testGetProgressWithNoFilter() {
        List<Work> works = List.of(workWithId(1), workWithId(2));
        when(mockWorkRepo.findAll()).thenReturn(works);
        List<WorkProgress> wps = service.getProgress(null, null, null);
        verifyProgress(wps, works);
    }

    private void mockWorkType(String workTypeName, WorkType workType) {
        if (workType != null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
            when(mockWorkTypeRepo.getAllByNameIn(List.of(workTypeName))).thenReturn(List.of(workType));
        } else if (workTypeName!=null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenThrow(new EntityNotFoundException("Unknown work type: "+workTypeName));
            final List<String> workTypeNames = List.of(workTypeName);
            when(mockWorkTypeRepo.getAllByNameIn(workTypeNames)).thenThrow(new EntityNotFoundException("Unknown work types: "+workTypeNames));
        }
    }

    private void verifyProgress(List<WorkProgress> wps, List<Work> works) {
        assertThat(wps.stream().map(WorkProgress::getWork)).containsExactlyElementsOf(works);
        verify(service, times(works.size())).getProgressForWork(notNull(), notNull(), notNull(), notNull(), notNull(),notNull());
    }

    @Test
    public void testGetProgressForWork() {
        Work work = workWithId(17);
        final LocalDateTime sectionTime = LocalDateTime.of(2021, 9, 23, 11, 0);
        final LocalDateTime stainTime = LocalDateTime.of(2021, 9, 22, 12, 0);
        Map<String, LocalDateTime> times = Map.of("Section", sectionTime,"Stain", stainTime);
        Predicate<OperationType> opTypeFilter = x -> true;
        Predicate<StainType> stainTypeFilter = x -> true;
        Predicate<LabwareType> lwTypeFilter = x -> true;
        Map<Integer, LabwareType> lwIdTypeMap = Map.of(4, EntityFactory.getTubeType());
        final Map<String,Set<String>> labwareTypeStainMap = Map.of("Visium ADH",Set.of("H&E"));
        doReturn(times).when(service).loadOpTimes(work, opTypeFilter, stainTypeFilter, lwTypeFilter, lwIdTypeMap,labwareTypeStainMap);
        WorkProgress wp = service.getProgressForWork(work, opTypeFilter, stainTypeFilter, lwTypeFilter, lwIdTypeMap,labwareTypeStainMap);
        assertSame(work, wp.getWork());
        assertThat(wp.getTimestamps()).containsExactlyInAnyOrder(
                new WorkProgressTimestamp("Section", sectionTime),
                new WorkProgressTimestamp("Stain", stainTime)
        );
    }

    @Test
    public void testLoadOpTimes() {
        OperationType sectionType = EntityFactory.makeOperationType( "Section", null, OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType stainOpType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.STAIN, OperationTypeFlag.IN_PLACE);
        OperationType rinOpType = EntityFactory.makeOperationType("RIN analysis", null, OperationTypeFlag.ANALYSIS);
        OperationType dv200OpType = EntityFactory.makeOperationType("DV200 analysis", null, OperationTypeFlag.ANALYSIS);
        OperationType otherType = EntityFactory.makeOperationType("Bananas", null);

        StainType st1 = new StainType(1, "RNAscope");
        StainType st2 = new StainType(2, "IHC");
        StainType st3 = new StainType(3, "Rhubarb");
        StainType st4 = new StainType(4, "H&E");

        LabwareType lt1 = new LabwareType(1, "jar", 1, 1, null, false);
        LabwareType lt2 = new LabwareType(2, "box", 1, 1, null, false);
        LabwareType lt3 = new LabwareType(3, "jug", 1, 1, null, false);
        LabwareType lt4 = new LabwareType(4, "Visium ADH", 1, 1, null, false);


        final Map<String,Set<String>> labwareTypeStainMap = Map.of("Visium ADH",Set.of("H&E"));

        Predicate<OperationType> opTypeFilter = x -> (x==sectionType || x==stainOpType || x==rinOpType || x==dv200OpType);
        Predicate<StainType> stainTypeFilter = x -> (x==st1 || x==st2);
        Predicate<LabwareType> lwTypeFilter = x -> (x==lt1 || x==lt2);
        Map<Integer, LabwareType> lwIdTypeMap = new HashMap<>();

        OperationType[] opTypes = { sectionType, stainOpType, otherType, sectionType, sectionType,
                stainOpType, stainOpType, stainOpType, stainOpType,stainOpType, rinOpType, dv200OpType };
        StainType[] stainTypes = {null, st1, null, null, null, st1, st2, st3, st3,st4, null, null};
        LocalDateTime[] times = IntStream.of(10,11,12,13,9, 14, 15, 16, 17, 18, 19,20)
                .mapToObj(d -> LocalDateTime.of(2021,9, d, 12,0))
                .toArray(LocalDateTime[]::new);
        List<Operation> ops = IntStream.range(0, opTypes.length)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(5+i);
                    op.setOperationType(opTypes[i]);
                    op.setPerformed(times[i]);
                    op.setStainType(stainTypes[i]);
                    return op;
                }).collect(toList());

        LabwareType[][] opLwTypes = {
                null, null, null, null, null, {lt1}, {lt1, lt2}, {lt2}, {lt3},{lt4}, null, null,
        };

        IntStream.range(0, ops.size()).forEach(i -> {
            LabwareType[] lts = opLwTypes[i];
            doReturn((lts==null ? Set.of() : Set.of(lts)))
                    .when(service).opLabwareTypes(same(ops.get(i)), same(lwIdTypeMap));
        });

        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        Work work = workWithId(17);
        work.setOperationIds(opIds);
        when(mockOpRepo.findAllById(opIds)).thenReturn(ops);

        var result = service.loadOpTimes(work, opTypeFilter, stainTypeFilter, lwTypeFilter, lwIdTypeMap,labwareTypeStainMap);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                sectionType.getName(), times[3],
                stainOpType.getName(), times[9],
                "RNAscope/IHC stain", times[6],
                "Stain "+lt1.getName(), times[6],
                "Stain "+lt2.getName(), times[7],
                lt4.getName()+" "+st4.getName()+" stain", times[9],
                "Analysis", times[11]
        ));
    }

    @Test
    public void testOpLabwareTypes() {
        LabwareType lt0 = EntityFactory.makeLabwareType(1,1);
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1);
        LabwareType lt2 = EntityFactory.makeLabwareType(1,1);
        Sample sample = EntityFactory.getSample();
        Labware lw0 = EntityFactory.makeLabware(lt0, sample);
        Labware lw1A = EntityFactory.makeLabware(lt1, sample);
        Labware lw1B = EntityFactory.makeLabware(lt1, sample);
        Labware lw2 = EntityFactory.makeLabware(lt2, sample);
        List<Labware> dests = List.of(lw1A, lw1B, lw2);
        OperationType opType = EntityFactory.makeOperationType("Splat", null);
        Operation op = EntityFactory.makeOpForLabware(opType, List.of(lw0), dests);
        Map<Integer, LabwareType> lwIdToType = Map.of();
        dests.forEach(lw ->
                doReturn(lw.getLabwareType()).when(service).getLabwareType(eq(lw.getId()), same(lwIdToType))
        );
        assertThat(service.opLabwareTypes(op, lwIdToType)).containsExactlyInAnyOrder(lt1, lt2);
        dests.forEach(lw -> verify(service).getLabwareType(eq(lw.getId()), same(lwIdToType)));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testGetLabwareType(boolean cached) {
        LabwareType lt = EntityFactory.makeLabwareType(1, 1);
        Map<Integer, LabwareType> map = new HashMap<>(1);
        if (cached) {
            map.put(4, lt);
            assertSame(lt, service.getLabwareType(4, map));
            verifyNoInteractions(mockLwRepo);
            assertThat(map).containsExactly(Map.entry(4, lt));
        } else {
            Labware lw = EntityFactory.makeEmptyLabware(lt);
            final Integer id = lw.getId();
            when(mockLwRepo.getById(id)).thenReturn(lw);
            assertSame(lt, service.getLabwareType(id, map));
            assertThat(map).containsExactly(Map.entry(id, lt));
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,false",",true","1,true"})
    public void testAddTime(Integer diff, boolean shouldReplace) {
        LocalDateTime thisTime = LocalDateTime.of(2021,12,8, 10,30);
        Map<String, LocalDateTime> opTimes = new HashMap<>(1);
        final String key = "opkey";
        LocalDateTime savedTime;
        if (diff==null) {
            savedTime = null;
        } else {
            savedTime = thisTime.minusDays(diff);
            opTimes.put(key, savedTime);
        }
        assert shouldReplace || savedTime!=null;
        service.addTime(opTimes, key, thisTime);
        assertThat(opTimes).containsExactly(Map.entry(key, shouldReplace ? thisTime : savedTime));
    }

    private static Work workWithId(int id) {
        Work work = new Work();
        work.setId(id);
        return work;
    }

}
