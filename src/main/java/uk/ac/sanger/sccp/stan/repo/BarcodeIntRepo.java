package uk.ac.sanger.sccp.stan.repo;

import org.springframework.stereotype.Repository;
import uk.ac.sanger.sccp.stan.service.BarcodeUtils;

import javax.persistence.*;
import java.util.List;

/** Repo for getting barcode seeds */
@Repository
public class BarcodeIntRepo {
    @PersistenceContext
    private EntityManager entityManager;

    /** Gets the next barcode seed and marks it as used */
    public int next() {
        if (!entityManager.isJoinedToTransaction()) {
            throw new TransactionRequiredException();
        }
        Query select = entityManager.createNativeQuery("select seed from barcode_int where not used order by id limit 1");
        int seed = (int) select.getSingleResult();
        Query update = entityManager.createNativeQuery("update barcode_int set used=true where seed=?1");
        update.setParameter(1, seed);
        update.executeUpdate();
        return seed;
    }

    /** Gets the next <tt>n</tt> barcode seeds and marks them used */
    public List<Integer> next(int n) {
        if (!entityManager.isJoinedToTransaction()) {
            throw new TransactionRequiredException();
        }
        Query select = entityManager.createNativeQuery("select seed from barcode_int where not used order by id limit ?1");
        select.setParameter(1, n);
        //noinspection unchecked
        List<Integer> seeds = select.getResultList();
        Query update = entityManager.createNativeQuery("update barcode_int set used=true where seed in (?1)");
        update.setParameter(1, seeds);
        update.executeUpdate();
        return seeds;
    }

    /** Creates a new barcode with the given prefix */
    public String createBarcode(String prefix) {
        int seed = next();
        return BarcodeUtils.barcode(prefix, seed);
    }

    /** Creates <tt>n</tt> new barcodes with the given prefix */
    public List<String> createBarcodes(String prefix, int n) {
        return next(n).stream()
                .map(seed -> BarcodeUtils.barcode(prefix, seed))
                .toList();
    }

    /** Creates a new Stan barcode */
    public String createStanBarcode() {
        return createBarcode(BarcodeUtils.STAN_PREFIX);
    }

    /** Creates <tt>n</tt> new Stan barcodes */
    public List<String> createStanBarcodes(int n) {
        return createBarcodes(BarcodeUtils.STAN_PREFIX, n);
    }
}
