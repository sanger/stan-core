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
import uk.ac.sanger.sccp.utils.Zip;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
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
    private IRegisterService<BlockRegisterRequest> mockBlockRegisterService;
    @Mock
    private IRegisterService<OriginalSampleRegisterRequest> mockOriginalRegisterService;
    @Mock
    private MultipartFileReader<SectionRegisterRequest> mockSectionFileReader;
    @Mock
    private MultipartFileReader<BlockRegisterRequest> mockBlockFileReader;
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
        service = spy(new FileRegisterServiceImp(mockSectionRegisterService, mockBlockRegisterService, mockOriginalRegisterService,
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
        if (request instanceof BlockRegisterRequest && existingExt!=null) {
            verify(service).updateWithExisting((BlockRegisterRequest) request, existingExt);
        } else {
            verify(service, never()).updateWithExisting(any(), any());
        }
        if (request instanceof BlockRegisterRequest && ignoreExt!=null) {
            verify(service).updateToRemove((BlockRegisterRequest) request, ignoreExt);
        } else {
            verify(service, never()).updateToRemove(any(), any());
        }
        verify(mockTransactor).transact(any(), any());
    }

    static Stream<Arguments> regExtNamesIgnoreArgs() {
        return Arrays.stream(new Object[][] {
                    {new SectionRegisterRequest()},
                    {new OriginalSampleRegisterRequest()},
                    {new BlockRegisterRequest()},
                    {new BlockRegisterRequest(), List.of("Alpha1"), null},
                    {new BlockRegisterRequest(), null, List.of("Beta")},
                    {new BlockRegisterRequest(), List.of("Alpha1"), List.of("Beta")},
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
        BlockRegisterRequest request = requestWithExternalNames(extNames);
        List<String> existing = any ? List.of("ALPHA1", "alpha2") : List.of();

        service.updateWithExisting(request, existing);
        Zip.enumerate(streamSamples(request)).forEach((i, brs) ->
                assertEquals(any && (i==1 || i==3), brs.isExistingTissue()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testUpdateToRemove(boolean anyToRemove) {
        String[] extNames = { null, "Alpha1", "Beta", "Alpha2" };
        BlockRegisterRequest request = requestWithExternalNames(extNames);
        List<String> ignore = anyToRemove ? List.of("ALPHA1", "alpha2") : List.of();
        service.updateToRemove(request, ignore);
        String[] remaining = (anyToRemove ? new String[]{null, "Beta"} : extNames);
        assertThat(streamSamples(request).map(BlockRegisterSample::getExternalIdentifier)).containsExactly(remaining);
    }

    private static BlockRegisterRequest requestWithExternalNames(String... xns) {
        List<BlockRegisterSample> brss = Arrays.stream(xns)
                .map(TestFileRegisterService::brsWithExternalName)
                .toList();
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setSamples(brss);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        return request;
    }

    private static Stream<BlockRegisterSample> streamSamples(BlockRegisterRequest request) {
        return request.getLabware().stream().flatMap(brl -> brl.getSamples().stream());
    }

    private static BlockRegisterSample brsWithExternalName(String xn) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(xn);
        return brs;
    }
}