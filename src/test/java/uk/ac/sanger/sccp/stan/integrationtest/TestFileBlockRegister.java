package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import uk.ac.sanger.sccp.stan.*;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.IRegisterService;

import javax.transaction.Transactional;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
public class TestFileBlockRegister {
    @Autowired
    EntityCreator creator;
    @Autowired
    GraphQLTester tester;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    IRegisterService<RegisterRequest> mockRegService;

    @Test
    @Transactional
    public void testValidationError() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        var response = upload("testdata/block_reg.xlsx");
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(getProblem(map)).matches(".*work number.*");
        verifyNoInteractions(mockRegService);
    }

    @Test
    @Transactional
    public void testNoRows() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        var response = upload("testdata/reg_empty.xlsx");
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("No registrations requested.", getProblem(map));
        verifyNoInteractions(mockRegService);
    }

    /**
     * This test only goes as far as the service level, but checks that existing external names
     * are received in the post request and incorporated into the register request.
     */
    @Test
    @Transactional
    public void testExistingExtNames() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        when(mockRegService.register(any(), any())).thenThrow(new ValidationException(List.of("Bad reg")));
        var response = upload("testdata/block_reg_existing.xlsx", List.of("Ext17"));
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(mockRegService).register(eq(user), requestCaptor.capture());
        RegisterRequest request = requestCaptor.getValue();
        assertThat(request.getBlocks()).hasSize(2);
        assertTrue(request.getBlocks().get(0).isExistingTissue());
        assertFalse(request.getBlocks().get(1).isExistingTissue());
        assertEquals("Bad reg", getProblem(map));
    }

    private MockHttpServletResponse upload(String filename) throws Exception {
        return upload(filename, null);
    }

    private MockHttpServletResponse upload(String filename, List<String> existing) throws Exception {
        URL url = Resources.getResource(filename);
        byte[] bytes = Resources.toByteArray(url);
        MockMultipartFile file = new MockMultipartFile("file", bytes);
        final MockMultipartHttpServletRequestBuilder rb = multipart("/register/block").file(file);
        if (existing!=null) {
            rb.param("existingExternalNames", existing.toArray(String[]::new));
        }
        MvcResult mvcr = tester.getMockMvc().perform(rb).andExpect(status().is4xxClientError()).andReturn();
        return mvcr.getResponse();
    }

    @SuppressWarnings("unchecked")
    private static String getProblem(Map<?, ?> map) {
        List<String> problems = (List<String>) map.get("problems");
        assertThat(problems).hasSize(1);
        return problems.getFirst();
    }
}
