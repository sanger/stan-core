package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.register.filereader.MultipartFileReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link FileRegisterServiceImp}
 */
class TestFileRegisterService {
    @Mock
    private IRegisterService<SectionRegisterRequest> mockSectionRegisterService;
    @Mock
    private IRegisterService<RegisterRequest> mockBlockRegisterService;
    @Mock
    private MultipartFileReader<SectionRegisterRequest> mockSectionFileReader;
    @Mock
    private MultipartFileReader<RegisterRequest> mockBlockFileReader;
    @Mock
    private Transactor mockTransactor;
    @Mock
    private MultipartFile file;
    private FileRegisterServiceImp service;
    private AutoCloseable mocking;
    private User user;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = new FileRegisterServiceImp(mockSectionRegisterService, mockBlockRegisterService,
                mockSectionFileReader, mockBlockFileReader, mockTransactor);
        user = EntityFactory.getUser();
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @MethodSource("regArgs")
    void testSectionRegister_success(Object request) throws IOException {
        Matchers.mockTransactor(mockTransactor);
        IRegisterService<?> regService;
        MultipartFileReader<?> fileReader;
        BiFunction<User, MultipartFile, RegisterResult> functionUnderTest;
        if (request instanceof SectionRegisterRequest) {
            regService = mockSectionRegisterService;
            fileReader = mockSectionFileReader;
            functionUnderTest = service::registerSections;
        } else {
            regService = mockBlockRegisterService;
            fileReader = mockBlockFileReader;
            functionUnderTest = service::registerBlocks;
        }
        doReturn(request).when(fileReader).read(any(MultipartFile.class));
        RegisterResult result = new RegisterResult();
        when(regService.register(any(), any())).thenReturn(result);
        assertSame(result, functionUnderTest.apply(user, file));
        verify(fileReader).read(file);
        //noinspection unchecked,rawtypes
        verify((IRegisterService) regService).register(user, request);
        verify(mockTransactor).transact(any(), any());
    }

    @ParameterizedTest
    @MethodSource("regArgs")
    void testSectionRegister_IOException(Object request) throws IOException {
        IRegisterService<?> regService;
        MultipartFileReader<?> fileReader;
        BiFunction<User, MultipartFile, RegisterResult> functionUnderTest;
        if (request instanceof SectionRegisterRequest) {
            regService = mockSectionRegisterService;
            fileReader = mockSectionFileReader;
            functionUnderTest = service::registerSections;
        } else {
            regService = mockBlockRegisterService;
            fileReader = mockBlockFileReader;
            functionUnderTest = service::registerBlocks;
        }

        IOException iox = new IOException("Bad IO.");
        when(fileReader.read(any(MultipartFile.class))).thenThrow(iox);
        assertThat(assertThrows(UncheckedIOException.class, () -> functionUnderTest.apply(user, file))).hasCause(iox);
        verify(fileReader).read(file);
        verifyNoInteractions(mockTransactor);
        verifyNoInteractions(regService);
    }

    static Stream<Arguments> regArgs() {
        return Arrays.stream(new Object[][] {
                {new SectionRegisterRequest()},
                {new RegisterRequest()},
        }).map(Arguments::of);
    }
}