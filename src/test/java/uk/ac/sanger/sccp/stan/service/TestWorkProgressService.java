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
import uk.ac.sanger.sccp.stan.service.work.WorkEventService;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
    private ReleaseRepo mockReleaseRepo;
    private StainTypeRepo mockStainTypeRepo;
    private WorkEventService mockWorkEventService;
    private WorkProgressServiceImp service;
    @BeforeEach
    void setup() {
        mockWorkRepo = mock(WorkRepo.class);
        mockWorkTypeRepo = mock(WorkTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockReleaseRepo = mock(ReleaseRepo.class);
        mockStainTypeRepo = mock(StainTypeRepo.class);
        mockWorkEventService = mock(WorkEventService.class);

        service = spy(new WorkProgressServiceImp(mockWorkRepo, mockWorkTypeRepo, mockOpRepo, mockLwRepo, mockReleaseRepo, mockStainTypeRepo, mockWorkEventService));
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
            verify(service, never()).getProgressForWork(any(), any(), any(), any(), any(), any(), any());
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
        verify(service, times(works.size())).getProgressForWork(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull());
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
        Predicate<LabwareType> releaseFilter = x -> true;
        Sample sample = EntityFactory.getSample();
        Map<Integer, Labware> lwIdTypeMap = Map.of(4, EntityFactory.makeLabware(EntityFactory.getTubeType(), sample) );
        final Map<String,Set<String>> labwareTypeStainMap = Map.of("Visium ADH",Set.of("H&E"));
        doReturn(times).when(service).loadOpTimes(work, opTypeFilter, stainTypeFilter, lwTypeFilter,releaseFilter, lwIdTypeMap,labwareTypeStainMap);
        WorkProgress wp = service.getProgressForWork(work, opTypeFilter, stainTypeFilter, lwTypeFilter,releaseFilter, lwIdTypeMap,labwareTypeStainMap);
        assertSame(work, wp.getWork());
        assertThat(wp.getTimestamps()).containsExactlyInAnyOrder(
                new WorkProgressTimestamp("Section", sectionTime),
                new WorkProgressTimestamp("Stain", stainTime)
        );
        assertEquals(wp.getMostRecentOperation(), "Section");
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
        LabwareType lt3 = new LabwareType(3, "96 well plate", 12, 8, null, false);
        LabwareType lt4 = new LabwareType(4, "Visium ADH", 1, 1, null, false);

        final Map<String,Set<String>> labwareTypeStainMap = Map.of("visium adh", Set.of("h&e"));

        Predicate<OperationType> opTypeFilter = x -> (x==sectionType || x==stainOpType || x==rinOpType || x==dv200OpType);
        Predicate<StainType> stainTypeFilter = x -> (x==st1 || x==st2);
        Predicate<LabwareType> lwTypeFilter = x -> (x==lt1 || x==lt2);
        Predicate<LabwareType> releaseFilter = x-> (x==lt3);
        Map<Integer, Labware> lwIdLabwareMap = new HashMap<>();

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
                    op.setActions(List.of());
                    return op;
                }).collect(toList());

        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt1, sample);
        Labware lw2 = EntityFactory.makeLabware(lt2, sample);
        Labware lw3 = EntityFactory.makeLabware(lt3, sample);
        lw3.setReleased(true);
        Labware lw4 = EntityFactory.makeLabware(lt4, sample);
        Labware[][] opLw = {
                null, null, null, null, null, {lw1}, {lw1, lw2}, {lw2}, {lw3},{lw4}, null, null,
        };

        IntStream.range(0, ops.size()).forEach(i -> {
            Labware[] lts = opLw[i];
            doReturn((lts==null ? Set.of() : Set.of(lts)))
                    .when(service).opLabwares(same(ops.get(i)), same(lwIdLabwareMap));
        });

        Map<Integer, List<StainType>> opStainTypes = new HashMap<>(ops.size());
        for (int i = 0; i < ops.size(); ++i) {
            StainType st = stainTypes[i];
            if (st!=null) {
                opStainTypes.put(ops.get(i).getId(), List.of(st));
            }
        }

        when(mockStainTypeRepo.loadOperationStainTypes(any())).thenReturn(opStainTypes);
        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        Work work = workWithId(17);
        work.setOperationIds(opIds);
        when(mockOpRepo.findAllById(opIds)).thenReturn(ops);

        /*Mock findAllByLabwareIdIn method in Release Repo to return labware lw3*/
        Release release = new Release();
        release.setId(5);
        release.setLabware(lw3);
        release.setReleased(times[8]);

        when(mockReleaseRepo.findAllByLabwareIdIn(any()))
                .thenReturn(List.of(release));

        doAnswer(invocation -> {
            Map<String, LocalDateTime> opTimes = invocation.getArgument(0);
            opTimes.put("Release 96 well plate", times[8]);
            return null;
        }).when(service).loadReleases(any(), any(), any(), any());

        var result = service.loadOpTimes(work, opTypeFilter, stainTypeFilter, lwTypeFilter,
                releaseFilter, lwIdLabwareMap, labwareTypeStainMap);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                sectionType.getName(), times[3],
                stainOpType.getName(), times[9],
                "RNAscope/IHC stain", times[6],
                "Stain "+lt1.getName(), times[6],
                "Stain "+lt2.getName(), times[7],
                lt4.getName()+" "+st4.getName()+" stain", times[9],
                "Analysis", times[11],
                "Release "+lt3.getName(), times[8]
        ));

        verify(service).loadReleases(any(), eq(ops), same(releaseFilter), eq(lwIdLabwareMap));
        verify(mockStainTypeRepo).loadOperationStainTypes(work.getOperationIds());
    }

    @Test
    public void testOpLabwares() {
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
        Map<Integer, Labware> lwIdToLabware = Map.of();
        dests.forEach(lw ->
                doReturn(lw).when(service).getLabware(eq(lw.getId()), same(lwIdToLabware))
        );
        assertThat(service.opLabwares(op, lwIdToLabware)).containsExactlyInAnyOrder(lw1A, lw1B, lw2);
        dests.forEach(lw -> verify(service).getLabware(eq(lw.getId()), same(lwIdToLabware)));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetLabware(boolean cached) {
        Labware lw = EntityFactory.getTube();
        Integer id = lw.getId();
        Map<Integer, Labware> cache;
        if (cached) {
            cache = Map.of(id, lw);
        } else {
            cache = new HashMap<>(1);
            when(mockLwRepo.getById(id)).thenReturn(lw);
        }
        assertSame(lw, service.getLabware(id, cache));
        if (cached) {
            verifyNoInteractions(mockLwRepo);
        } else {
            assertSame(lw, cache.get(id));
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

    @Test
    public void testLoadReleases() {
        Labware lw1 = EntityFactory.getTube();
        Sample sample = EntityFactory.getSample();
        LabwareType includedLwType = new LabwareType(500, "Tub", 1, 1, EntityFactory.getLabelType(), false);
        Labware lw2 = EntityFactory.makeLabware(includedLwType, sample);
        List<Action> op1Actions = List.of(
                new Action(100, 10, lw1.getFirstSlot(), lw1.getFirstSlot(), sample, sample)
        );
        Operation op1 = new Operation(10, null, null, op1Actions, null);
        List<Action> op2Actions = List.of(
                new Action(110, 11, lw1.getFirstSlot(), lw2.getFirstSlot(), sample, sample)
        );
        Operation op2 = new Operation(11, null, null, op2Actions, null);

        Predicate<LabwareType> lwTypeFilter = lt -> lt.getName().equalsIgnoreCase("Tub");
        Map<Integer, Labware> labwareIdCache = Map.of(lw1.getId(), lw1, lw2.getId(), lw2);

        Release release = new Release();
        release.setLabware(lw2);
        release.setReleased(LocalDateTime.of(2022,4,19,9,50));

        when(mockReleaseRepo.findAllByLabwareIdIn(any())).thenReturn(List.of(release));
        Map<String, LocalDateTime> opTimes = new HashMap<>(1);

        service.loadReleases(opTimes, List.of(op1, op2), lwTypeFilter, labwareIdCache);

        assertThat(opTimes).hasSize(1).containsEntry("Release Tub", release.getReleased());

        verify(mockReleaseRepo).findAllByLabwareIdIn(Set.of(lw2.getId()));
    }

    @Test
    public void testGetMostRecentOperation() {
        List<WorkProgressTimestamp> wpts = new ArrayList<>(List.of(
                new WorkProgressTimestamp("Stain", LocalDateTime.of(2021, 9, 22, 12, 0)),
                new WorkProgressTimestamp("Section", LocalDateTime.of(2021, 9, 22, 10, 0)),
                new WorkProgressTimestamp("Analysis", LocalDateTime.of(2022, 1, 22, 12, 0))
        ));
        List<WorkProgressTimestamp> wpt = new ArrayList<>(List.of(wpts.get(0)));

        assertEquals(service.getMostRecentOperation(wpts),"Analysis");
        assertEquals(service.getMostRecentOperation(wpt),"Stain");
        assertNull(service.getMostRecentOperation(List.of()));
    }

    @Test
    public void testGetWorkComment() {
        Work workA = new Work(1, "SGP1", null, null, null, null, Status.active);
        Work workC = new Work(2, "SGP2", null, null, null, null, Status.completed);
        Work workF = new Work(3, "SGP3", null, null, null, null, Status.failed);
        Work workP = new Work(4, "SGP4", null, null, null, null, Status.paused);
        Work workW = new Work(5, "SGP5", null, null, null, null, Status.withdrawn);

        Comment failedComment = new Comment(1, "This work failed", "work status");
        Comment pausedComment = new Comment(2, "This work is paused", "work status");
        Comment withdrawnComment = new Comment(3, "This work is withdrawn", "work status");

        WorkEvent eventF = new WorkEvent(workF, WorkEvent.Type.fail, null, failedComment);
        WorkEvent eventP = new WorkEvent(workP, WorkEvent.Type.pause, null, pausedComment);
        WorkEvent eventW = new WorkEvent(workW, WorkEvent.Type.withdraw, null, withdrawnComment);

        when(mockWorkEventService.loadLatestEvents(List.of(workF.getId()))).thenReturn(Map.of(workF.getId(), eventF));
        when(mockWorkEventService.loadLatestEvents(List.of(workP.getId()))).thenReturn(Map.of(workP.getId(), eventP));
        when(mockWorkEventService.loadLatestEvents(List.of(workW.getId()))).thenReturn(Map.of(workW.getId(), eventW));

        assertEquals(service.getWorkComment(workA),null);
        assertEquals(service.getWorkComment(workC),null);
        assertEquals(service.getWorkComment(workF),"This work failed");
        assertEquals(service.getWorkComment(workP),"This work is paused");
        assertEquals(service.getWorkComment(workW),"This work is withdrawn");

    }

    private static Work workWithId(int id) {
        Work work = new Work();
        work.setId(id);
        return work;
    }

}
