package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.StainType;

import java.util.*;

import static java.util.stream.Collectors.toSet;

public interface StainTypeRepo extends CrudRepository<StainType, Integer> {
    List<StainType> findAllByEnabled(boolean enabled);

    Optional<StainType> findByName(String name);

    List<StainType> findAllByNameIn(Collection<String> names);

    @Query(value="select operation_id as opId, stain_type_id as stainTypeId from stain where operation_id in (?1) ORDER BY stain_type_id", nativeQuery=true)
    List<Object[]> _stains(Collection<Integer> opIds);

    /**
     * Loads the stain types for the specified operations.
     * Operations without stain types may be omitted from the returned map.
     * @param opIds the ids of operations
     * @return a map from operation id to associated stain types
     */
    default Map<Integer, List<StainType>> loadOperationStainTypes(Collection<Integer> opIds) {
        List<Object[]> data = _stains(opIds);
        if (data.isEmpty()) {
            return Map.of();
        }
        Set<Integer> stainIds = data.stream()
                .map(obj -> (Integer) obj[1])
                .collect(toSet());
        var stainTypes = findAllById(stainIds);
        Map<Integer, StainType> stainTypeMap = new HashMap<>();
        for (var stainType : stainTypes) {
            stainTypeMap.put(stainType.getId(), stainType);
        }
        Map<Integer, List<StainType>> opStainTypes = new HashMap<>();
        for (Object[] item : data) {
            Integer opId = (Integer) item[0];
            Integer stainTypeId = (Integer) item[1];
            StainType stainType = stainTypeMap.get(stainTypeId);
            opStainTypes.computeIfAbsent(opId, k -> new ArrayList<>()).add(stainType);
        }
        return opStainTypes;
    }

    @Modifying
    @Query(value="insert into stain (operation_id, stain_type_id) values ((?1), (?2))", nativeQuery=true)
    void _saveStain(Integer operationId, Integer stainTypeId);

    /**
     * Saves the given stain types in association with the specified operation.
     * Old associations are not cleared. Clashes will cause exceptions.
     * @param opId the id of the operation
     * @param stainTypes the stain types to associate with the operation
     */
    default void saveOperationStainTypes(Integer opId, Collection<StainType> stainTypes) {
        for (StainType stainType : stainTypes) {
            _saveStain(opId, stainType.getId());
        }
    }
}
