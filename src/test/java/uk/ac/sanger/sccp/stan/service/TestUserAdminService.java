package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Tests {@link UserAdminService}
 * @author dr6
 */
public class TestUserAdminService {
    private UserRepo mockUserRepo;
    private UserAdminService service;

    @BeforeEach
    void setup() {
        mockUserRepo = mock(UserRepo.class);
        Validator<String> usernameValidator = new StringValidator("username", 1, 16, StringValidator.CharacterType.ALPHA);
        service = new UserAdminService(mockUserRepo, usernameValidator);
    }

    @ParameterizedTest
    @MethodSource("addNormalUserArgs")
    public void testAddNormalUser(User creator, String username, String sanitisedUsername, Exception expectedException) {
        User existingUser;
        if (expectedException instanceof EntityExistsException) {
            existingUser = new User(13, sanitisedUsername, User.Role.normal);
        } else {
            existingUser = null;
        }
        when(mockUserRepo.findByUsername(sanitisedUsername)).thenReturn(Optional.ofNullable(existingUser));
        if (expectedException != null) {
            assertException(expectedException, () -> service.addNormalUser(creator, username));
            verify(mockUserRepo, never()).save(any());
            return;
        }
        User newUser = new User(14, sanitisedUsername, User.Role.normal);
        when(mockUserRepo.save(any())).thenReturn(newUser);
        assertSame(newUser, service.addNormalUser(creator, username));
        verify(mockUserRepo).save(new User(null, sanitisedUsername, User.Role.normal));
    }

    static Stream<Arguments> addNormalUserArgs() {
        User creator = EntityFactory.getUser();
        return Stream.of(
                Arguments.of(creator, "Alpha", "alpha", null),
                Arguments.of(creator, "   Alpha\t  \n", "alpha", null),
                Arguments.of(creator, "!Alpha", "!alpha", new IllegalArgumentException("username \"!alpha\" contains invalid characters \"!\".")),
                Arguments.of(creator, null, null, new IllegalArgumentException("Username not supplied.")),
                Arguments.of(creator, "  \t\n  ", null, new IllegalArgumentException("Username not supplied.")),
                Arguments.of(creator, "  ALPHA ", "alpha", new EntityExistsException("User already exists: alpha"))
        );
    }

    @ParameterizedTest
    @MethodSource("setUserRoleArgs")
    public void testSetUserRole(String username, User.Role newRole, User.Role oldRole,
                                Exception expectedException) {
        String sanitisedUsername = (username==null ? null : username.trim());
        User user = oldRole==null ? null : new User(13, sanitisedUsername, oldRole);
        if (user!=null) {
            when(mockUserRepo.getByUsername(sanitisedUsername)).thenReturn(user);
        } else {
            when(mockUserRepo.getByUsername(any())).then(invocation -> {
                String arg = invocation.getArgument(0);
                throw new EntityNotFoundException("User not found: "+repr(arg));
            });
        }
        when(mockUserRepo.findByUsername(sanitisedUsername)).thenReturn(Optional.ofNullable(user));
        if (expectedException!=null) {
            assertException(expectedException, () -> service.setUserRole(username, newRole));
            verify(mockUserRepo, never()).save(any());
            if (user!=null) {
                assertEquals(oldRole, user.getRole());
            }
            return;
        }
        when(mockUserRepo.save(any())).then(Matchers.returnArgument());
        assertSame(user, service.setUserRole(username, newRole));
        assert user != null;
        if (newRole==oldRole) {
            verify(mockUserRepo, never()).save(any());
        } else {
            verify(mockUserRepo).save(user);
        }
        assertEquals(newRole, user.getRole());
    }

    static Stream<Arguments> setUserRoleArgs() {
        Exception noUsername = new IllegalArgumentException("Username not supplied.");
        return Stream.of(
                Arguments.of("  alpha\n\t  ", User.Role.disabled, User.Role.normal, null),
                Arguments.of("alpha", User.Role.normal, User.Role.disabled, null),
                Arguments.of("alpha", User.Role.normal, User.Role.normal, null),
                Arguments.of("alpha", User.Role.disabled, User.Role.disabled, null),
                Arguments.of(null, User.Role.normal, null, noUsername),
                Arguments.of("  \t\n  ", User.Role.normal, null, noUsername),
                Arguments.of("alpha", User.Role.normal, null, new EntityNotFoundException("User not found: \"alpha\"")),
                Arguments.of("alpha", null, null, new NullPointerException("Role not supplied.")),
                Arguments.of("alpha", User.Role.admin, User.Role.admin, null),
                Arguments.of("alpha", User.Role.admin, User.Role.normal, new IllegalArgumentException("Cannot promote a user to admin.")),
                Arguments.of("alpha", User.Role.normal, User.Role.admin, new IllegalArgumentException("Cannot set the role of an admin user."))
        );
    }

    private static void assertException(Exception exception, Executable executable) {
        assertThat(assertThrows(exception.getClass(), executable)).hasMessage(exception.getMessage());
    }
}
