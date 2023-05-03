package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.taglayout.TagLayout;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TagLayoutRepo extends CrudRepository<TagLayout, Integer> {

    Optional<TagLayout> findByName(String name);

    @Query(value="SELECT tag_layout_id FROM reagent_plate_type_tag_layout WHERE plate_type=?1",
            nativeQuery=true)
    Integer layoutIdForReagentPlateType(String plateType);

    /**
     * Gets the named tag layout
     * @param name the name of the layout
     * @return the specified layout
     * @exception EntityNotFoundException if no such layout is found
     */
    default TagLayout getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Tag layout not found: "+repr(name)));
    }

    /**
     * Looks up tag layouts by their id
     * @param ids ids to look up
     * @return a map of id to tag layout
     * @exception EntityNotFoundException if any id is unknown
     */
    default Map<Integer, TagLayout> getMapByIdIn(Collection<Integer> ids) throws EntityNotFoundException {
        return RepoUtils.getMapByField(this::findAllById, ids, TagLayout::getId,
                "Unknown tag layout ID{s}: ");
    }
}
