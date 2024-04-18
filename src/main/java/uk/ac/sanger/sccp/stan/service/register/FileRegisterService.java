package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Service handling registrations by user-submitted file.
 */
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
    default RegisterResult registerBlocks(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException {
        return registerBlocks(user, multipartFile, null, null);
    }

    /**
     * Registers blocks as described in the given file.
     * @param user the user responsible
     * @param multipartFile the file data
     * @param existingExternalNames known existing tissue external names to reregister
     * @param ignoreExternalNames external names of rows to exclude from the request
     * @return the result of the registration
     * @exception ValidationException the data received is invalid
     * @exception UncheckedIOException the file cannot be read
     */
    RegisterResult registerBlocks(User user, MultipartFile multipartFile, List<String> existingExternalNames, List<String> ignoreExternalNames)
            throws ValidationException, UncheckedIOException;

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
