package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.sql.*;

/**
 * @author dr6
 */
@Configuration
public class MlwhConfig {
    @Value("${spring.mlwh.url}")
    String url;
    @Value("${spring.mlwh.username}")
    String user;
    @Value("${spring.mlwh.password}")
    String password;

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
