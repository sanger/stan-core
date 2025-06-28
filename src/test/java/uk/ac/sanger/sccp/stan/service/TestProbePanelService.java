package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.model.ProbePanel;
import uk.ac.sanger.sccp.stan.model.ProbePanel.ProbeType;
import uk.ac.sanger.sccp.stan.repo.ProbePanelRepo;

import javax.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.returnArgument;

/**
 * Tests {@link ProbePanelService}
 * @author dr6
 */
public class TestProbePanelService {
    ProbePanelRepo mockRepo;
    Validator<String> validator;
    ProbePanelService service;

    @BeforeEach
    void setup() {
        mockRepo = mock(ProbePanelRepo.class);
        validator = new StringValidator("name", 1, 16, StringValidator.CharacterType.ALPHA);
        service = new ProbePanelService(mockRepo, validator);
    }

    @ParameterizedTest
    @CsvSource({
            "xenium, bananas, false,",
            ", bananas, false, No probe type supplied.",
            "xenium,,false, No name supplied.",
            "xenium, ba!#, false, name \"ba!#\" contains invalid characters \"!#\".",
            "xenium, bananas, true, Probe panel already exists: xenium bananas",
    })
    void testAddProbePanel(ProbeType type, String name, boolean exists, String expectedErrorMessage) {
        when(mockRepo.existsByTypeAndName(any(), any())).thenReturn(exists);
        if (expectedErrorMessage != null) {
            assertThat(assertThrows(Exception.class, () -> service.addProbePanel(type, name))).hasMessage(expectedErrorMessage);
            verify(mockRepo, never()).save(any());
            return;
        }
        ProbePanel savedProbe = new ProbePanel(1, type, name);
        when(mockRepo.save(any())).thenReturn(savedProbe);
        assertSame(savedProbe, service.addProbePanel(type, name));
        verify(mockRepo).save(new ProbePanel(type, name));
    }

    @ParameterizedTest
    @CsvSource({
            ",false",
            "true,true",
            "true,false",
            "false,true",
            "false,false",
    })
    void testSetProbePanelEnabled(Boolean state, boolean enable) {
        if (state==null) {
            when(mockRepo.getByTypeAndName(any(), any())).thenThrow(new EntityNotFoundException("No such thing."));
            assertThrows(EntityNotFoundException.class, () -> service.setProbePanelEnabled(ProbeType.xenium, "bananas", enable));
            verify(mockRepo, never()).save(any());
            verify(mockRepo).getByTypeAndName(ProbeType.xenium, "bananas");
            return;
        }
        ProbePanel probe = new ProbePanel(10, ProbeType.xenium, "bananas");
        probe.setEnabled(state);
        when(mockRepo.getByTypeAndName(any(), any())).thenReturn(probe);
        when(mockRepo.save(any())).thenAnswer(returnArgument());

        assertSame(probe, service.setProbePanelEnabled(ProbeType.xenium, "bananas", enable));
        verify(mockRepo).getByTypeAndName(ProbeType.xenium, "bananas");
        assertEquals(enable, probe.isEnabled());
        if (enable != state) {
            verify(mockRepo).save(probe);
        }
    }
}
