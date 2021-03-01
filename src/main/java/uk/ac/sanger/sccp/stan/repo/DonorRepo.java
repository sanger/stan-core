package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Donor;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface DonorRepo extends CrudRepository<Donor, Integer> {
    Optional<Donor> findByDonorName(String donorName);

    default Donor getByDonorName(String donorName) throws EntityNotFoundException {
        return findByDonorName(donorName).orElseThrow(
                () -> new EntityNotFoundException("Donor name not found: "+repr(donorName))
        );
    }
}
