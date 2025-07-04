package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.transaction.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests admin mutations of various types of entity
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestAdminMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private SpeciesRepo speciesRepo;
    @Autowired
    private ReleaseDestinationRepo releaseDestinationRepo;
    @Autowired
    private ReleaseRecipientRepo releaseRecipientRepo;
    @Autowired
    private HmdmcRepo hmdmcRepo;
    @Autowired
    private DestructionReasonRepo destructionReasonRepo;
    @Autowired
    private ProjectRepo projectRepo;
    @Autowired
    private CostCodeRepo costCodeRepo;
    @Autowired
    private FixativeRepo fixativeRepo;
    @Autowired
    private WorkTypeRepo workTypeRepo;
    @Autowired
    private SolutionRepo solutionRepo;
    @Autowired
    private ProgramRepo programRepo;
    @Autowired
    private OmeroProjectRepo omeroProjectRepo;
    @Autowired
    private SlotRegionRepo slotRegionRepo;
    @Autowired
    private ProbePanelRepo probePanelRepo;
    @Autowired
    private BioRiskRepo bioRiskRepo;

    @Test
    @Transactional
    public void testEquipmentAdmin() throws Exception {
        String mutation = "mutation { addEquipment(name: \"Bananas\", category: \"SCANNER\") { id, name, category, enabled }}";
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        Object result = tester.post(mutation);
        Integer eqId = chainGet(result, "data", "addEquipment", "id");
        assertNotNull(eqId);
        assertEquals(Map.of("id", eqId, "name", "Bananas", "category", "scanner", "enabled", true),
                chainGet(result, "data", "addEquipment"));

        mutation = "mutation { setEquipmentEnabled(equipmentId: "+eqId+", enabled: false) { id, name, category, enabled }}";
        result = tester.post(mutation);
        assertEquals(Map.of("id", eqId, "name", "Bananas", "category", "scanner", "enabled", false),
                chainGet(result, "data", "setEquipmentEnabled"));
    }

    @Test
    @Transactional
    public void testProbePanelAdmin() throws Exception {
        String mutation = "mutation { addProbePanel(type: xenium, name: \"bananas\") { type, name, enabled }}";
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        Object result = tester.post(mutation);
        Map<String, ?> ppData = chainGet(result, "data", "addProbePanel");
        assertEquals("bananas", ppData.get("name"));
        assertEquals("xenium", ppData.get("type"));
        assertEquals(Boolean.TRUE, ppData.get("enabled"));
        ProbePanel pp = probePanelRepo.getByTypeAndName(ProbePanel.ProbeType.xenium, "bananas");
        assertEquals("bananas", pp.getName());
        assertEquals(ProbePanel.ProbeType.xenium, pp.getType());
        assertTrue(pp.isEnabled());

        mutation = "mutation { setProbePanelEnabled(type: xenium, name: \"bananas\", enabled: false) { type, name, enabled }}";
        result = tester.post(mutation);
        ppData = chainGet(result, "data", "setProbePanelEnabled");
        assertEquals("bananas", ppData.get("name"));
        assertEquals("xenium", ppData.get("type"));
        assertEquals(Boolean.FALSE, ppData.get("enabled"));
        pp = probePanelRepo.getByTypeAndName(ProbePanel.ProbeType.xenium, "bananas");
        assertFalse(pp.isEnabled());
    }

    private <E extends HasEnabled> void testGenericAddNewAndSetEnabled(String entityTypeName, String fieldName,
                                                                      String string,
                                                                      Function<String, Optional<E>> findFunction,
                                                                      Function<E, String> stringFunction,
                                                                      String queryString) throws Exception {
        String mutation = tester.readGraphQL("genericaddnew.graphql")
                .replace("Species", entityTypeName).replace("name", fieldName)
                .replace("Unicorn", string);
        tester.setUser(entityCreator.createUser("dr6"));
        Object result = tester.post(mutation);
        Map<String, ?> newEntityMap = Map.of(fieldName, string, "enabled", true);
        assertEquals(newEntityMap, chainGet(result, "data", "add"+entityTypeName));
        E entity = findFunction.apply(string).orElseThrow();
        assertEquals(string, stringFunction.apply(entity));
        assertTrue(entity.isEnabled());

        result = tester.post(String.format("query { %s { %s, enabled }}", queryString, fieldName));
        assertThat(chainGetList(result, "data", queryString)).contains(newEntityMap);

        mutation = tester.readGraphQL("genericsetenabled.graphql")
                .replace("Species", entityTypeName).replace("name", fieldName).replace("Unicorn", string);
        result = tester.post(mutation);
        newEntityMap = Map.of(fieldName, string, "enabled", false);
        assertEquals(newEntityMap, chainGet(result, "data", "set"+entityTypeName+"Enabled"));
        entity = findFunction.apply(string).orElseThrow();
        assertFalse(entity.isEnabled());
    }

    @Test
    @Transactional
    public void testAddSpeciesAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Species", "name", "Unicorn", speciesRepo::findByName, Species::getName, "species");
    }
    @Test
    @Transactional
    public void testAddReleaseDestinationAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("ReleaseDestination", "name", "Venus", releaseDestinationRepo::findByName, ReleaseDestination::getName, "releaseDestinations");
    }
    @Test
    @Transactional
    public void testAddReleaseRecipientAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("ReleaseRecipient", "username", "mekon", releaseRecipientRepo::findByUsername, ReleaseRecipient::getUsername, "releaseRecipients");
    }
    @Test
    @Transactional
    public void testAddHmdmcAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Hmdmc", "hmdmc", "12/345", hmdmcRepo::findByHmdmc, Hmdmc::getHmdmc, "hmdmcs");
    }
    @Test
    @Transactional
    public void testAddDestructionReasonAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("DestructionReason", "text", "Dropped.", destructionReasonRepo::findByText, DestructionReason::getText, "destructionReasons");
    }
    @Test
    @Transactional
    public void testAddNewProjectAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Project", "name", "Stargate", projectRepo::findByName, Project::getName, "projects");
    }
    @Test
    @Transactional
    public void testAddNewCostCodeAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("CostCode", "code", "S12345", costCodeRepo::findByCode, CostCode::getCode, "costCodes");
    }
    @Test
    @Transactional
    public void testAddNewFixativeAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Fixative", "name", "Bananas", fixativeRepo::findByName, Fixative::getName, "fixatives");
    }
    @Test
    @Transactional
    public void testAddNewWorkTypeAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("WorkType", "name", "Drywalling", workTypeRepo::findByName, WorkType::getName, "workTypes");
    }
    @Test
    @Transactional
    public void testAddNewSolutionAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Solution", "name", "Glue", solutionRepo::findByName, Solution::getName, "solutions");
    }
    @Test
    @Transactional
    public void testAddNewProgramAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("Program", "name", "Hello", programRepo::findByName, Program::getName, "programs");
    }

    @Test
    @Transactional
    public void testAddNewOmeroProjectAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("OmeroProject", "name", "Hello", omeroProjectRepo::findByName, OmeroProject::getName, "omeroProjects");
    }

    @Test
    @Transactional
    public void testAddNewSlotRegionAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("SlotRegion", "name", "North", slotRegionRepo::findByName, SlotRegion::getName, "slotRegions");
    }

    @Test
    @Transactional
    public void testAddNewBioRiskAndSetEnabled() throws Exception {
        testGenericAddNewAndSetEnabled("BioRisk", "code", "SIBAGMRA12_09v1", bioRiskRepo::findByCode, BioRisk::getCode, "bioRisks");
    }
}
