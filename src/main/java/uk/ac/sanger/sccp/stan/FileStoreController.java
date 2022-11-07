package uk.ac.sanger.sccp.stan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.StanFile;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.service.FileStoreService;

/**
 * Controller for handling download/upload of files
 * @author dr6
 */
@Controller
public class FileStoreController {
    private final Logger log = LoggerFactory.getLogger(FileStoreController.class);

    private final FileStoreService fileService;
    private final AuthenticationComponent authComp;

    @Autowired
    public FileStoreController(FileStoreService fileService, AuthenticationComponent authComp) {
        this.fileService = fileService;
        this.authComp = authComp;
    }

    @GetMapping("/files/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable int id) {
        StanFile sf = fileService.lookUp(id);

        Resource resource = fileService.loadResource(sf);
        log.debug("Serving file "+sf.getPath());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + sf.getName() + "\"").body(resource);
    }

    @PostMapping("/files/{workNumber}")
    public ResponseEntity<String> receiveFile(@RequestParam("file") MultipartFile file,
                              @PathVariable String workNumber) {
        User user = checkUserForUpload();
        StanFile sf = fileService.save(user, file, workNumber);
        log.info("Saved file "+sf);
        return ResponseEntity.ok("Uploaded.");
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
            throw new InsufficientAuthenticationException("User "+user.getUsername()+" does not have privilege to upload files.");
        }
        return user;
    }
}
