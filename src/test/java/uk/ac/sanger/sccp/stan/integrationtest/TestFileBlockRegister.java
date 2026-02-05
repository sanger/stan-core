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
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.register.*;
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

    @Test
    @Transactional
    public void testClashes() throws Exception {
        User user = creator.createUser("user1");
        tester.setUser(user);
        Tissue tissue1 = creator.createTissue(null, "EXT1");
        Tissue tissue2 = creator.createTissue(tissue1.getDonor(), "EXT2");
        Sample sample1 = creator.createBlockSample(tissue1);
        Sample sample2 = creator.createBlockSample(tissue2);
        Labware lw1 = creator.createTube("STAN-X", sample1);
        Labware lw2 = creator.createTube("STAN-Y", sample2);
        when(mockRegService.register(any(), any())).thenReturn(RegisterResult.clashes(
                List.of(new RegisterClash(tissue1, List.of(lw1)), new RegisterClash(tissue2, List.of(lw2)))
        ));
        var response = upload("testdata/block_reg_existing.xlsx", null, null, true);
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        Object clashesObj = map.get("clashes");
        assertNotNull(clashesObj);
        //noinspection unchecked
        List<Map<String, Map<String, ?>>> clashes = (List<Map<String, Map<String, ?>>>) clashesObj;
        assertThat(clashes).hasSize(2);
        assertThat(clashes.stream()
                .map(clash -> (String) clash.get("tissue").get("externalName")))
                .containsExactlyInAnyOrder("EXT1", "EXT2");
        for (var clash : clashes) {
            String bc = clash.get("tissue").get("externalName").equals("EXT1") ? "STAN-X" : "STAN-Y";
            assertEquals(List.of(Map.of("barcode", bc,"labwareType", Map.of("name", "Tube"))),
                    clash.get("labware"));
        }
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
        var response = upload("testdata/block_reg_existing.xlsx", List.of("Ext17"), null, false);
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(mockRegService).register(eq(user), requestCaptor.capture());
        RegisterRequest request = requestCaptor.getValue();
        assertThat(request.getBlocks()).hasSize(2);
        assertTrue(request.getBlocks().get(0).isExistingTissue());
        assertFalse(request.getBlocks().get(1).isExistingTissue());
        assertEquals("Bad reg", getProblem(map));
    }

    /**
     * This test only goes as far as the service level, but checks that external names to ignore
     * are received in the post request and used to filter the register request.
     */
    @Test
    @Transactional
    public void testIgnoreExtNames() throws Exception {
        User user = creator.createUser("user1");
        creator.createBioRisk("risk1");
        creator.createBioRisk("risk2");
        tester.setUser(user);
        when(mockRegService.register(any(), any())).thenThrow(new ValidationException(List.of("Bad reg")));
        var response = upload("testdata/block_reg_existing.xlsx", null, List.of("Ext17"), false);
        var map = objectMapper.readValue(response.getContentAsString(), Map.class);
        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(mockRegService).register(eq(user), requestCaptor.capture());
        RegisterRequest request = requestCaptor.getValue();
        assertThat(request.getBlocks()).hasSize(1);
        BlockRegisterRequest_old br = request.getBlocks().getFirst();
        assertEquals("EXT18", br.getExternalIdentifier());
        assertEquals("Bad reg", getProblem(map));
        assertEquals("risk1", br.getBioRiskCode());
    }

    private MockHttpServletResponse upload(String filename) throws Exception {
        return upload(filename, null, null, false);
    }

    private MockHttpServletResponse upload(String filename, List<String> existing, List<String> ignore, boolean success) throws Exception {
        URL url = Resources.getResource(filename);
        byte[] bytes = Resources.toByteArray(url);
        MockMultipartFile file = new MockMultipartFile("file", bytes);
        final MockMultipartHttpServletRequestBuilder rb = multipart("/register/block").file(file);
        if (existing!=null) {
            rb.param("existingExternalNames", existing.toArray(String[]::new));
        }
        if (ignore!=null) {
            rb.param("ignoreExternalNames", ignore.toArray(String[]::new));
        }
        MvcResult mvcr = tester.getMockMvc()
                .perform(rb)
                .andExpect(success ? status().is2xxSuccessful() : status().is4xxClientError())
                .andReturn();
        return mvcr.getResponse();
    }

    @SuppressWarnings("unchecked")
    private static String getProblem(Map<?, ?> map) {
        List<String> problems = (List<String>) map.get("problems");
        assertThat(problems).hasSize(1);
        return problems.getFirst();
    }
}
