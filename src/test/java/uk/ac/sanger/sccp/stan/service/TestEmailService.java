package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import uk.ac.sanger.sccp.stan.config.MailConfig;

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

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        service = spy(new EmailService(mockMailSender, mockMailConfig));
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
    @ValueSource(booleans={false,true})
    public void tryReleaseEmail(boolean success) {
        String recipient = "rec@sanger.ac.uk";
        String cc = "cc@sanger.ac.uk";
        String desc = "Stan test";
        when(mockMailConfig.getServiceDescription()).thenReturn(desc);
        when(mockMailConfig.getReleaseCC()).thenReturn(cc);
        String path = "path_to_file";
        (success ? doNothing() : doThrow(RuntimeException.class)).when(service).send(any(), any(), any(), any());
        assertEquals(success, service.tryReleaseEmail(recipient, path));
        verify(service).send(desc+" release",
                "The details of the release are available at "+path,
                new String[] { recipient }, new String[] { cc });
    }
}
