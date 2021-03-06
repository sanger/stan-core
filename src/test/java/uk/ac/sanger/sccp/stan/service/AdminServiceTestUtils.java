package uk.ac.sanger.sccp.stan.service;

import org.assertj.core.util.TriFunction;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.HasEnabled;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Utils class for tests of admin services
 * @author dr6
 */
abstract class AdminServiceTestUtils<E extends HasEnabled, R extends CrudRepository<E, ?>, S> {
    private static final String MISSING_STRING_MESSAGE = "MISSING_STRING_MESSAGE";
    private final String missingStringMessage;
    private final String entityTypeName;
    private final BiFunction<Integer, String, E> newEntityFunction;
    private final BiFunction<R, String, Optional<E>> repoFindFunction;
    R mockRepo;
    S service;

    AdminServiceTestUtils(String entityTypeName, BiFunction<Integer, String, E> newEntityFunction,
                          BiFunction<R, String, Optional<E>> repoFindFunction,
                          String missingStringMessage) {
        this.entityTypeName = entityTypeName;
        this.newEntityFunction = newEntityFunction;
        this.repoFindFunction = repoFindFunction;
        this.missingStringMessage = missingStringMessage;
    }

    E newEntity(Integer id, String string) {
        return newEntityFunction.apply(id, string);
    }

    void genericTestAddNew(BiFunction<S, String, E> serviceAddFunction,
                           String string, String existingEntityString, Exception expectedException, String expectedResultString) {
        when(repoFindFunction.apply(mockRepo, string)).thenReturn(Optional.ofNullable(existingEntityString).map(s -> newEntity(14, s)));
        if (expectedException != null) {
            assertException(expectedException, () -> serviceAddFunction.apply(service, string));
            verify(mockRepo, never()).save(any());
            return;
        }
        E expectedResult = newEntity(20, expectedResultString);
        when(mockRepo.save(any())).thenReturn(expectedResult);
        assertSame(expectedResult, serviceAddFunction.apply(service, string));
        verify(mockRepo).save(newEntity(null, expectedResultString));
    }

    String expectedMessage(Exception expectedException) {
        String expectedMessage = expectedException.getMessage();
        if (expectedMessage.equals(MISSING_STRING_MESSAGE)) {
            return this.missingStringMessage;
        }
        return expectedMessage.replace("<ENTITY>", this.entityTypeName);
    }

    void assertException(Exception exception, Executable executable) {
        assertThat(assertThrows(exception.getClass(), executable)).hasMessage(expectedMessage(exception));
    }

    static Stream<Arguments> addNewArgs() {
        Exception missingStringException = new IllegalArgumentException(MISSING_STRING_MESSAGE);
        return Stream.of(
                Arguments.of("Alpha", null, null, "Alpha"),
                Arguments.of("   Alpha\t\n", null, null, "Alpha"),
                Arguments.of("!Alpha", null, new IllegalArgumentException("string \"!Alpha\" contains invalid characters \"!\"."), null),
                Arguments.of(null, null, missingStringException, null),
                Arguments.of("   \n", null, missingStringException, null),
                Arguments.of("Alpha", "Alpha", new EntityExistsException("<ENTITY> already exists: Alpha"), null)
        );
    }

    void genericTestSetEnabled(TriFunction<S, String, Boolean, E> serviceSetEnabledFunction,
                               String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        String name = (string==null ? null : string.trim());
        E entity = (oldValue==null ? null : newEntity(17, name));
        if (entity!=null) {
            entity.setEnabled(oldValue);
        }
        when(repoFindFunction.apply(mockRepo, name)).thenReturn(Optional.ofNullable(entity));
        if (expectedException!=null) {
            assertException(expectedException, () -> serviceSetEnabledFunction.apply(service, string, newValue));
            verify(mockRepo, never()).save(any());
            return;
        }
        assert entity != null;
        when(mockRepo.save(any())).thenReturn(entity);
        assertSame(entity, serviceSetEnabledFunction.apply(service, string, newValue));
        assertEquals(newValue, entity.isEnabled());
        if (newValue==oldValue) {
            verify(mockRepo, never()).save(any());
        } else {
            verify(mockRepo).save(entity);
        }
    }

    static Stream<Arguments> setEnabledArgs() {
        Exception missingStringException = new IllegalArgumentException(MISSING_STRING_MESSAGE);
        Exception entityNotFoundAlphaException = new EntityNotFoundException("<ENTITY> not found: Alpha");
        return Stream.of(
                Arguments.of(null, true, null, missingStringException),
                Arguments.of("  \t\n ", false, null, missingStringException),
                Arguments.of("Alpha", true, null, entityNotFoundAlphaException),
                Arguments.of("   Alpha\n\t", false, null, entityNotFoundAlphaException),
                Arguments.of("  Alpha", true, true, null),
                Arguments.of("Alpha  ", true, false, null),
                Arguments.of("Alpha", false, true, null),
                Arguments.of("Alpha", false, false, null)
        );
    }

    static Validator<String> simpleValidator() {
        return new StringValidator("string", 1, 16, StringValidator.CharacterType.ALPHA);
    }
}
