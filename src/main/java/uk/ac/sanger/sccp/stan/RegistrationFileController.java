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
import uk.ac.sanger.sccp.stan.request.register.LabwareSolutionName;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.FileRegisterService;

import java.net.URISyntaxException;
import java.util.*;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Controller for accepting excel files for registration
 * @author dr6
 */
@Controller
public class RegistrationFileController {
    private final Logger log = LoggerFactory.getLogger(RegistrationFileController.class);

    private final FileRegisterService fileRegisterService;
    private final AuthenticationComponent authComp;

    @Autowired
    public RegistrationFileController(AuthenticationComponent authComp,
                                      FileRegisterService fileRegisterService) {
        this.authComp = authComp;
        this.fileRegisterService = fileRegisterService;
    }

    @PostMapping(value = "/register/section", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveSectionFile(@RequestParam("file") MultipartFile file) throws URISyntaxException {
        return receiveFile("section", file, fileRegisterService::registerSections);
    }

    @PostMapping(value = "/register/block", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveBlockFile(@RequestParam("file") MultipartFile file) throws URISyntaxException {
        return receiveFile("block", file, fileRegisterService::registerBlocks);
    }

    @PostMapping(value="/register/original", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveOriginalFile(@RequestParam("file") MultipartFile file) throws URISyntaxException {
        return receiveFile("original sample", file, fileRegisterService::registerOriginal);
    }

    public ResponseEntity<?> receiveFile(String desc, MultipartFile file,
                                         BiFunction<User, MultipartFile, RegisterResult> serviceFunction)
            throws URISyntaxException {
        User user = checkUserForUpload();
        RegisterResult result;
        try {
            result = serviceFunction.apply(user, file);
        } catch (ValidationException e) {
            log.error("File "+desc+" registration failed.", e);
            List<String> problems = e.getProblems().stream().map(Object::toString).collect(toList());
            Map<String, List<String>> output = Map.of("problems", problems);
            return ResponseEntity.badRequest().body(output);
        }
        log.info("{} {} file registration: {}", user.getUsername(), desc, result);
        List<String> barcodes = result.getLabware().stream().map(Labware::getBarcode).collect(toList());
        final Map<String, List<?>> output;
        if (nullOrEmpty(result.getLabwareSolutions())) {
            output = Map.of("barcodes", barcodes);
        } else {
            output = Map.of("barcodes", barcodes, "labwareSolutions", barcodeSolutions(result.getLabwareSolutions()));
        }
        return ResponseEntity.ok().body(output);
    }

    protected List<Map<String, String>> barcodeSolutions(Collection<LabwareSolutionName> labwareSolutions) {
        return labwareSolutions.stream()
                .map(lsn -> Map.of("barcode", lsn.getBarcode(), "solution", lsn.getSolutionName()))
                .collect(toList());
    }

    protected User getUser() {
        Authentication auth = authComp.getAuthentication();
        if (auth != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                return (User) principal;
            }
        }
        return null;
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
