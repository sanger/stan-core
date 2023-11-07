package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.mlwh.SSStudy;
import uk.ac.sanger.sccp.stan.mlwh.SSStudyRepo;
import uk.ac.sanger.sccp.stan.model.DnapStudy;
import uk.ac.sanger.sccp.stan.repo.DnapStudyRepo;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.returnArgument;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

public class TestSSStudyService {
    @Mock
    private Transactor mockTransactor;
    @Mock
    private DnapStudyRepo mockDsRepo;
    @Mock
    private SSStudyRepo mockSsRepo;

    @InjectMocks
    private SSStudyServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testUpdateStudies() {
        List<DnapStudy> dss = List.of(new DnapStudy(1, 10, "Ten", false),
                new DnapStudy(2, 20, "Twenty", true));
        List<SSStudy> sss = List.of(new SSStudy(10, "Ten"), new SSStudy(20, "Bananas"),
                new SSStudy(30, "Custard"));
        when(mockDsRepo.findAll()).thenReturn(dss);
        when(mockSsRepo.loadAllSs()).thenReturn(sss);
        doNothing().when(service).update(any(), any());

        Matchers.mockTransactor(mockTransactor);

        service.updateStudies();

        ArgumentCaptor<Map<Integer, DnapStudy>> dsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<Integer, SSStudy>> ssCaptor = ArgumentCaptor.forClass(Map.class);

        verify(mockTransactor).transact(any(), any());
        verify(service).update(dsCaptor.capture(), ssCaptor.capture());

        var dsMap = dsCaptor.getValue();
        assertThat(dsMap.values()).containsExactlyInAnyOrderElementsOf(dss);
        dss.forEach(ds -> assertSame(ds, dsMap.get(ds.getSsId())));

        var ssMap = ssCaptor.getValue();
        assertThat(ssMap.values()).containsExactlyInAnyOrderElementsOf(sss);
        sss.forEach(ss -> assertSame(ss, ssMap.get(ss.getId())));
    }

    @Test
    void testScheduledUpdate() {
        doNothing().when(service).updateStudies();
        service.scheduledUpdate();
        verify(service).updateStudies();
    }

    @Test
    void testLoadEnabledStudies() {
        List<DnapStudy> studies = List.of(new DnapStudy(1, 10, "s10", true));
        when(mockDsRepo.findAllByEnabled(true)).thenReturn(studies);
        assertSame(studies, service.loadEnabledStudies());
    }

    @Test
    void testUpdate() {
        DnapStudy toDisable = new DnapStudy(1, 10, "Ten", true);
        DnapStudy toEnable = new DnapStudy(2, 20, "Twenty", false);
        DnapStudy toRename = new DnapStudy(3, 30, "Bananas", true);
        DnapStudy toEnableAndRename = new DnapStudy(4, 40, "Custard", false);
        DnapStudy toLeaveEnabled = new DnapStudy(6, 60, "Sixty", true);
        DnapStudy toLeaveDisabled = new DnapStudy(7, 70, "Seventy", false);

        SSStudy toCreate = new SSStudy(50, "Fifty");

        Map<Integer, DnapStudy> dsMap = Stream.of(toDisable, toEnable, toRename, toEnableAndRename,
                        toLeaveEnabled, toLeaveDisabled)
                .collect(inMap(DnapStudy::getSsId));

        Map<Integer, SSStudy> ssMap = Stream.of(toCreate,
                new SSStudy(20, "Twenty"), new SSStudy(30, "Thirty"),
                new SSStudy(40, "Forty"), new SSStudy(60, "Sixty")
                ).collect(inMap(SSStudy::getId));
        DnapStudy newDs = new DnapStudy(50, "Fifty");

        doAnswer(returnArgument()).when(service).setEnabled(any(), anyBoolean());
        doAnswer(returnArgument()).when(service).rename(any(), any());
        doReturn(List.of(newDs)).when(service).create(any());

        service.update(dsMap, ssMap);

        verify(service).setEnabled(List.of(toDisable), false);
        verify(service).setEnabled(sameElements(List.of(toEnable, toEnableAndRename), true), eq(true));
        verify(service).rename(sameElements(List.of(toRename, toEnableAndRename), true), same(ssMap));
        verify(service).create(List.of(toCreate));

        Set<DnapStudy> expected = Set.of(toDisable, toEnable, toRename, toEnableAndRename);
        verify(mockDsRepo).saveAll(expected);
        verify(mockDsRepo).saveAll(List.of(newDs));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testSetEnabled(boolean enable) {
        List<DnapStudy> dss = List.of(new DnapStudy(1, 10, "Ten", !enable),
                new DnapStudy(2, 20, "Twenty", !enable));
        assertSame(dss, service.setEnabled(dss, enable));
        dss.forEach(ds -> assertEquals(enable, ds.isEnabled()));
    }

    @Test
    void testRename() {
        List<DnapStudy> dss = List.of(new DnapStudy(1, 10, "Beep", true),
                new DnapStudy(2, 20, "Boop", false));
        Map<Integer, SSStudy> ssMap = Map.of(
                10, new SSStudy(10, "Ten"), 20, new SSStudy(20, "Twenty"),
                30, new SSStudy(30, "Thirty"));
        assertSame(dss, service.rename(dss, ssMap));
        assertEquals("Ten", dss.get(0).getName());
        assertEquals("Twenty", dss.get(1).getName());
    }

    @Test
    void testCreate() {
        List<SSStudy> toCreate = List.of(
                new SSStudy(10, "Ten"), new SSStudy(20, "Twenty")
        );

        assertThat(service.create(toCreate)).containsExactly(
                new DnapStudy(10, "Ten"), new DnapStudy(20, "Twenty")
        );
    }
}