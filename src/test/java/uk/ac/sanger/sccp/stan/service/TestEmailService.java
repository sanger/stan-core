package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import uk.ac.sanger.sccp.stan.config.MailConfig;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link EmailService}
 * @author dr6
 */
public class TestEmailService {
    @Mock
    JavaMailSender mockMailSender;
    @Mock
    MailConfig mockMailConfig;

    EmailService service;

    AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new EmailService(mockMailSender, mockMailConfig));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSend(boolean sendCC) {
        String subject = "Subject alpha";
        String text = "Text pattern beta";
        String sender = "no-reply@sanger.ac.uk";
        when(mockMailConfig.getSender()).thenReturn(sender);

        String[] recipients = {"alabama@nowhere.com", "alaska@nowhere.com"};
        String[] cc = (sendCC ? new String[] {"arizona@nowhere.com", "arkansas@nowhere.com"} : null);

        service.send(subject, text, recipients, cc);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setText(text);
        message.setSubject(subject);
        message.setTo(recipients);
        if (sendCC) {
            message.setCc(cc);
        }
        message.setFrom(sender);

        verify(mockMailSender).send(message);
    }

    @Test
    public void testGetServiceDescription() {
        String desc = "Stan test";
        when(mockMailConfig.getServiceDescription()).thenReturn(desc);
        assertEquals(desc, service.getServiceDescription());
    }

    @ParameterizedTest
    @CsvSource(value={
            "false, recipients@sanger.ac.uk, true",
            "true, recipients@sanger.ac.uk, false",
            "false, , false",
    })
    public void testTryAndSendAlert(boolean throwError, String recipient, boolean expectedResult) {
        String[] alertRecipients = (recipient==null ? new String[0] : new String[] { recipient});
        when(mockMailConfig.getAlertRecipients()).thenReturn(alertRecipients);
        boolean shouldAttempt = (alertRecipients.length > 0);
        String subject = "Subject alpha";
        String text = "Text pattern beta";
        if (shouldAttempt) {
            if (throwError) {
                doThrow(MailSendException.class).when(service).send(any(), any(), any(), any());
            } else {
                doNothing().when(service).send(any(), any(), any(), any());
            }
        }

        boolean result = service.tryAndSendAlert(subject, text);
        assertEquals(expectedResult, result);
        if (shouldAttempt) {
            verify(service).send(subject, text, alertRecipients, null);
        }
    }

    @ParameterizedTest
    @CsvSource({
            ",,",
            "rcc,,rcc@sanger.ac.uk",
            "r@x,,r@x",
            ",a@x b@x,a@x b@x",
            "rcc,a@x b@x,a@x b@x rcc@sanger.ac.uk",
            "r@x,a@x b@x,a@x b@x r@x",
    })
    public void testReleaseEmailCCs(String releaseCc, String otherCc, String expected) {
        when(mockMailConfig.getReleaseCC()).thenReturn(releaseCc);
        List<String> ccList;
        if (otherCc==null) {
            ccList = null;
        } else {
            ccList = Arrays.asList(otherCc.split("\\s+"));
        }
        String[] expectedResult;
        if (expected==null) {
            expectedResult = null;
        } else {
            expectedResult = expected.split("\\s+");
        }

        assertArrayEquals(expectedResult, service.releaseEmailCCs(ccList));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    public void testTryReleaseEmail(boolean success, boolean anyWork) {
        String recipient = "rec@sanger.ac.uk";
        String desc = "Stan test";
        String[] ccArray = { "a", "b", "c" };
        List<String> ccList = List.of("a", "b");
        List<String> workNumbers = anyWork ? List.of("SGP1", "SGP2") : List.of();
        when(mockMailConfig.getServiceDescription()).thenReturn(desc);
        doReturn(ccArray).when(service).releaseEmailCCs(any());
        String path = "path_to_file";
        (success ? doNothing() : doThrow(RuntimeException.class)).when(service).send(any(), any(), any(), any());
        assertEquals(success, service.tryReleaseEmail(recipient, ccList, workNumbers, path));
        verify(service).releaseEmailCCs(ccList);
        String body = anyWork ?"Release to rec@sanger.ac.uk for work numbers SGP1, SGP2.\nThe details of the release are available at "
                : "Release to rec@sanger.ac.uk.\nThe details of the release are available at ";

        verify(service).send(desc+" release", body + path, new String[] { recipient }, ccArray);
    }

    @ParameterizedTest
    @CsvSource({",",
            "alpha, alpha@sanger.ac.uk",
            "beta@gamma, beta@gamma",
    })
    public void testUsernameToEmail(String input, String expected) {
        assertEquals(expected, service.usernameToEmail(input));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testTryEmail(boolean succeeds) {
        if (succeeds) {
            doNothing().when(service).send(any(), any(), any(), any());
        } else {
            doThrow(RuntimeException.class).when(service).send(any(), any(), any(), any());
        }
        List<String> recipients = List.of("alpha", "beta@gamma");
        assertEquals(succeeds, service.tryEmail(recipients, "Email header",
                "Email body."));
        verify(service).send("Email header",
                "Email body.",
                new String[] { "alpha@sanger.ac.uk", "beta@gamma" },
                null);
    }
}
