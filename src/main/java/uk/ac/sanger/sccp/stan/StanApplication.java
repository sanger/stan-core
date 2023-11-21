package uk.ac.sanger.sccp.stan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StanApplication {

	public static void main(String[] args) throws ClassNotFoundException {
		Class.forName("com.mysql.cj.jdbc.Driver");
		SpringApplication.run(StanApplication.class, args);
	}

}
