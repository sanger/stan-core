package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.asCollection;

/**
 * Test {@link FileStoreServiceImp}
 * @author dr6
 */
public class TestFileStoreService {
    @Mock private StanFileConfig mockConfig;
    @Mock private StanFileRepo mockFileRepo;
    @Mock private WorkRepo mockWorkRepo;
    @Mock private WorkEventRepo mockWorkEventRepo;
    @Mock private Transactor mockTransactor;
    private Clock clock;

    private FileStoreServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        when(mockConfig.getRoot()).thenReturn("/ROOT");
        when(mockConfig.getDir()).thenReturn("path-to-folder");
        clock = Clock.fixed(LocalDateTime.of(2022,11,4,14,0).toInstant(ZoneOffset.UTC), ZoneId.systemDefault());

        service = spy(new FileStoreServiceImp(mockConfig, clock, mockTransactor, mockFileRepo, mockWorkRepo, mockWorkEventRepo));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @CsvSource({"folder/alpha,alpha,alpha", "/Robot/SW/R2D2 *&^%,R2D2 *&^%,R2D2", "Alpha/^%^&*,^%^&*, unnamed", ",unnamed,unnamed",

            "folder/Alabama Alaska Arizona Arkansas California Colorado Connecticut Delaware Florida Georgia Hawaii Idah," +
            "Alabama Alaska Arizona Arkansas California Colorado Connecticut Delaware Florida Georgia Hawaii Idah," +
            "AlabamaAlaskaArizonaArkansasCaliforniaColoradoConnecticutDelawareFloridaGeorgiaHawaiiIdah",

            "folder/AlabamaAlaskaArizonaArkansasCaliforniaColoradoConnecticutDelawareFloridaGeorgiaHawaiiIdahoIllinoisIn," +
            "AlabamaAlaskaArizonaArkansasCaliforniaColoradoConnecticutDelawareFloridaGeorgiaHawaiiIdahoIllinoisIn," +
            "AlabamaAlaskaArizonaArkansasCaliforniaColoradoConnecticutDelawareFloridaGeorgiaHawaiiIdahoIllino",

            "folder/Alabama Alaska Arizona Arkansas California Colorado Connecticut Delaware Florida Georgia Hawaii Idaho,,"})
    public void testSave(String name, String expectedName, String expectedPathFragment) throws IOException {
        Work work = new Work(500, "SGP500", null, null, null, null, null, Work.Status.active);
        MultipartFile data = mock(MultipartFile.class);
        LocalDateTime time = LocalDateTime.now(clock);
        when(data.getOriginalFilename()).thenReturn(name);

        when(mockFileRepo.saveAll(any())).then(invocation -> {
            Collection<StanFile> sfs = invocation.getArgument(0);
            int id = 300;
            for (StanFile sf : sfs) {
                sf.setId(id);
                sf.setCreated(time);
                ++id;
            }
            return sfs;
        });
        final Set<Work> works = Set.of(work);
        when(mockWorkRepo.getSetByWorkNumberIn(List.of(work.getWorkNumber()))).thenReturn(works);
        User user = EntityFactory.getUser();
        doNothing().when(service).checkAuthorisation(any(), any());
        Matchers.mockTransactor(mockTransactor);

        if (expectedName==null) {
            assertThrows(IllegalArgumentException.class, () -> service.save(user, data, List.of(work.getWorkNumber())));
            verify(data, never()).transferTo(any(Path.class));
            verifyNoInteractions(mockTransactor);
            verifyNoInteractions(mockFileRepo);
            verify(service).checkAuthorisation(user, works);
            return;
        }

        var sfs = asCollection(service.save(user, data, List.of(work.getWorkNumber())));
        assertThat(sfs).hasSize(1);
        var sf = sfs.iterator().next();
        String expectedPath = "path-to-folder/"+time+"_"+expectedPathFragment;
        assertEquals(expectedPath, sf.getPath());
        assertEquals(expectedName, sf.getName());
        assertEquals(300, sf.getId());
        assertEquals(user, sf.getUser());

        verify(data).transferTo(Paths.get("/ROOT/"+expectedPath));
        verify(service).checkAuthorisation(user, works);
        verify(service).deprecateOldFiles(expectedName, List.of(work.getId()), time);
        verify(mockFileRepo).saveAll(any());
        verify(mockTransactor).transact(eq("updateStanFiles"), notNull());
    }

