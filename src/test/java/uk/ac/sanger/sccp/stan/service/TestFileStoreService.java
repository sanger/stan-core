package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.StanFileRepo;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test {@link FileStoreServiceImp}
 * @author dr6
 */
public class TestFileStoreService {
    private StanFileConfig mockConfig;
    private Clock clock;
    private StanFileRepo mockFileRepo;
    private WorkRepo mockWorkRepo;

    private FileStoreServiceImp service;

    @BeforeEach
    void setup() {
        mockConfig = mock(StanFileConfig.class);
        when(mockConfig.getRoot()).thenReturn("/ROOT");
        when(mockConfig.getDir()).thenReturn("DIR");
        clock = Clock.fixed(LocalDateTime.of(2022,11,4,14,0).toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
        mockFileRepo = mock(StanFileRepo.class);
        mockWorkRepo = mock(WorkRepo.class);

        service = spy(new FileStoreServiceImp(mockConfig, clock, mockFileRepo, mockWorkRepo));
    }

    @ParameterizedTest
    @CsvSource({"folder/alpha,alpha,alpha", "/Robot/SW/R2D2 *&^%,R2D2 *&^%,R2D2", "Alpha/^%^&*,^%^&*, unnamed", ",unnamed,unnamed"})
    public void testSave(String name, String expectedName, String expectedPathFragment) throws IOException {
        Work work = new Work(500, "SGP500", null, null, null, null, Work.Status.active);
        MultipartFile data = mock(MultipartFile.class);
        LocalDateTime time = LocalDateTime.now(clock);
        when(data.getOriginalFilename()).thenReturn(name);

        when(mockFileRepo.save(any())).then(invocation -> {
            StanFile sf = invocation.getArgument(0);
            sf.setId(300);
            sf.setCreated(time);
            return sf;
        });
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        User user = EntityFactory.getUser();
        StanFile sf = service.save(user, data, work.getWorkNumber());
        String expectedPath = "DIR/"+time+"_"+expectedPathFragment;
        assertEquals(expectedPath, sf.getPath());
        assertEquals(expectedName, sf.getName());
        assertEquals(300, sf.getId());
        assertEquals(user, sf.getUser());

        verify(data).transferTo(Paths.get("/ROOT/"+expectedPath));
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
        service.deprecateOldFiles("name", 24, LocalDateTime.now());
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

        when(mockFileRepo.findAllActiveByWorkIdAndName(24, "name")).thenReturn(sfs);
        final LocalDateTime time = LocalDateTime.now();
        service.deprecateOldFiles("name", 24, time);
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
        Work work = new Work(500, "SGP500", null, null, null, null, Work.Status.active);
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        when(mockFileRepo.findAllActiveByWorkId(work.getId())).thenReturn(sfs);
        assertEquals(sfs, service.list(work.getWorkNumber()));
    }
}
