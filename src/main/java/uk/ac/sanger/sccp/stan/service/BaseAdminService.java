package uk.ac.sanger.sccp.stan.service;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.HasEnabled;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.trimAndRequire;

/**
 * Base class for a couple of required services.
 * @author dr6
 */
public abstract class BaseAdminService<E extends HasEnabled, R extends CrudRepository<E, ?>> {
    final String entityTypeName;
    final R repo;
    final String missingFieldMessage;
    final Validator<String> stringValidator;

    protected BaseAdminService(R repo, String entityTypeName, String stringFieldName, Validator<String> stringValidator) {
        this.repo = repo;
        this.entityTypeName = entityTypeName;
        this.missingFieldMessage = stringFieldName+" not supplied.";
        this.stringValidator = stringValidator;
    }

    /**
     * Creates a new item representing the given string
     * @param string the string. This will be trimmed before it is used.
     * @return the new item
     * @exception IllegalArgumentException if the string is blank or null, or fails validation
     * @exception EntityExistsException A matching item already exists
     */
    public E addNew(String string) {
        validateEntity(string);
        return repo.save(newEntity(string));
    }

    public void validateEntity(String identifier) {
        validateIdentifier(identifier);
        validateUniqueness(identifier);
    }

    public void validateIdentifier(String identifier) {
        identifier = trimAndRequire(identifier, missingFieldMessage);
        if (this.stringValidator!=null) {
            this.stringValidator.checkArgument(identifier);
        }
    }

    private void validateUniqueness(String identifier) {
        Optional<E> entity = findEntity(repo, identifier);
        if (entity.isPresent()) {
            throw new EntityExistsException(entityTypeName+" already exists: "+identifier);
        }
    }

    /**
     * Sets the enabled field of an item identified by the given string.
     * If the item's {@link HasEnabled#isEnabled} already matches the {@code enabled} argument,
     * no update is recorded
     * @param string the string. This will be trimmed before it is used.
     * @param enabled whether the item should be enabled
     * @return the updated item
     * @exception IllegalArgumentException if the string is blank or null
     * @exception EntityNotFoundException if no such entity is found
     */
    public E setEnabled(String string, boolean enabled) {
        final String stringValue = trimAndRequire(string, missingFieldMessage);
        E entity = findEntity(repo, stringValue).orElseThrow(() -> new EntityNotFoundException(entityTypeName+" not found: "+stringValue));
        if (entity.isEnabled()==enabled) {
            return entity;
        }
        entity.setEnabled(enabled);
        return repo.save(entity);
    }

    /**
     * Returns a new unpersisted object
     * @param string the string for the new object
     * @return the new object
     */
    protected abstract E newEntity(String string);

    /**
     * Looks up an entity
     * @param repo the repo to use
     * @param string the string to use
     * @return an optional that will contain the entity if it is found
     */
    protected abstract Optional<E> findEntity(R repo, String string);
}
