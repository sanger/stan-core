package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link FileSectionRegisterServiceImp}
 */
class TestFileSectionRegisterService {
    @Mock
    private SectionRegisterService mockSectionRegisterService;
    @Mock
    private SectionRegisterFileReader mockFileReader;
    @Mock
    private Transactor mockTransactor;
    @Mock
    private MultipartFile file;
    private FileSectionRegisterServiceImp service;
    private AutoCloseable mocking;
    private User user;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = new FileSectionRegisterServiceImp(mockSectionRegisterService, mockFileReader, mockTransactor);
        user = EntityFactory.getUser();
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testRegister_success() throws IOException {
        Matchers.mockTransactor(mockTransactor);
        SectionRegisterRequest request = new SectionRegisterRequest();
        when(mockFileReader.read(any(MultipartFile.class))).thenReturn(request);
        RegisterResult result = new RegisterResult();
        when(mockSectionRegisterService.register(any(), any())).thenReturn(result);
        assertSame(result, service.register(user, file));
        verify(mockFileReader).read(file);
        verify(mockSectionRegisterService).register(user, request);
        verify(mockTransactor).transact(any(), any());
    }

    @Test
    void testRegister_IOException() throws IOException {
        IOException iox = new IOException("Bad IO.");
        when(mockFileReader.read(any(MultipartFile.class))).thenThrow(iox);
        assertThat(assertThrows(UncheckedIOException.class, () -> service.register(user, file))).hasCause(iox);
        verify(mockFileReader).read(file);
        verifyNoInteractions(mockTransactor);
        verifyNoInteractions(mockSectionRegisterService);
    }
}