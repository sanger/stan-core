package uk.ac.sanger.sccp.stan.mlwh;


import org.hibernate.exception.JDBCConnectionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import uk.ac.sanger.sccp.stan.config.MlwhConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SSStudyRepoImp implements SSStudyRepo {

    private final MlwhConfig mlwh;

    @Autowired
    public SSStudyRepoImp(MlwhConfig mlwh) {
        this.mlwh = mlwh;
    }

    @Override
    public List<SSStudy> loadAllSs() throws JDBCConnectionException {
        List<SSStudy> results = new ArrayList<>();
        //noinspection SqlResolve
        final String sql = "SELECT id_study_lims, name FROM study WHERE id_lims='SQSCP'";
        try (Connection con = mlwh.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id_study_lims");
                String name = rs.getString("name");
                results.add(new SSStudy(id, name));
            }
        } catch (SQLException se) {
            throw new JDBCConnectionException("Problem connecting to mlwh", se);
        }
        return results;

    }
}
