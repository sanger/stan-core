package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test {@link StainTypeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestStainTypeRepo {
    @Autowired
    StainTypeRepo stainTypeRepo;
    @Autowired
    UserRepo userRepo;
    @Autowired
    OperationTypeRepo opTypeRepo;
    @Autowired
    OperationRepo opRepo;

    @Transactional
    @Test
    public void testOperationStainTypes() {
        OperationType opType = opTypeRepo.getByName("Stain");
        User user = userRepo.save(new User(null, "user1", User.Role.normal));
        Operation op1 = opRepo.save(new Operation(null, opType, null, null, user));
        Operation op2 = opRepo.save(new Operation(null, opType, null, null, user));

        List<Integer> opIds = List.of(op1.getId(), op2.getId());

        for (var ots : stainTypeRepo.loadOperationStainTypes(opIds).values()) {
            assertThat(ots).isNullOrEmpty();
        }

        StainType st1 = stainTypeRepo.findByName("H&E").orElseThrow();
        StainType st2 = stainTypeRepo.findByName("Masson's Trichrome").orElseThrow();

        assertThat(st1.getId()).isLessThan(st2.getId()); // * this is a precondition of other assertions

        stainTypeRepo.saveOperationStainTypes(op1.getId(), List.of(st2, st1));

        var opStainTypes = stainTypeRepo.loadOperationStainTypes(opIds);
        assertThat(opStainTypes.get(op1.getId())).containsExactly(st1, st2); // sorted by stain type id (*)
        assertThat(opStainTypes.get(op2.getId())).isNullOrEmpty();

        opStainTypes = stainTypeRepo.loadOperationStainTypes(List.of(op2.getId()));
        assertNull(opStainTypes.get(op1.getId()));
        assertThat(opStainTypes.get(op2.getId())).isNullOrEmpty();

        stainTypeRepo.saveOperationStainTypes(op2.getId(), List.of(st1));
        opStainTypes = stainTypeRepo.loadOperationStainTypes(opIds);
        assertThat(opStainTypes.get(op1.getId())).containsExactly(st1, st2);
        assertThat(opStainTypes.get(op2.getId())).containsExactly(st1);
    }

}
