package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.User;

import javax.transaction.Transactional;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests {@link RegistrationFileController}
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestFileSectionRegister {
    @Autowired
    EntityCreator creator;
    @Autowired
    GraphQLTester tester;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Transactional
    public void testValidationError() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        var response = upload("testdata/section_reg.xlsx");
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        List<?> problems = (List<?>) map.get("problems");
        assertThat(problems).hasSize(3);
    }

    @Test
    @Transactional
    public void testNoRows() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        var response = upload("testdata/reg_empty.xlsx");
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        //noinspection unchecked
        List<String> problems = (List<String>) map.get("problems");
        assertThat(problems).containsOnly("No registrations requested.");
    }

    private MockHttpServletResponse upload(String filename) throws Exception {
        URL url = Resources.getResource(filename);
        byte[] bytes = Resources.toByteArray(url);
        MockMultipartFile file = new MockMultipartFile("file", bytes);
        MvcResult mvcr = tester.getMockMvc().perform(
                multipart("/register/section").file(file)
        ).andExpect(status().is4xxClientError()).andReturn();
        return mvcr.getResponse();
    }
}
