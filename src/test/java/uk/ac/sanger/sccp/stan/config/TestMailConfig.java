package uk.ac.sanger.sccp.stan.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles(profiles = "test")
class TestMailConfig {
    @Autowired
    MailConfig mailConfig;

    @ParameterizedTest
    @CsvSource({"Apple, true",
            "APPLE, true",
            "banana, false",
            "BANANA, false",
            "orange, true",
    })
    public void testIsAdminNotificationEnabled(String name, boolean expected) {
        assertEquals(expected, mailConfig.isAdminNotificationEnabled(name));
    }
}