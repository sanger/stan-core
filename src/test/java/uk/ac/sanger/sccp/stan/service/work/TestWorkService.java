package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.work.WorkService.WorkOp;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Tests {@link WorkServiceImp}
 * @author dr6
 */
public class TestWorkService {
    @Mock private ProjectRepo mockProjectRepo;
    @Mock private ProgramRepo mockProgramRepo;
    @Mock private CostCodeRepo mockCostCodeRepo;
    @Mock private WorkRepo mockWorkRepo;
    @Mock private LabwareRepo mockLwRepo;
    @Mock private OmeroProjectRepo mockOmeroProjectRepo;
    @Mock private DnapStudyRepo mockDnapStudyRepo;
    @Mock private WorkTypeRepo mockWorkTypeRepo;
    @Mock private ReleaseRecipientRepo mockReleaseRecipientRepo;
    @Mock private WorkEventRepo mockWorkEventRepo;
    @Mock private WorkEventService mockWorkEventService;
    @Mock private Validator<String> mockPriorityValidator;

    private AutoCloseable mocking;

    @InjectMocks
    private WorkServiceImp workService;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        workService = spy(workService);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,,",
            "1,,,",
            ",2,,",
            ",,3,",
            "-1,,,Number of blocks cannot be a negative number.",
            ",-2,,Number of slides cannot be a negative number.",
            ",,-3,Number of original samples cannot be a negative number.",
    })
    public void testCreateWork_numLabware(Integer numBlocks, Integer numSlides, Integer numOriginalSamples, String expectedErrorMessage) {
        String projectName = "Stargate";
        String code = "S1234";
        String workTypeName = "Drywalling";
        String workRequesterName = "test1";
        String progName = "Hello";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        Program prog = new Program(15, progName, true);
        when(mockProgramRepo.getByName(progName)).thenReturn(prog);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        WorkType workType = new WorkType(30, workTypeName);
        when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
        String prefix = "SGP";
        String workNumber = "SGP4000";
        when(mockWorkRepo.createNumber(prefix)).thenReturn(workNumber);
        User user = new User(1, "user1", User.Role.admin);
        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
        ReleaseRecipient workRequester = new ReleaseRecipient(30, workRequesterName);
        doReturn(workRequester).when(workService).findOrCreateRequester(user, workRequesterName);

        if (expectedErrorMessage==null) {
            Work result = workService.createWork(user, prefix, workTypeName, workRequesterName, projectName, progName, code, numBlocks, numSlides, numOriginalSamples, null, null);
            verify(workService).checkPrefix(prefix);
            verify(mockWorkRepo).createNumber(prefix);
            verify(mockWorkRepo).save(result);
            verify(mockWorkEventService).recordEvent(user, result, WorkEvent.Type.create, null);
            assertEquals(new Work(null, workNumber, workType, workRequester, project, prog, cc, Status.unstarted, numBlocks, numSlides, numOriginalSamples, null, null, null), result);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.createWork(user, prefix, workTypeName, workRequesterName, projectName,
                    progName, code, numBlocks, numSlides, numOriginalSamples, null, null))).hasMessage(expectedErrorMessage);
            verifyNoInteractions(mockWorkRepo);
        }
    }

    @ParameterizedTest
    @CsvSource({
            ", Omero project not found.",
            "true,",
            "false,Omero project OM_PROJ is disabled.",
    })
    public void testCreateWork_omeroProject(Boolean enabled, String expectedErrorMessage) {
        final String omeroName = "OM_PROJ";
        OmeroProject omero;
        if (enabled==null) {
            omero = null;
            when(mockOmeroProjectRepo.getByName(omeroName)).thenThrow(new EntityNotFoundException(expectedErrorMessage));
        } else {
            omero = new OmeroProject(100, omeroName, enabled);
            when(mockOmeroProjectRepo.getByName(omeroName)).thenReturn(omero);
        }
        String projectName = "Stargate";
        String code = "S1234";
        String workTypeName = "Drywalling";
        String workRequesterName = "test1";
        String progName = "Hello";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        Program prog = new Program(15, progName, true);
        when(mockProgramRepo.getByName(progName)).thenReturn(prog);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        WorkType workType = new WorkType(30, workTypeName);
        when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
        String prefix = "SGP";
        String workNumber = "SGP4000";
        when(mockWorkRepo.createNumber(prefix)).thenReturn(workNumber);
        User user = new User(1, "user1", User.Role.admin);
        when(mockReleaseRecipientRepo.findByUsername(workRequesterName)).thenReturn(Optional.of(new ReleaseRecipient(10, workRequesterName)));

        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(RuntimeException.class, () -> workService.createWork(user, prefix, workTypeName, workRequesterName, projectName, progName, code, null, null, null, omeroName, null)))
                    .hasMessage(expectedErrorMessage);
        } else {
            Work result = workService.createWork(user, prefix, workTypeName, workRequesterName, projectName, progName, code, null, null, null, omeroName, null);
            verify(mockWorkRepo).save(result);
            assertSame(omero, result.getOmeroProject());
        }
    }


    @ParameterizedTest
    @CsvSource({
            ", DNAP study not found.",
            "true,",
            "false, DNAP study is disabled: 123: S123",
    })
    public void testCreateWork_dnapStudy(Boolean enabled, String expectedErrorMessage) {
        final Integer ssId = 123;
        DnapStudy study;
        if (enabled==null) {
            study = null;
            when(mockDnapStudyRepo.getBySsId(ssId)).thenThrow(new EntityNotFoundException(expectedErrorMessage));
        } else {
            study = new DnapStudy(20, ssId, "S123", enabled);
            when(mockDnapStudyRepo.getBySsId(ssId)).thenReturn(study);
        }
        String projectName = "Stargate";
        String code = "S1234";
        String workTypeName = "Drywalling";
        String workRequesterName = "test1";
        String progName = "Hello";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        Program prog = new Program(15, progName, true);
        when(mockProgramRepo.getByName(progName)).thenReturn(prog);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        WorkType workType = new WorkType(30, workTypeName);
        when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
        String prefix = "SGP";
        String workNumber = "SGP4000";
        when(mockWorkRepo.createNumber(prefix)).thenReturn(workNumber);
        User user = new User(1, "user1", User.Role.admin);
        when(mockReleaseRecipientRepo.findByUsername(workRequesterName)).thenReturn(Optional.of(new ReleaseRecipient(10, workRequesterName)));

        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(RuntimeException.class, () -> workService.createWork(user, prefix, workTypeName, workRequesterName, projectName, progName, code, null, null, null, null, ssId)))
                    .hasMessage(expectedErrorMessage);
        } else {
            Work result = workService.createWork(user, prefix, workTypeName, workRequesterName, projectName, progName, code, null, null, null, null, ssId);
            verify(mockWorkRepo).save(result);
            assertSame(study, result.getDnapStudy());
        }
    }

    @ParameterizedTest
    @CsvSource({", user1, fred, true",
            ", user1, user1, false",
            ", user1, USER1, false",
            "No work requester specified., user1,,false",
            "Unknown requester: \"Bananas\", user1, Bananas, false",
    })
    public void testFindOrCreateRequester(String expectedErrorMessage, String username, String recipientName, boolean exists) {
        ReleaseRecipient rec = (expectedErrorMessage==null ? new ReleaseRecipient(10, recipientName) : null);
        if (rec!=null && exists) {
            when(mockReleaseRecipientRepo.findByUsername(recipientName)).thenReturn(Optional.of(rec));
        } else if (recipientName!=null) {
            when(mockReleaseRecipientRepo.findByUsername(recipientName)).thenReturn(Optional.empty());
        }

        if (!exists && expectedErrorMessage==null) {
            when(mockReleaseRecipientRepo.save(any())).thenReturn(rec);
        }

        User user = new User(200, username, User.Role.enduser);

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.findOrCreateRequester(user, recipientName)))
                    .hasMessage(expectedErrorMessage);
        } else {
            assertSame(rec, workService.findOrCreateRequester(user, recipientName));
        }
        if (rec!=null && !exists) {
            verify(mockReleaseRecipientRepo).save(new ReleaseRecipient(null, user.getUsername()));
        } else {
            verify(mockReleaseRecipientRepo, never()).save(any());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true,active,,completed,,",
            "false,active,,paused,,",
            "true,active,,paused,,20",
            "true,active,B4,paused,B4,20",
            "false,completed,B4,failed,B4,20",
            "true,active,B4,failed,,20",
            "true,active,B4,completed,,",
            "true,active,B4,withdrawn,,20"
    })
    public void testUpdateStatus(boolean legal, Status oldStatus, String oldPriority, Status newStatus,
                                 String expectedPriority, Integer commentId) {
        User user = new User(1, "user1", User.Role.admin);
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, null, null, oldStatus);
        work.setPriority(oldPriority);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        if (!legal) {
            doThrow(IllegalArgumentException.class).when(mockWorkEventService).recordStatusChange(any(), any(), any(), any());
            assertThrows(IllegalArgumentException.class, () -> workService.updateStatus(user, workNumber, newStatus, commentId));
            verify(mockWorkRepo, never()).save(any());
            return;
        }
        Comment comment = (commentId==null ? null : new Comment(commentId, "Custard", "Alabama"));
        WorkEvent event = new WorkEvent(100, work, null, null, comment, null);
        when(mockWorkEventService.recordStatusChange(any(), any(), any(), any())).thenReturn(event);
        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
        WorkWithComment wc = workService.updateStatus(user, workNumber, newStatus, commentId);
        assertSame(work, wc.getWork());
        assertEquals(comment!=null ? comment.getText() : null, wc.getComment());
        verify(mockWorkRepo).save(work);
        verify(mockWorkEventService).recordStatusChange(user, work, newStatus, commentId);
        assertEquals(newStatus, work.getStatus());
        assertEquals(expectedPriority, work.getPriority());
    }


    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",1,",
            "1,2,",
            "2,1,",
            ",-2,Number of blocks cannot be a negative number.",
            "2,-1,Number of blocks cannot be a negative number.",
    })
    public void testUpdateNumBlocks(Integer oldValue, Integer newValue, String expectedErrorMessage) {
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, null, null, Status.active);
        work.setNumBlocks(oldValue);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        User user = EntityFactory.getUser();

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.updateWorkNumBlocks(user, workNumber, newValue)))
                    .hasMessage(expectedErrorMessage);
            verify(mockWorkRepo, never()).save(any());
        } else if (Objects.equals(oldValue, newValue)) {
            assertSame(work, workService.updateWorkNumBlocks(user, workNumber, newValue));
            assertEquals(work.getNumBlocks(), newValue);
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateWorkNumBlocks(user, workNumber, newValue));
            verify(mockWorkRepo).save(work);
            assertEquals(work.getNumBlocks(), newValue);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",1,",
            "1,2,",
            "2,1,",
            ",-2,Number of slides cannot be a negative number.",
            "2,-1,Number of slides cannot be a negative number.",
    })
    public void testUpdateNumSlides(Integer oldValue, Integer newValue, String expectedErrorMessage) {
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, null, null, Status.active);
        work.setNumBlocks(oldValue);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        User user = EntityFactory.getUser();

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.updateWorkNumSlides(user, workNumber, newValue)))
                    .hasMessage(expectedErrorMessage);
            verify(mockWorkRepo, never()).save(any());
        } else if (Objects.equals(oldValue, newValue)) {
            assertSame(work, workService.updateWorkNumSlides(user, workNumber, newValue));
            assertEquals(work.getNumSlides(), newValue);
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateWorkNumSlides(user, workNumber, newValue));
            verify(mockWorkRepo).save(work);
            assertEquals(work.getNumSlides(), newValue);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",1,",
            "1,2,",
            "2,1,",
            ",-2,Number of original samples cannot be a negative number.",
            "2,-1,Number of original samples cannot be a negative number.",
    })
    public void testUpdateNumOriginalSamples(Integer oldValue, Integer newValue, String expectedErrorMessage) {
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, null, null, Status.active);
        work.setNumOriginalSamples(oldValue);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        User user = EntityFactory.getUser();

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.updateWorkNumOriginalSamples(user, workNumber, newValue)))
                    .hasMessage(expectedErrorMessage);
            verify(mockWorkRepo, never()).save(any());
        } else if (Objects.equals(oldValue, newValue)) {
            assertSame(work, workService.updateWorkNumOriginalSamples(user, workNumber, newValue));
            assertEquals(work.getNumOriginalSamples(), newValue);
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateWorkNumOriginalSamples(user, workNumber, newValue));
            assertEquals(work.getNumOriginalSamples(), newValue);
            verify(mockWorkRepo).save(work);
        }
    }

    @ParameterizedTest
    @CsvSource({
            ",,active",
            ",,failed",
            ",A2,active",
            ",a2,active",
            ",a2,failed",
            ",a2,completed",
            ",a2,withdrawn",
            "A2,A2,active",
            "A2,B3,active",
            "A2,a2,active",
            "A2,B3,failed",
            "A2,B3,completed",
            "A2,B3,withdrawn",
            "A2,,active",
            "A2,!4,active",
            ",!4,active",
    })
    public void testUpdateWorkPriority(String oldPriority, String newPriority, Status status) {
        Work work = new Work(10, "SGP4000", null, null, null, null, null, status);
        work.setPriority(oldPriority);
        String exMsg;
        if (newPriority!=null && newPriority.indexOf('!')>=0) {
            exMsg = "Bad priority";
            doThrow(new IllegalArgumentException(exMsg)).when(mockPriorityValidator).checkArgument(newPriority);
        } else if (work.isClosed() && newPriority!=null) {
            exMsg = "Cannot set a new priority on "+status+" work.";
        } else {
            exMsg = null;
        }
        User user = EntityFactory.getUser();
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        if (exMsg!=null) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> workService.updateWorkPriority(user, work.getWorkNumber(), newPriority));
            assertThat(thrown).hasMessage(exMsg);
        } else {
            workService.updateWorkPriority(user, work.getWorkNumber(), newPriority);
            String newPrioritySan = (newPriority==null ? null : newPriority.toUpperCase());
            if (!Objects.equals(oldPriority, newPrioritySan)) {
                verify(mockWorkRepo).save(work);
            } else {
                verify(mockWorkRepo, never()).save(any());
            }
            assertEquals(newPrioritySan, work.getPriority());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,Alpha,Beta,",
            "true,true,Alpha,Alpha,",
            "false,,,,No such work.",
            "true,,Alpha,Beta,No such omero.",
            "true,false,Alpha,Beta,Omero project Beta is disabled.",
            "true,true,Alpha,,",
    })
    public void testUpdateWorkOmeroProject(boolean workExists, Boolean omeroEnabled,
                                           String oldOmero, String newOmero, String expectedError) {
        boolean change = !(oldOmero==null ? newOmero==null : oldOmero.equalsIgnoreCase(newOmero));
        String workNumber = "SGP100";
        Work work;
        if (!workExists) {
            work = null;
            EntityNotFoundException ex = new EntityNotFoundException(expectedError);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenThrow(ex);
        } else {
            work = new Work(100, workNumber, null, null, null, null, null, Status.active);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
            OmeroProject omero;
            if (newOmero!=null) {
                if (omeroEnabled==null) {
                    omero = null;
                    EntityNotFoundException ex = new EntityNotFoundException(expectedError);
                    when(mockOmeroProjectRepo.getByName(newOmero)).thenThrow(ex);
                } else {
                    omero = new OmeroProject(50, newOmero, omeroEnabled);
                    when(mockOmeroProjectRepo.getByName(newOmero)).thenReturn(omero);
                }

            } else {
                omero = null;
            }
            if (oldOmero!=null) {
                work.setOmeroProject(change ? new OmeroProject(10, oldOmero, true) : omero);
            }
        }

        User user = EntityFactory.getUser();

        if (expectedError!=null) {
            assertThat(assertThrows(RuntimeException.class, () -> workService.updateWorkOmeroProject(user, workNumber, newOmero)))
                    .hasMessage(expectedError);
            verify(mockWorkRepo, never()).save(any());
        } else {
            assert work != null;
            when(mockWorkRepo.save(any())).thenReturn(work);
            assertSame(work, workService.updateWorkOmeroProject(user, workNumber, newOmero));
            if (newOmero==null) {
                assertNull(work.getOmeroProject());
            } else {
                assertNotNull(work.getOmeroProject());
                assertEquals(newOmero, work.getOmeroProject().getName());
            }
            if (change) {
                verify(mockWorkRepo).save(work);
            } else {
                verify(mockWorkRepo, never()).save(any());
            }
        }
    }


    @ParameterizedTest
    @CsvSource({
            "true,true,10,20,",
            "true,true,10,10,",
            "false,,,,No such work.",
            "true,,10,20,No such study.",
            "true,false,10,20,DNAP study is disabled: 20: Study20",
            "true,true,10,,",
    })
    public void testUpdateWorkDnapStudy(boolean workExists, Boolean studyEnabled,
                                           Integer oldId, Integer newId, String expectedError) {
        boolean change = !Objects.equals(oldId, newId);
        String workNumber = "SGP100";
        Work work;
        if (!workExists) {
            work = null;
            EntityNotFoundException ex = new EntityNotFoundException(expectedError);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenThrow(ex);
        } else {
            work = new Work(100, workNumber, null, null, null, null, null, Status.active);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
            DnapStudy study;
            if (newId!=null) {
                if (studyEnabled==null) {
                    study = null;
                    EntityNotFoundException ex = new EntityNotFoundException(expectedError);
                    when(mockDnapStudyRepo.getBySsId(newId)).thenThrow(ex);
                } else {
                    study = new DnapStudy(50, newId, "Study"+newId, studyEnabled);
                    when(mockDnapStudyRepo.getBySsId(newId)).thenReturn(study);
                }

            } else {
                study = null;
            }
            if (oldId!=null) {
                work.setDnapStudy(change ? new DnapStudy(10, oldId, "Custard", true) : study);
            }
        }

        User user = EntityFactory.getUser();

        if (expectedError!=null) {
            assertThat(assertThrows(RuntimeException.class, () -> workService.updateWorkDnapStudy(user, workNumber, newId)))
                    .hasMessage(expectedError);
            verify(mockWorkRepo, never()).save(any());
        } else {
            assert work != null;
            when(mockWorkRepo.save(any())).thenReturn(work);
            assertSame(work, workService.updateWorkDnapStudy(user, workNumber, newId));
            if (newId==null) {
                assertNull(work.getDnapStudy());
            } else {
                assertNotNull(work.getDnapStudy());
                assertEquals(newId, work.getDnapStudy().getSsId());
            }
            if (change) {
                verify(mockWorkRepo).save(work);
            } else {
                verify(mockWorkRepo, never()).save(any());
            }
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            "sgp, ",
            "SGP, ",
            "r&d, ",
            "R&D, ",
            ", No prefix supplied for work number.",
            "Bananas, Invalid work number prefix: \"Bananas\"",
    })
    public void testCheckPrefix(String prefix, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            workService.checkPrefix(prefix);
        } else {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> workService.checkPrefix(prefix));
            assertThat(ex).hasMessage(expectedErrorMessage);
        }
    }

    @ParameterizedTest
    @MethodSource("linkVariousArgs")
    public void testLinkVarious(Object arg, int numOps, String expectedErrorMsg) {
        String workNumber;
        Work work;
        if (arg instanceof Work) {
            work = (Work) arg;
            workNumber = work.getWorkNumber();
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        } else {
            workNumber = (String) arg;
            work = null;
            when(mockWorkRepo.getByWorkNumber(workNumber)).then(invocation -> {
                throw new EntityNotFoundException("Unknown work number.");
            });
        }

        List<Operation> ops = (numOps==0 ? List.of() : List.of(new Operation()));
        if (work==null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> workService.link(workNumber, ops)))
                    .hasMessage(expectedErrorMsg);
        } else if (expectedErrorMsg==null) {
            assertSame(arg, workService.link(workNumber, ops));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.link(workNumber, ops)))
                    .hasMessage(expectedErrorMsg);
        }
        verify(mockWorkRepo, never()).save(any());
    }

    static Stream<Arguments> linkVariousArgs() {
        Work activeWork = new Work(1, "SGP1001", null, null, null, null, null, Status.active);
        Work pausedWork = new Work(2, "R&D1002", null, null, null, null, null, Status.paused);
        Work completedWork = new Work(3, "SGP1003", null, null, null, null, null, Status.completed);
        Work failedWork = new Work(4, "SGP1004", null, null, null, null, null, Status.failed);
        Work withdrawnWork = new Work(4, "SGP1005", null, null, null, null, null, Status.withdrawn);
        return Arrays.stream(new Object[][] {
                { activeWork, 0, null },
                { pausedWork, 1, "R&D1002 cannot be used because it is paused." },
                { completedWork, 1, "SGP1003 cannot be used because it is completed." },
                { failedWork, 1, "SGP1004 cannot be used because it is failed." },
                { withdrawnWork, 1, "SGP1005 cannot be used because it is withdrawn." },
                { "SGP404", 1, "Unknown work number." },
        }).map(Arguments::of);
    }

    @Test
    public void testLinkSuccessful() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, sam1.getSection()+1, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sam1, sam2);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.getFirstSlot().getSamples().add(sam1);
        lw2.getFirstSlot().getSamples().add(sam2);
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);

        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Operation op1 = makeOp(opType, 10, lw0, lw1);
        Operation op2 = makeOp(opType, 11, lw0, lw2);

        Work work = new Work(50, "SGP5000", null, null, null, null, null, Status.active);

        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        work.setOperationIds(hashSetOf(1,2,3));
        work.setSampleSlotIds(hashSetOf(new SampleSlotId(sam1.getId(), 2)));

        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());

        assertEquals(work, workService.link(work.getWorkNumber(), List.of(op1, op2)));
        verify(mockWorkRepo).save(work);
        assertThat(work.getOperationIds()).containsExactlyInAnyOrder(1,2,3,10,11);
        assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrder(
                new SampleSlotId(sam1.getId(), 2),
                new SampleSlotId(sam1.getId(), lw1.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw1.getSlot(new Address(1,2)).getId()),
                new SampleSlotId(sam1.getId(), lw2.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw2.getFirstSlot().getId())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    public void testLinkMultiple_nothing(boolean anyOps) {
        List<Work> works;
        List<Operation> ops;
        if (anyOps) {
            works = List.of();
            ops = List.of(new Operation());
        } else {
            works = List.of(new Work());
            ops = List.of();
        }
        workService.link(works, ops);
        verifyNoInteractions(mockWorkRepo);
        verify(workService, never()).link(any(Work.class), any());
    }

    @Test
    public void testLinkMultiple_one() {
        Work work = new Work();
        work.setId(10);
        List<Operation> ops = List.of(new Operation(), new Operation());
        doReturn(work).when(workService).link(same(work), any());
        workService.link(List.of(work), ops);
        verify(workService).link(work, ops);
        verifyNoInteractions(mockWorkRepo);
    }

    @Test
    public void testLinkMultiple_unusable() {
        Status[] statuses = { Status.failed, Status.withdrawn, Status.active };
        List<Work> works = IntStream.range(0, statuses.length)
                .mapToObj(i -> {
                    Work work = new Work();
                    int id = i+1;
                    work.setId(id);
                    work.setWorkNumber("SGP"+id);
                    work.setStatus(statuses[i]);
                    return work;
                }).collect(toList());
        assertThat(
                assertThrows(IllegalArgumentException.class, () -> workService.link(works, List.of(new Operation())))
        ).hasMessage("Specified work cannot be used because it is not active: [SGP1, SGP2]");
    }

    @Test
    public void testLinkMultiple() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, sam1.getSection()+1, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sam1, sam2);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.getFirstSlot().getSamples().add(sam1);
        lw2.getFirstSlot().getSamples().add(sam2);
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);

        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Operation op1 = makeOp(opType, 10, lw0, lw1);
        Operation op2 = makeOp(opType, 11, lw0, lw2);
        final Integer existingOpId = 1;
        final SampleSlotId existingSsid = new SampleSlotId(3, 4);

        List<Work> works = IntStream.rangeClosed(51,52).mapToObj(i -> {
            Work w = new Work();
            w.setId(i);
            w.setWorkNumber("SGP"+i);
            w.setStatus(Status.active);
            w.setOperationIds(i==51 ? hashSetOf(existingOpId) : hashSetOf());
            w.setSampleSlotIds(i==51 ? hashSetOf(existingSsid) : hashSetOf());
            return w;
        }).collect(toList());
        workService.link(works, List.of(op1, op2));
        assertThat(works.get(0).getOperationIds()).containsExactlyInAnyOrder(existingOpId, 10, 11);
        assertThat(works.get(1).getOperationIds()).containsExactlyInAnyOrder(10, 11);
        assertThat(works.get(0).getSampleSlotIds()).containsExactlyInAnyOrder(
                Stream.concat(Stream.of(existingSsid),
                        Stream.concat(opSsids(op1), opSsids(op2)))
                        .toArray(SampleSlotId[]::new)
        );
        assertThat(works.get(1).getSampleSlotIds()).containsExactlyInAnyOrder(
                Stream.concat(opSsids(op1), opSsids(op2)).toArray(SampleSlotId[]::new)
        );

        verify(mockWorkRepo).saveAll(works);
    }

    static Stream<SampleSlotId> opSsids(Operation op) {
        return op.getActions().stream()
                .map(a -> new SampleSlotId(a.getSample().getId(), a.getDestination().getId()));
    }

    @Test
    public void testLinkReleases_inactive() {
        Work work = quickWork(Status.paused);
        Labware lw = EntityFactory.getTube();
        List<Release> releases = List.of(quickRelease(100, lw));
        assertThat(assertThrows(IllegalArgumentException.class, () -> workService.linkReleases(work, releases)))
                .hasMessage("Work SGP1 is not usable because it is paused.");
    }

    @Test
    public void testLinkReleases_none() {
        Work work = quickWork(Status.active);
        assertSame(work, workService.linkReleases(work, List.of()));
        verifyNoInteractions(mockWorkRepo);
    }

    @Test
    public void testLinkReleases() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw2 = EntityFactory.makeLabware(lt, sam1, sam2);
        lw2.getFirstSlot().addSample(sam2);
        Release rel1 = quickRelease(100, lw1);
        Release rel2 = quickRelease(101, lw2);
        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
        Work work = quickWork(Status.active);
        work.setReleaseIds(hashSetOf(1));
        work.setSampleSlotIds(hashSetOf(new SampleSlotId(2,3)));
        assertSame(work, workService.linkReleases(work, List.of(rel1, rel2)));
        verify(mockWorkRepo).save(work);
        assertThat(work.getReleaseIds()).containsExactlyInAnyOrder(1, 100, 101);
        assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrder(
                new SampleSlotId(2,3),
                new SampleSlotId(sam1.getId(), lw1.getFirstSlot().getId()),
                new SampleSlotId(sam1.getId(), lw2.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw2.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw2.getSlot(new Address(1,2)).getId())
        );
    }

    @Test
    public void testLinkWorkOps() {
        OperationType opType = EntityFactory.makeOperationType("Bananas", null);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,4)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        Operation[] ops = IntStream.range(0,lws.length)
                        .mapToObj(i -> makeOp(opType, 100+i, lws[i], lws[i]))
                .toArray(Operation[]::new);
        Work[] works = IntStream.range(20,23).mapToObj(i -> quickWork(i, Status.active)).toArray(Work[]::new);

        Stream<WorkOp> workOps = Stream.of(
                new WorkOp(works[0], ops[0]), new WorkOp(works[0], ops[1]),
                new WorkOp(works[1], ops[2]), new WorkOp(works[2], ops[3])
        );
        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());

        List<Work> result = workService.linkWorkOps(workOps);
        assertThat(result).containsExactlyInAnyOrder(works);
        verify(workService).link(works[0], List.of(ops[0], ops[1]));
        verify(workService).link(works[1], List.of(ops[2]));
        verify(workService).link(works[2], List.of(ops[3]));
    }

    static Work quickWork(Status status) {
        return quickWork(1, status);
    }

    static Work quickWork(Integer id, Status status) {
        Work work = new Work();
        work.setId(id);
        work.setWorkNumber("SGP"+id);
        work.setStatus(status);
        return work;
    }

    static Release quickRelease(Integer id, Labware lw) {
        Release rel = new Release();
        rel.setId(id);
        rel.setLabware(lw);
        return rel;
    }

    @ParameterizedTest
    @MethodSource("usableWorkArgs")
    public void testGetUsableWork(String workNumber, Status status, Class<? extends Exception> expectedExceptionType, String expectedErrorMessage) {
        if (expectedExceptionType==null) {
            Work work = new Work(14, workNumber, null, null, null, null, null, status);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
            assertSame(work, workService.getUsableWork(workNumber));
            return;
        }

        if (status==null) {
            when(mockWorkRepo.getByWorkNumber(workNumber))
                    .then(invocation -> {throw new EntityNotFoundException("Work number not recognised: "+repr(workNumber)); });
        } else {
            Work work = new Work(14, workNumber, null, null, null, null, null, status);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        }
        assertThat(assertThrows(expectedExceptionType, () -> workService.getUsableWork(workNumber)))
                .hasMessage(expectedErrorMessage);
    }

    @ParameterizedTest
    @MethodSource("getUsableWorkMapArgs")
    public void testGetUsableWorkMap(Collection<String> workNumbers, Collection<Work> works, String expectedError) {
        UCMap<Work> workMap = UCMap.from(works, Work::getWorkNumber);
        when(mockWorkRepo.findAllByWorkNumberIn(any())).then(invocation -> {
            Collection<String> wns = invocation.getArgument(0);
            return wns.stream()
                    .map(workMap::get)
                    .filter(Objects::nonNull)
                    .collect(toList());
        });
        if (expectedError!=null) {
            assertThat(assertThrows(RuntimeException.class, () -> workService.getUsableWorkMap(workNumbers)))
                    .hasMessage(expectedError);
            return;
        }
        assertEquals(workMap, workService.getUsableWorkMap(workNumbers));
    }

    static Stream<Arguments> getUsableWorkMapArgs() {
        Work work1 = quickWork(1, Status.paused);
        Work work2 = quickWork(2, Status.failed);
        Work work3 = quickWork(3, Status.active);
        Work work4 = quickWork(4, Status.active);
        return Arrays.stream(new Object[][] {
                { List.of("SGP3", "sgp4", "SGP3"), List.of(work3, work4), null },
                { List.of("SGP1", "SGP404", "SGP2"), List.of(work1, work2), "Unknown work number: [SGP404]"},
                { List.of("SGP1", "SGP404", "SGP405"), List.of(work1, work2), "Unknown work numbers: [SGP404, SGP405]"},
                { Arrays.asList("SGP1", "SGP2", null), List.of(work1, work2), "null given as work number."},
                { List.of("SGP3", "SGP1", "SGP2", "SGP4"), List.of(work1, work2, work3, work4), "Inactive work numbers: [SGP1, SGP2]"},
                { List.of(), List.of(), null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("usableWorkArgs")
    public void testValidateUsableWork(String workNumber, Status status, Class<? extends Exception> ignored, String expectedErrorMessage) {
        List<String> problems = new ArrayList<>(1);
        if (workNumber==null) {
            assertNull(workService.validateUsableWork(problems, null));
            assertThat(problems).containsExactly("Work number is not specified.");
            verifyNoInteractions(mockWorkRepo);
            return;
        }

        Optional<Work> optWork = Optional.ofNullable(status).map(st -> new Work(14, workNumber, null, null, null, null, null, st));
        when(mockWorkRepo.findByWorkNumber(workNumber)).thenReturn(optWork);
        assertSame(optWork.orElse(null), workService.validateUsableWork(problems, workNumber));

        if (expectedErrorMessage==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedErrorMessage);
        }
    }

    @ParameterizedTest
    @CsvSource({
            ",,[Not found]",
            "SGPX,,Work number not recognised: \"SGPX\"",
            "SGP1,active,",
            "sgp2,unstarted,",
            "Sgp3,paused,",
            "Sgp3,withdrawn,SGP3 cannot be used because it is withdrawn.",
            "Sgp3,failed,SGP3 cannot be used because it is failed.",
            "Sgp3,completed,SGP3 cannot be used because it is completed.",
    })
    public void testValidateOpenWork(String workNumber, Status status, String expectedErrorMessage) {
        List<String> problems = new ArrayList<>(1);
        if (nullOrEmpty(workNumber)) {
            assertNull(workService.validateUsableWork(problems, null));
            assertThat(problems).containsExactly("Work number is not specified.");
            verifyNoInteractions(mockWorkRepo);
            return;
        }
        Work work;
        if (status==null) {
            work = null;
        } else {
            work = quickWork(status);
            work.setWorkNumber(workNumber.toUpperCase());
        }
        when(mockWorkRepo.findByWorkNumber(workNumber)).thenReturn(Optional.ofNullable(work));
        assertSame(work, workService.validateOpenWork(problems, workNumber));
        verify(mockWorkRepo).findByWorkNumber(workNumber);
        assertProblem(problems, expectedErrorMessage);
    }

    @ParameterizedTest
    @CsvSource({
            ",,true,[Not found]",
            "SGPX,,true,Work number not recognised: \"SGPX\"",
            "SGP1,active,true,",
            "sgp2,unstarted,true,",
            "Sgp3,paused,true,",
            "Sgp3,withdrawn,true,SGP3 cannot be used because it is withdrawn.",
            "Sgp3,failed,true,SGP3 cannot be used because it is failed.",
            "Sgp3,completed,true,SGP3 cannot be used because it is completed.",
            "SGP1,active,false,",
            "sgp2,unstarted,false,SGP2 cannot be used because it is unstarted.",
            "Sgp3,paused,false,SGP3 cannot be used because it is paused.",
            "Sgp3,withdrawn,false,SGP3 cannot be used because it is withdrawn.",
            "Sgp3,failed,false,SGP3 cannot be used because it is failed.",
            "Sgp3,completed,false,SGP3 cannot be used because it is completed.",
    })
    public void testValidateWorkForOpType(String workNumber, Status status, boolean anyOpen, String expectedErrorMessage) {
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        if (anyOpen) {
            opType.setFlags(opType.getFlags()|OperationTypeFlag.ANY_OPEN_WORK.bit());
        }
        List<String> problems = new ArrayList<>(1);
        if (nullOrEmpty(workNumber)) {
            assertNull(workService.validateWorkForOpType(problems, null, opType));
            assertThat(problems).containsExactly("Work number is not specified.");
            verifyNoInteractions(mockWorkRepo);
            return;
        }
        Work work;
        if (status==null) {
            work = null;
        } else {
            work = quickWork(status);
            work.setWorkNumber(workNumber.toUpperCase());
        }
        when(mockWorkRepo.findByWorkNumber(workNumber)).thenReturn(Optional.ofNullable(work));
        assertSame(work, workService.validateWorkForOpType(problems, workNumber, opType));
        verify(mockWorkRepo).findByWorkNumber(workNumber);
        assertProblem(problems, expectedErrorMessage);
    }

    static Stream<Arguments> usableWorkArgs() {
        return Arrays.stream(new Object[][] {
                { "SGP5000", Status.active, null, null },
                { "SGP5000", Status.paused, IllegalArgumentException.class, "SGP5000 cannot be used because it is paused." },
                { "SGP5000", Status.completed, IllegalArgumentException.class, "SGP5000 cannot be used because it is completed."  },
                { "SGP5000", Status.failed, IllegalArgumentException.class, "SGP5000 cannot be used because it is failed."  },
                { "SGP5000", Status.withdrawn, IllegalArgumentException.class, "SGP5000 cannot be used because it is withdrawn."  },
                { "SGP404", null, EntityNotFoundException.class, "Work number not recognised: \"SGP404\"" },
                { null, null, NullPointerException.class, "Work number is null." },
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("usableWorksArgs")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testValidateUsableWorks(Object[] workNumbers, Object[] workData, Object[] expectedErrors) {
        assert workData.length%2==0;
        List<Work> works = IntStream.range(0, workData.length/2)
                .mapToObj(i -> new Work(i, (String) workData[2*i], null, null, null, null, null, (Status) workData[2*i+1]))
                .collect(toList());
        List<String> workNumbersList = (List) Arrays.asList(workNumbers);
        List<String> nonNullWorkNumbers = workNumbersList.stream().filter(Objects::nonNull).collect(toList());
        when(mockWorkRepo.findAllByWorkNumberIn(nonNullWorkNumbers)).thenReturn(works);

        final List<String> problems = new ArrayList<>(expectedErrors.length);
        UCMap<Work> workMap = workService.validateUsableWorks(problems, workNumbersList);

        if (!nonNullWorkNumbers.isEmpty()) {
            verify(mockWorkRepo).findAllByWorkNumberIn(nonNullWorkNumbers);
        }
        assertThat(workMap.values()).containsExactlyInAnyOrderElementsOf(works);
        if (expectedErrors.length==1 && expectedErrors[0] instanceof ArgumentMatcher) {
            ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) expectedErrors[0];
            assertThat(problems).isNotEmpty().allMatch(matcher::matches);
        } else {
            assertThat(problems).containsExactlyInAnyOrderElementsOf((List) Arrays.asList(expectedErrors));
        }
    }

    static Stream<Arguments> usableWorksArgs() {
        return Arrays.stream(new Object[][][] {
                {{},{},{"No work numbers given."}},
                {{null},{},{"No work numbers given."}},
                {{"SGP1", null}, {"SGP1", Status.active}, {"Work number is not specified."}},
                {{"SGP1", "sgp2", "SGP2"}, {"SGP1", Status.active, "SGP2", Status.active}, {}},
                {{"SGP1", "SGP2", "SGP404", "SGP405"}, {"SGP1", Status.active, "SGP2", Status.active},
                        {"Work numbers not recognised: [\"SGP404\", \"SGP405\"]"}},
                {{"SGP1", "SGP10", "SGP11"}, {"SGP1", Status.active, "SGP10", Status.paused, "SGP11", Status.paused},
                        {"Work numbers cannot be used because they are paused: [SGP10, SGP11]"}},
                {{"SGP10", "sgp10"}, {"SGP10", Status.failed},
                        {"Work number cannot be used because it is failed: [SGP10]"}},
                {{"SGP11", "sgp11"}, {"SGP11", Status.withdrawn},
                        {"Work number cannot be used because it is withdrawn: [SGP11]"}},
                {{"SGP1", "sgp1", "SGP404", "sgp404", "SGP10"}, {"SGP1", Status.active, "SGP10", Status.failed},
                        {"Work number not recognised: [\"SGP404\"]",
                                "Work number cannot be used because it is failed: [SGP10]"}},
                {{"SGP1", "SGP10", "SGP11", "SGP12","SGP13"},
                        {"SGP1", Status.active, "SGP10", Status.failed, "SGP11", Status.completed, "SGP12", Status.paused,"SGP13",Status.withdrawn},
                        {new Matchers.DisorderedStringMatcher("Work numbers cannot be used because they are %, %, % or %: \\[%, %, %, %]".replace("%", "(\\S+)"),
                                List.of("failed", "completed", "paused", "withdrawn", "SGP10", "SGP11", "SGP12", "SGP13"))}},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    public void testValidateWorksForOpType(boolean anyOpen) {
        OperationType opType = EntityFactory.makeOperationType("opname", null, OperationTypeFlag.IN_PLACE);
        if (anyOpen) {
            opType.setFlags(opType.getFlags()|OperationTypeFlag.ANY_OPEN_WORK.bit());
        }
        List<String> problems = new ArrayList<>(1);
        ArgumentCaptor<Predicate<Work>> predicateCaptor = genericCaptor(Predicate.class);
        List<String> workNumbers = List.of("SGP1");
        final Work work = EntityFactory.makeWork("SGP1");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);
        mayAddProblem("Bad thing.", workMap).when(workService).validateWorks(any(), any(), any());

        assertSame(workMap, workService.validateWorksForOpType(problems, workNumbers, opType));
        verify(workService).validateWorks(same(problems), same(workNumbers), predicateCaptor.capture());
        Predicate<Work> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(work));
        work.setStatus(Status.paused);
        assertEquals(anyOpen, predicate.test(work));
    }

    @ParameterizedTest
    @MethodSource("getWorksWithCommentsArgs")
    public void testGetWorksWithComments(Collection<Status> statuses, Collection<Work> works, Collection<WorkEvent> events) {
        if (statuses==null) {
            when(mockWorkRepo.findAll()).thenReturn(works);
        } else {
            when(mockWorkRepo.findAllByStatusIn(statuses)).thenReturn(works);
        }
        Map<Integer, WorkEvent> eventMap;
        if (events==null) {
            eventMap = null;
        } else {
            eventMap = events.stream().collect(BasicUtils.inMap(e -> e.getWork().getId()));
            when(mockWorkEventService.loadLatestEvents(any())).thenReturn(eventMap);
        }
        List<WorkWithComment> wcs = workService.getWorksWithComments(statuses);
        assertThat(wcs.stream().map(WorkWithComment::getWork)).containsExactlyElementsOf(works);

        if (events!=null) {
            List<Integer> workIds = works.stream()
                    .filter(work -> work.getStatus()==Status.failed || work.getStatus()==Status.paused || work.getStatus()==Status.withdrawn)
                    .map(Work::getId)
                    .collect(toList());

            verify(mockWorkEventService).loadLatestEvents(workIds);
            verify(workService).fillInComments(wcs, eventMap);
        }
    }

    static Stream<Arguments> getWorksWithCommentsArgs() {
        Work workA = quickWork(1, Status.active);
        Work workC = quickWork(2, Status.completed);
        Work workF = quickWork(3, Status.failed);
        Work workP = quickWork(4, Status.paused);
        Work workW = quickWork(5, Status.withdrawn);

        WorkEvent eventF = new WorkEvent(workF, WorkEvent.Type.fail, null, null);
        WorkEvent eventP = new WorkEvent(workP, WorkEvent.Type.pause, null, null);
        WorkEvent eventW = new WorkEvent(workW, WorkEvent.Type.withdraw, null, null);

        return Arrays.stream(new Object[][] {
                {null, List.of(workA, workC, workF, workP,workW), List.of(eventF, eventP,eventW)},
                {List.of(Status.active, Status.completed), List.of(workA, workC), null},
                {List.of(Status.failed, Status.paused), List.of(workF, workP), List.of(eventF, eventP)},
                {List.of(Status.withdrawn, Status.paused), List.of(workW, workP), List.of(eventW, eventP)},
        }).map(Arguments::of);
    }

    @Test
    public void testGetWorksCreatedBy() {
        User user = EntityFactory.getUser();
        List<Work> works = IntStream.range(1,3)
                .mapToObj(i -> quickWork(i, Status.active))
                .collect(toList());
        List<WorkEvent> events = works.stream()
                .map(work -> new WorkEvent(10 + work.getId(), work, WorkEvent.Type.create, user, null, null))
                .collect(toList());
        when(mockWorkEventRepo.findAllByUserAndType(user, WorkEvent.Type.create)).thenReturn(events);

        assertEquals(works, workService.getWorksCreatedBy(user));
        verify(mockWorkEventRepo).findAllByUserAndType(user, WorkEvent.Type.create);
    }

    @Test
    public void testFillInComments() {
        Work workF1 = quickWork(1, Status.failed);
        Work workF2 = quickWork(2, Status.failed);
        Work workP1 = quickWork(3, Status.paused);
        Work workP2 = quickWork(4, Status.paused);
        Work workW1 = quickWork(5, Status.withdrawn);
        Work workW2 = quickWork(6, Status.withdrawn);
        Map<Integer, WorkEvent> events = Stream.of(
                new WorkEvent(workF1, WorkEvent.Type.fail, null, new Comment(1, "Ohio", "")),
                new WorkEvent(workF2, WorkEvent.Type.create, null, new Comment(2, "Oklahoma", "")),
                new WorkEvent(workP1, WorkEvent.Type.pause, null, new Comment(3, "Oregon", "")),
                new WorkEvent(workW1, WorkEvent.Type.withdraw, null, new Comment(3, "Withdrawn", ""))
        ).collect(BasicUtils.inMap(e -> e.getWork().getId()));

        List<WorkWithComment> wcs = Stream.of(workF1, workF2, workP1, workP2,workW1,workW2)
                .map(WorkWithComment::new)
                .collect(toList());

        workService.fillInComments(wcs, events);

        assertEquals(List.of(new WorkWithComment(workF1, "Ohio"), new WorkWithComment(workF2),
                        new WorkWithComment(workP1, "Oregon"), new WorkWithComment(workP2),
                        new WorkWithComment(workW1, "Withdrawn"),new WorkWithComment(workW2)),
                     wcs);
    }

    @Test
    public void testSuggestWorkForLabwareBarcodes_unknown() {
        List<String> barcodes = List.of("STAN-404");
        when(mockLwRepo.getByBarcodeIn(barcodes)).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class, () -> workService.suggestWorkForLabwareBarcodes(barcodes, false));
        verifyNoInteractions(mockWorkRepo);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSuggestWorkForLabwareBarcodes(boolean includeInactive) {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labwares = IntStream.rangeClosed(1,4)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeLabware(lt, sample);
                    lw.setBarcode("STAN-"+i);
                    return lw;
                })
                .toArray(Labware[]::new);
        List<Labware> returnedLabware = new ArrayList<>(labwares.length+1);
        returnedLabware.addAll(Arrays.asList(labwares));
        returnedLabware.add(labwares[0]);
        List<String> barcodes = returnedLabware.stream().map(Labware::getBarcode).collect(toList());
        when(mockLwRepo.getByBarcodeIn(barcodes)).thenReturn(returnedLabware);
        List<Work> works = IntStream.rangeClosed(1,2)
                .mapToObj(i -> {
                    Work work = new Work();
                    work.setId(i);
                    work.setWorkNumber("SGP"+i);
                    return work;
                }).collect(toList());

        Function<Integer, Integer> workRepoFn = (includeInactive ? mockWorkRepo::findLatestWorkIdForLabwareId
                : mockWorkRepo::findLatestActiveWorkIdForLabwareId);

        when(workRepoFn.apply(labwares[0].getId())).thenReturn(1);
        when(workRepoFn.apply(labwares[1].getId())).thenReturn(null);
        when(workRepoFn.apply(labwares[2].getId())).thenReturn(1);
        when(workRepoFn.apply(labwares[3].getId())).thenReturn(2);
        when(mockWorkRepo.findAllById(Set.of(1,2))).thenReturn(works);

        SuggestedWorkResponse response = workService.suggestWorkForLabwareBarcodes(barcodes, includeInactive);
        assertThat(response.getSuggestedWorks()).containsExactlyInAnyOrder(
                new SuggestedWork("STAN-1", "SGP1"),
                new SuggestedWork("STAN-2", null),
                new SuggestedWork("STAN-3", "SGP1"),
                new SuggestedWork("STAN-4", "SGP2")
        );
        assertThat(response.getWorks()).containsExactlyInAnyOrderElementsOf(works);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSuggestLabwareForWork(boolean forRelease) {
        Work work = new Work();
        work.setId(100);
        work.setWorkNumber("SGP100");
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0,5)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeLabware(lt, sample);
                    lw.setId(100+i);
                    lw.setBarcode("STAN-"+lw.getId());
                    return lw;
                })
                .collect(toList());
        labware.get(2).setUsed(true);
        labware.get(3).setReleased(true);
        labware.get(4).setDiscarded(true);
        final List<Integer> lwIds = List.of(100, 101, 102, 103, 104);
        when(mockWorkRepo.findLabwareIdsForWorkIds(List.of(work.getId())))
                .thenReturn(lwIds);
        when(mockLwRepo.findAllByIdIn(lwIds)).thenReturn(labware);

        assertEquals(labware.subList(0, forRelease ? 3 : 2),
                workService.suggestLabwareForWorkNumber(work.getWorkNumber(), forRelease));
    }

    @Test
    public void testLoadWorksForSlotsIn() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(3,1);
        Labware lw = EntityFactory.makeLabware(lt, sample);
        Map<SlotIdSampleId, Set<Work>> slotWorks = Map.of(new SlotIdSampleId(lw.getFirstSlot(), sample),
                Set.of(EntityFactory.makeWork("SGP1")));
        when(mockWorkRepo.slotSampleWorksForSlotIds(any())).thenReturn(slotWorks);
        assertSame(slotWorks, workService.loadWorksForSlotsIn(List.of(lw)));
        List<Integer> slotIds = lw.getSlots().stream().map(Slot::getId).toList();
        verify(mockWorkRepo).slotSampleWorksForSlotIds(slotIds);
    }

    private Operation makeOp(OperationType opType, int opId, Labware srcLw, Labware dstLw) {
        List<Action> actions = new ArrayList<>();
        int acId = 100*opId;
        for (Slot src : srcLw.getSlots()) {
            for (Sample sam0 : src.getSamples()) {
                for (Slot dst : dstLw.getSlots()) {
                    for (Sample sam1 : dst.getSamples()) {
                        Action action = new Action(acId, opId, src, dst, sam1, sam0);
                        actions.add(action);
                        ++acId;
                    }
                }
            }
        }
        return new Operation(opId, opType, null, actions, EntityFactory.getUser());
    }
}