    @Test
    public void testSaveToMultipleWorks() throws IOException {
        String originalFilename = "Alpha/ABC @-%.txt";
        String originalBasename = "ABC @-%.txt";
        String expectedPathFragment = "ABC-txt";
        List<Integer> workIds = IntStream.range(500, 503).boxed().collect(toList());
        Set<Work> works = workIds.stream()
                .map(i -> new Work(i, "SGP"+i, null, null, null, null, null, Work.Status.active))
                .collect(toSet());
        MultipartFile data = mock(MultipartFile.class);
        LocalDateTime time = LocalDateTime.now(clock);
        when(data.getOriginalFilename()).thenReturn(originalFilename);
        final int[] newFileId = {300};
        doNothing().when(service).checkAuthorisation(any(), any());

        when(mockFileRepo.saveAll(any())).then(invocation -> {
            Collection<StanFile> sfs = invocation.getArgument(0);
            for (StanFile sf : sfs) {
                sf.setId(newFileId[0]);
                sf.setCreated(time);
                ++newFileId[0];
            }
            return sfs;
        });
        List<String> workNumbers = works.stream().map(Work::getWorkNumber).collect(toList());
        when(mockWorkRepo.getSetByWorkNumberIn(Matchers.sameElements(workNumbers, true))).thenReturn(works);
        User user = EntityFactory.getUser();
        Matchers.mockTransactor(mockTransactor);

        var sfs = asCollection(service.save(user, data, workNumbers));
        assertThat(sfs).hasSize(works.size());
        String expectedPath = "path-to-folder/" + time + "_" + expectedPathFragment;
        int index = 0;
        for (var sf : sfs) {
            assertEquals(expectedPath, sf.getPath());
            assertEquals(originalBasename, sf.getName());
            assertEquals(300+index, sf.getId());
            assertEquals(user, sf.getUser());
            assertThat(works).contains(sf.getWork());
            assertEquals(sf.getWork().getId(), 500+index);
            ++index;
        }

        verify(data).transferTo(Paths.get("/ROOT/"+expectedPath));
        verify(service).checkAuthorisation(user, works);
        verify(service).deprecateOldFiles(eq(originalBasename), Matchers.sameElements(workIds, true), eq(time));
        verify(mockFileRepo).saveAll(any());
        verify(mockTransactor).transact(eq("updateStanFiles"), notNull());
    }

    @ParameterizedTest
    @CsvSource({
            "normal, 012,",
            "enduser, 12, User user1 does not have privilege to upload files for work number [SGP12]",
            "enduser, 012, User user1 does not have privilege to upload files for work numbers [SGP11, SGP13]",
            "disabled, 1, User user1 does not have privilege to upload files.",
            "enduser, 111,",
    })
    public void testCheckAuthorisation(User.Role role, String ownerJoined, String expectedError) {
        int numUsers = ownerJoined.chars().map(n -> n - '0').max().orElseThrow();
        User[] users = IntStream.rangeClosed(1, numUsers)
                .mapToObj(i -> new User(i, "user"+i, User.Role.normal))
                .toArray(User[]::new);
        users[0].setRole(role);
        List<Work> works = IntStream.rangeClosed(11, 10+ownerJoined.length())
                .mapToObj(TestFileStoreService::makeWork)
                .collect(toList());
        List<WorkEvent> workEvents = IntStream.range(0, ownerJoined.length())
                .mapToObj(i -> {
                    int userIndex = ownerJoined.charAt(i)-'1';
                    if (userIndex < 0) {
                        return null;
                    }
                    return new WorkEvent(21+i, works.get(i), WorkEvent.Type.create,
                            users[userIndex], null, null);
                })
                .filter(Objects::nonNull)
                .collect(toList());
        when(mockWorkEventRepo.findAllByWorkInAndType(any(), any())).thenReturn(workEvents);

        if (expectedError==null) {
            service.checkAuthorisation(users[0], works);
        } else {
            assertThrows(InsufficientAuthenticationException.class, () -> service.checkAuthorisation(users[0], works));
        }
        if (role==User.Role.enduser) {
            verify(mockWorkEventRepo).findAllByWorkInAndType(works, WorkEvent.Type.create);
        } else {
            verifyNoInteractions(mockWorkEventRepo);
        }
    }

    private static Work makeWork(int id) {
        Work work = new Work();
        work.setId(id);
        work.setWorkNumber("SGP"+id);
        return work;
    }

    @Test
    public void testFileTransferPathExists() throws IOException {
        Matchers.mockTransactor(mockTransactor);
        MultipartFile data = mock(MultipartFile.class);
        final String name = "FILENAME.txt";
        Work work = new Work(500, "SGP500", null, null, null, null, null, Work.Status.active);

        when(data.getOriginalFilename()).thenReturn(name);
        when(mockWorkRepo.getSetByWorkNumberIn(List.of(work.getWorkNumber()))).thenReturn(Set.of(work));
        User user = EntityFactory.getUser();
        final LocalDateTime time = LocalDateTime.now(clock);
        when(mockFileRepo.existsByPath("path-to-folder/"+time+"_FILENAMEtxt")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.save(user, data, List.of(work.getWorkNumber())));
        verify(data, never()).transferTo(any(Path.class));
        verify(mockWorkRepo, never()).save(any());
        verify(mockTransactor, never()).transact(any(), any());
    }

