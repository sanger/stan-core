package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Donor;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface DonorRepo extends CrudRepository<Donor, Integer> {
    Optional<Donor> findByDonorName(String donorName);

    default Donor getByDonorName(String donorName) throws EntityNotFoundException {
        return findByDonorName(donorName).orElseThrow(
                () -> new EntityNotFoundException("Donor name not found: "+repr(donorName))
        );
    }

    List<Donor> findAllByDonorNameIn(Collection<String> donorNames);

    default List<Donor> getAllByDonorNameIn(Collection<String> donorNames) throws EntityNotFoundException {
        return RepoUtils.getAllByField(this::findAllByDonorNameIn, donorNames, Donor::getDonorName,
                "Unknown donor name{s}: ", String::toUpperCase);
    }
}
