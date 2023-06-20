package uk.ac.sanger.sccp.stan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.FileSectionRegisterService;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Controller for accepting excel files for registration
 * @author dr6
 */
@Controller
public class RegistrationFileController {
    private final Logger log = LoggerFactory.getLogger(RegistrationFileController.class);

    private final FileSectionRegisterService fileSectionRegisterService;
    private final AuthenticationComponent authComp;

    @Autowired
    public RegistrationFileController(AuthenticationComponent authComp,
                                      FileSectionRegisterService fileSectionRegisterService) {
        this.authComp = authComp;
        this.fileSectionRegisterService = fileSectionRegisterService;
    }

    @PostMapping(value = "/register/section", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveFile(@RequestParam("file") MultipartFile file) throws URISyntaxException {
        User user = checkUserForUpload();
        RegisterResult result;
        try {
            result = fileSectionRegisterService.register(user, file);
        } catch (ValidationException e) {
            log.error("File section registration failed.", e);
            List<String> problems = e.getProblems().stream().map(Object::toString).collect(toList());
            Map<String, List<String>> output = Map.of("problems", problems);
            return ResponseEntity.badRequest().body(output);
        }
        log.info("{} file registration: {}", user.getUsername(), result);
        List<String> barcodes = result.getLabware().stream().map(Labware::getBarcode).collect(toList());
        Map<String, List<String>> output = Map.of("barcodes", barcodes);
        return ResponseEntity.ok().body(output);
    }

    protected User getUser() {
        Authentication auth = authComp.getAuthentication();
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                return (User) principal;
            }
        }
        return new User(17, "dr6", User.Role.normal);
    }

    protected User checkUserForUpload() {
        User user = getUser();
        if (user==null) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        if (!user.hasRole(User.Role.normal)) {
            throw new InsufficientAuthenticationException("User "+user.getUsername()
                    +" does not have privilege to perform this operation.");
        }
        return user;
    }
}