    @Test
    public void testFileTransferError() throws IOException {
        Matchers.mockTransactor(mockTransactor);
        MultipartFile data = mock(MultipartFile.class);
        doThrow(IOException.class).when(data).transferTo(any(Path.class));
        final String name = "FILENAME.txt";
        Work work = new Work(500, "SGP500", null, null, null, null, null, Work.Status.active);

        when(data.getOriginalFilename()).thenReturn(name);
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        User user = EntityFactory.getUser();

        final LocalDateTime time = LocalDateTime.now(clock);

        assertThrows(UncheckedIOException.class, () -> service.save(user, data, List.of(work.getWorkNumber())));
        final String expectedPath = "path-to-folder/"+time+"_FILENAMEtxt";
        verify(data).transferTo(Paths.get("/ROOT/"+expectedPath));
        verify(mockFileRepo, never()).save(any());
    }

    @Test
    public void testLoadResource() throws IOException {
        String root = System.getProperty("user.home");
        doReturn(root).when(mockConfig).getRoot();
        mkdir(Paths.get(root, "stan_files"));
        mkdir(Paths.get(root, "stan_files", "test"));
        Path filePath = Paths.get(root, "stan_files", "test", "testfile.txt");
        List<String> fileLines = List.of("Alpha", "Beta");
        Files.write(filePath, fileLines);
        User user = EntityFactory.getUser();
        StanFile sf = new StanFile(200, null, null, user,"filename",
                "stan_files/test/testfile.txt", null);
        var resource = service.loadResource(sf);
        try (var in = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            assertThat(in.lines()).containsExactlyElementsOf(fileLines);
        }
    }

    @Test
    public void testLoadResource_inactive() {
        StanFile sf = new StanFile(200, null, null, null, "filename",
                "stan_files/test/testfile.txt", LocalDateTime.now());
        assertThrows(IllegalStateException.class, () -> service.loadResource(sf));
    }

    private static Path mkdir(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.createDirectory(path);
        }
        return path;
    }

    @Test
    public void testLookUp() {
        StanFile sf = new StanFile(200, null, null, null, "filename", "path", null);
        when(mockFileRepo.getById(sf.getId())).thenReturn(sf);
        assertSame(sf, service.lookUp(sf.getId()));
    }

    @Test
    public void testDeprecateOldFiles_none() {
        when(mockFileRepo.findAllActiveByWorkIdAndName(any(), any())).thenReturn(List.of());
        service.deprecateOldFiles("name", List.of(24), LocalDateTime.now());
        verify(mockFileRepo, never()).save(any());
        verify(mockFileRepo, never()).saveAll(any());
    }


    @Test
    public void testDeprecateOldFiles() {
        List<StanFile> sfs = List.of(
                new StanFile(10, null, null, null, null, null, null),
                new StanFile(11, null, null, null, null, null, null)
        );
        sfs.forEach(sf -> assertNull(sf.getDeprecated()));
        sfs.forEach(sf -> assertTrue(sf.isActive()));
        List<Integer> workIds = List.of(24,25);
        when(mockFileRepo.findAllActiveByWorkIdAndName(workIds, "name")).thenReturn(sfs);
        final LocalDateTime time = LocalDateTime.now();
        service.deprecateOldFiles("name", workIds, time);
        verify(mockFileRepo).saveAll(sfs);
        sfs.forEach(sf -> assertEquals(time, sf.getDeprecated()));
        sfs.forEach(sf -> assertFalse(sf.isActive()));
    }

    @Test
    public void testList() {
        List<StanFile> sfs = List.of(
                new StanFile(10, null, null, null, null, null, null),
                new StanFile(11, null, null, null, null, null, null)
        );
        Work work1 = new Work(501, "SGP501", null, null, null, null, null, Work.Status.active);
        Work work2 = new Work(502, "SGP502", null, null, null, null, null, Work.Status.active);
        List<Integer> workIds = List.of(501, 502);
        List<String> workNumbers = List.of("SGP501", "SGP502");
        when(mockWorkRepo.getSetByWorkNumberIn(workNumbers)).thenReturn(Set.of(work1, work2));
        when(mockFileRepo.findAllActiveByWorkIdIn(Matchers.sameElements(workIds, true))).thenReturn(sfs);
        assertEquals(sfs, service.list(workNumbers));
    }
}
