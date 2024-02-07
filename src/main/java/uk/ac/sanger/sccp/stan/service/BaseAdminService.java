package uk.ac.sanger.sccp.stan.service;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.HasEnabled;
import uk.ac.sanger.sccp.stan.model.User;

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
    final Transactor transactor;
    final AdminNotifyService notifyService;

    protected BaseAdminService(R repo, String entityTypeName, String stringFieldName,
                               Validator<String> stringValidator, Transactor transactor,
                               AdminNotifyService notifyService) {
        this.repo = repo;
        this.entityTypeName = entityTypeName;
        this.missingFieldMessage = stringFieldName+" not supplied.";
        this.stringValidator = stringValidator;
        this.transactor = transactor;
        this.notifyService = notifyService;
    }

    /**
     * Creates a new item representing the given string.
     * If the creating user is an {@link User.Role#enduser enduser} then a notification is sent by email to admin users
     * @param creator the user responsible for creating the new item
     * @param string the string. This will be trimmed before it is used
     * @return the new item
     * @exception IllegalArgumentException if the string is blank or null, or fails validation
     * @exception EntityExistsException A matching item already exists
     */
    public E addNew(User creator, String string) {
        E newValue = transactor.transact("Add "+entityTypeName,
                () -> repo.save(newEntity(validateEntity(string))));
        if (creator != null && creator.getRole() == User.Role.enduser) {
            sendNewEntityEmail(creator, newValue);
        }
        return newValue;
    }

    /**
     * Validates the given identifier for its value and uniqueness.
     * @param identifier the identifier for a new entity
     * @return the sanitised version of the identifier
     * @exception IllegalArgumentException if validation fails
     * @exception EntityExistsException if the identifier is already in use
     */
    public String validateEntity(String identifier) throws IllegalArgumentException, EntityExistsException {
        identifier = validateIdentifier(identifier);
        validateUniqueness(identifier);
        return identifier;
    }

    /**
     * Validates the given identifier for its value
     * @param identifier the identifier for a new entity
     * @return the sanitised version of the identifier
     * @exception IllegalArgumentException if validation fails
     */
    public String validateIdentifier(String identifier) throws IllegalArgumentException {
        identifier = trimAndRequire(identifier, missingFieldMessage);
        if (this.stringValidator!=null) {
            this.stringValidator.checkArgument(identifier);
        }
        return identifier;
    }

    /**
     * Checks if an entity with the given identifier exists already
     * @param identifier the new identifier
     * @exception EntityExistsException if such an entity already exists
     */
    private void validateUniqueness(String identifier) throws EntityExistsException {
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
    public E setEnabled(String string, boolean enabled) throws IllegalArgumentException, EntityNotFoundException {
        final String stringValue = trimAndRequire(string, missingFieldMessage);
        E entity = findEntity(repo, stringValue).orElseThrow(() -> new EntityNotFoundException(entityTypeName+" not found: "+stringValue));
        if (entity.isEnabled()==enabled) {
            return entity;
        }
        entity.setEnabled(enabled);
        return repo.save(entity);
    }

    /**
     * Sends an email about the creation of a given item to admin users.
     * @param creator the user who created the item
     * @param item the new item created
     */
    public void sendNewEntityEmail(User creator, E item) {
        String notification = this.notificationName();
        if (notification!=null) {
            String body = String.format("User %s has created a new %s on %%service: %s",
                    creator.getUsername(), entityTypeName, item);
            notifyService.issue(notification, "%service new "+entityTypeName, body);
        }
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

    /**
     * If this returns a string, it will be used as the name of the notification sent to admin users.
     * By default it returns null, and no notification will be sent.
     * @return the name of the notification
     */
    public String notificationName() {
        return null;
    }
}
