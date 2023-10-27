package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.UncheckedIOException;

public interface FileRegisterService {

    /**
     * Registers sections as described in the given file.
     * @param user the user responsible
     * @param multipartFile the file data
     * @return the result of the registration
     * @exception ValidationException the data received is invalid
     * @exception UncheckedIOException the file cannot be read
     */
    RegisterResult registerSections(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException;

    /**
     * Registers blocks as described in the given file.
     * @param user the user responsible
     * @param multipartFile the file data
     * @return the result of the registration
     * @exception ValidationException the data received is invalid
     * @exception UncheckedIOException the file cannot be read
     */
    RegisterResult registerBlocks(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException;

    /**
     * Registers original samples as described in the given file.
     * @param user the user responsible
     * @param multipartFile the file data
     * @return the result of the registration
     * @exception ValidationException the data received is invalid
     * @exception UncheckedIOException the file cannot be read
     */
    RegisterResult registerOriginal(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException;
}
