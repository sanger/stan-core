package uk.ac.sanger.sccp.stan.mlwh;

import org.hibernate.exception.JDBCConnectionException;

import java.util.List;

public interface SSStudyRepo {
    /**
     * Loads all sequencescape studies from the mlwh
     * @return a list of such studies
     * @exception JDBCConnectionException problem reading from mlwh
     */
    List<SSStudy> loadAllSs() throws JDBCConnectionException;
}
