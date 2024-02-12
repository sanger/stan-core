package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.User;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests {@link UserRepo} */
@SpringBootTest
@ActiveProfiles(profiles = "test")
class TestUserRepo {
    @Autowired
    UserRepo userRepo;

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    @Transactional
    void testFindByUsername(boolean exists) {
        if (exists) {
            User user = userRepo.save(new User("bananas", User.Role.normal));
            assertThat(userRepo.findByUsername("BANANAS")).contains(user);
        } else {
            assertThat(userRepo.findByUsername("BANANAS")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    @Transactional
    void testGetByUsername(boolean exists) {
        if (exists) {
            User user = userRepo.save(new User("bananas", User.Role.normal));
            assertEquals(user, userRepo.getByUsername("BANANAS"));
        } else {
            assertThat(assertThrows(EntityNotFoundException.class, () -> userRepo.getByUsername("BANANAS")))
                    .hasMessage("User not found: \"BANANAS\"");
        }
    }

    @Test
    @Transactional
    void testFindByRole() {
        userRepo.deleteAll();
        var adminUsers = userRepo.saveAll(IntStream.rangeClosed(1,2)
                .mapToObj(i -> new User("admin"+i, User.Role.admin))
                .toList());
        var normalUsers = userRepo.saveAll(IntStream.rangeClosed(1,2)
                .mapToObj(i -> new User("user"+i, User.Role.normal))
                .toList());
        assertThat(userRepo.findAllByRole(User.Role.admin)).containsExactlyInAnyOrderElementsOf(adminUsers);
        assertThat(userRepo.findAllByRole(User.Role.normal)).containsExactlyInAnyOrderElementsOf(normalUsers);
        assertThat(userRepo.findAllByRole(User.Role.disabled)).isEmpty();
    }
}