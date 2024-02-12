package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.config.MailConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class AdminNotifyServiceImp implements AdminNotifyService {
    private final MailConfig mailConfig;
    private final UserRepo userRepo;
    private final EmailService emailService;

    @Autowired
    public AdminNotifyServiceImp(MailConfig mailConfig, UserRepo userRepo, EmailService emailService) {
        this.mailConfig = mailConfig;
        this.userRepo = userRepo;
        this.emailService = emailService;
    }

    @Override
    public boolean isNotificationEnabled(String name) {
        return this.mailConfig.isAdminNotificationEnabled(name);
    }

    @Override
    public String substitute(String string) {
        if (!nullOrEmpty(string)) {
            string = string.replace("%service", mailConfig.getServiceDescription());
        }
        return string;
    }

    @Override
    public boolean issue(String name, String heading, String body) {
        if (!isNotificationEnabled(name)) {
            return false;
        }
        List<User> admins = userRepo.findAllByRole(User.Role.admin);
        if (admins.isEmpty()) {
            return false;
        }
        List<String> recipients = admins.stream().map(User::getUsername).toList();
        return sendNotification(recipients, heading, body);
    }

    public boolean sendNotification(List<String> recipients, String heading, String body) {
        heading = substitute(heading);
        body = substitute(body);
        return emailService.tryEmail(recipients, heading, body);
    }
}
