package uk.ac.sanger.sccp.utils;

import uk.ac.sanger.sccp.stan.model.HasIntId;
import uk.ac.sanger.sccp.stan.model.HasName;

import java.util.*;
import java.util.function.Predicate;

/**
 * Utility for filtering entities identified by their name and cached by their id.
 * Each entity given is checked against the set of allowed names, and then
 * its id is used to check any subsequent times.
 * @author dr6
 */
public class EntityNameFilter<E extends HasName&HasIntId> implements Predicate<E> {
    private final Set<String> lowerCaseNames;
    private final Map<Integer, Boolean> idIncluded;

    public EntityNameFilter(Set<String> lowerCaseNames) {
        this.lowerCaseNames = lowerCaseNames;
        this.idIncluded = new HashMap<>();
    }

    @Override
    public boolean test(E entity) {
        Boolean included = idIncluded.get(entity.getId());
        if (included==null) {
            included = lowerCaseNames.contains(entity.getName().toLowerCase());
            idIncluded.put(entity.getId(), included);
        }
        return included;
    }
}
