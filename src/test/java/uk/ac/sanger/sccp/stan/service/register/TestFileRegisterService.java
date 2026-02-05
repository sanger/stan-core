package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
    private IRegisterService<OriginalSampleRegisterRequest> mockOriginalRegisterService;
    @Mock
    private MultipartFileReader<SectionRegisterRequest> mockSectionFileReader;
    @Mock
    private MultipartFileReader<RegisterRequest> mockBlockFileReader;
    @Mock
    private MultipartFileReader<OriginalSampleRegisterRequest> mockOriginalFileReader;
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
        service = spy(new FileRegisterServiceImp(mockSectionRegisterService, mockBlockRegisterService,mockOriginalRegisterService,
                mockSectionFileReader, mockBlockFileReader, mockOriginalFileReader, mockTransactor));
        user = EntityFactory.getUser();
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @MethodSource("regExtNamesIgnoreArgs")
    void testSectionRegister_success(Object request, List<String> existingExt, List<String> ignoreExt) throws IOException {
        Matchers.mockTransactor(mockTransactor);
        IRegisterService<?> regService;
        MultipartFileReader<?> fileReader;
        BiFunction<User, MultipartFile, RegisterResult> functionUnderTest;
        if (request instanceof SectionRegisterRequest) {
            regService = mockSectionRegisterService;
            fileReader = mockSectionFileReader;
            functionUnderTest = service::registerSections;
        } else if (request instanceof OriginalSampleRegisterRequest) {
            regService = mockOriginalRegisterService;
            fileReader = mockOriginalFileReader;
            functionUnderTest = service::registerOriginal;
        } else {
            regService = mockBlockRegisterService;
            fileReader = mockBlockFileReader;
            if (existingExt==null && ignoreExt==null) {
                functionUnderTest = service::registerBlocks;
            } else {
                functionUnderTest = (user, mpFile) -> service.registerBlocks(user, mpFile, existingExt, ignoreExt);
            }
        }
        doReturn(request).when(fileReader).read(any(MultipartFile.class));
        RegisterResult result = new RegisterResult();
        when(regService.register(any(), any())).thenReturn(result);
        assertSame(result, functionUnderTest.apply(user, file));
        verify(fileReader).read(file);
        //noinspection unchecked,rawtypes
        verify((IRegisterService) regService).register(user, request);
        if (request instanceof RegisterRequest && existingExt!=null) {
            verify(service).updateWithExisting((RegisterRequest) request, existingExt);
        } else {
            verify(service, never()).updateWithExisting(any(), any());
        }
        if (request instanceof RegisterRequest && ignoreExt!=null) {
            verify(service).updateToRemove((RegisterRequest) request, ignoreExt);
        } else {
            verify(service, never()).updateToRemove(any(), any());
        }
        verify(mockTransactor).transact(any(), any());
    }

    static Stream<Arguments> regExtNamesIgnoreArgs() {
        return Arrays.stream(new Object[][] {
                    {new SectionRegisterRequest()},
                    {new OriginalSampleRegisterRequest()},
                    {new RegisterRequest()},
                    {new RegisterRequest(), List.of("Alpha1"), null},
                    {new RegisterRequest(), null, List.of("Beta")},
                    {new RegisterRequest(), List.of("Alpha1"), List.of("Beta")},
            }).map(arr -> arr.length < 3 ? Arrays.copyOf(arr, 3) : arr)
            .map(Arguments::of);
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
        } else if (request instanceof OriginalSampleRegisterRequest) {
            regService = mockOriginalRegisterService;
            fileReader = mockOriginalFileReader;
            functionUnderTest = service::registerOriginal;
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
                {new OriginalSampleRegisterRequest()},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testUpdateWithExisting(boolean any) {
        String[] extNames = { null, "Alpha1", "Beta", "Alpha2" };
        List<BlockRegisterRequest_old> blocks = Arrays.stream(extNames)
                .map(TestFileRegisterService::blockRegWithExternalName)
                .toList();
        RegisterRequest request = new RegisterRequest(blocks);
        List<String> existing = any ? List.of("ALPHA1", "alpha2") : List.of();

        service.updateWithExisting(request, existing);
        IntStream.range(0, blocks.size()).forEach(i ->
            assertEquals(any && (i==1 || i==3), blocks.get(i).isExistingTissue())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testUpdateToRemove(boolean anyToRemove) {
        String[] extNames = { null, "Alpha1", "Beta", "Alpha2" };
        RegisterRequest request = new RegisterRequest(Arrays.stream(extNames)
                .map(TestFileRegisterService::blockRegWithExternalName)
                .toList());
        List<String> ignore = anyToRemove ? List.of("ALPHA1", "alpha2") : List.of();
        service.updateToRemove(request, ignore);
        String[] remaining = (anyToRemove ? new String[]{null, "Beta"} : extNames);
        assertThat(request.getBlocks().stream().map(BlockRegisterRequest_old::getExternalIdentifier)).containsExactly(remaining);
    }

    private static BlockRegisterRequest_old blockRegWithExternalName(String xn) {
        BlockRegisterRequest_old br = new BlockRegisterRequest_old();
        br.setExternalIdentifier(xn);
        return br;
    }
}