package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.WorkEventRepo;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests storing files.
 * @see StanFile
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestFileStore {
    @Autowired
    private StanFileConfig stanFileConfig;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private WorkEventRepo workEventRepo;

    @Test
    @Transactional
    public void testFileStore() throws Exception {
        Path directory = Paths.get(stanFileConfig.getRoot(), stanFileConfig.getDir());
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
        }
        Work work = entityCreator.createWork(null, null, null, null, null);
        String workNumber = work.getWorkNumber();
        Work work2 = entityCreator.createWorkLike(work);
        String workNumber2 = work2.getWorkNumber();

        assertThat(listFiles(workNumber)).isEmpty();
        String username = "user1";
        User user = entityCreator.createUser(username, User.Role.enduser);
        workEventRepo.saveAll(Stream.of(work, work2)
                .map(w -> new WorkEvent(w, WorkEvent.Type.create, user, null))
                .collect(toList()));

        tester.setUser(user);

        final String filename1 = "stanfile.txt";
        final String fileContent1 = "Hello\nworld";
        String url1 = upload(filename1, fileContent1, workNumber, workNumber2);
        String url1b = nextFileUrl(url1);

        assertEquals(fileContent1, download(url1, filename1));

        var filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(1);
        assertFileData(filesData.get(0), filename1, url1, username, workNumber);
        filesData = listFiles(workNumber2);
        assertThat(filesData).hasSize(1);
        assertFileData(filesData.get(0), filename1, url1b, username, workNumber2);

        String fileContent2 = "Alabama\nAlaska";
        final String filename2 = "stanfile2.txt";
        String url2 = upload(filename2, fileContent2, workNumber);

        assertEquals(fileContent2, download(url2, filename2));

        filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(2);
        if (filesData.get(0).get("name").equals(filename2)) {
            filesData = List.of(filesData.get(1), filesData.get(0));
        }

        assertFileData(filesData.get(0), filename1, url1, username, workNumber);
        assertFileData(filesData.get(1), filename2, url2, username, workNumber);

        String fileContentB = "Goodbye\nWorld";
        url1 = upload(filename1, fileContentB, workNumber);

        assertEquals(fileContentB, download(url1, filename1));
        filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(2);
        int index0 = filesData.get(0).get("name").equals(filename1) ? 0 : 1;

        assertFileData(filesData.get(index0), filename1, url1, username, workNumber);
        assertFileData(filesData.get(1-index0), filename2, url2, username, workNumber);

        deleteTestFiles(directory);
    }

    private static String nextFileUrl(String url) {
        int i = url.lastIndexOf('/')+1;
        int id = Integer.parseInt(url.substring(i));
        return url.substring(0, i) + (id+1);
    }

    private List<Map<String, ?>> listFiles(String workNumber) throws Exception {
        String query = tester.readGraphQL("listfiles.graphql").replace("[]", "[\""+workNumber+"\"]");
        Object result = tester.post(query);
        return chainGet(result, "data", "listFiles");
    }

    private String upload(String filename, String content, String... workNumbers) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, MediaType.TEXT_PLAIN_VALUE, content.getBytes()
        );
        return tester.getMockMvc().perform(
                multipart("/files").file(file).queryParam("workNumber", workNumbers)
        ).andExpect(status().isCreated()).andReturn().getResponse().getHeader("location");
    }

    private String download(String downloadUrl, String filename) throws Exception {
        var r = tester.getMockMvc().perform(MockMvcRequestBuilders.get(downloadUrl))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertThat(r.getHeader("Content-Disposition")).contains(filename);
        return r.getContentAsString();
    }

    private void assertFileData(Map<String, ?> data, String filename, String url, String username, String workNumber) {
        assertNotNull(data.get("created"));
        assertEquals(filename, data.get("name"));
        assertEquals(url, data.get("url"));
        assertEquals(username, chainGet(data, "user", "username"));
        assertEquals(workNumber, chainGet(data, "work", "workNumber"));
    }

    private void deleteTestFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            Iterable<Path> pathIter = files::iterator;
            for (Path path : pathIter) {
                Files.delete(path);
            }
        }
    }
}
