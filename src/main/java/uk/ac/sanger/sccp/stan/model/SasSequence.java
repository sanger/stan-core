package uk.ac.sanger.sccp.stan.model;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author dr6
 */
@Entity
public class SasSequence {
    @Id
    private String prefix;
    private Integer counter;
}
