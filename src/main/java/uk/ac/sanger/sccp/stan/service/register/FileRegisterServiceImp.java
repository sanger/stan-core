package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.MultipartFileReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class FileRegisterServiceImp implements FileRegisterService {
    private final IRegisterService<SectionRegisterRequest> sectionRegisterService;
    private final IRegisterService<RegisterRequest> blockRegisterService;
    private final IRegisterService<OriginalSampleRegisterRequest> originalSampleRegisterService;
    private final MultipartFileReader<SectionRegisterRequest> sectionFileReader;
    private final MultipartFileReader<RegisterRequest> blockFileReader;
    private final MultipartFileReader<OriginalSampleRegisterRequest> originalSampleFileReader;
    private final Transactor transactor;

    @Autowired
    public FileRegisterServiceImp(IRegisterService<SectionRegisterRequest> sectionRegisterService,
                                  IRegisterService<RegisterRequest> blockRegisterService,
                                  IRegisterService<OriginalSampleRegisterRequest> originalSampleRegisterService,
                                  MultipartFileReader<SectionRegisterRequest> sectionFileReader,
                                  MultipartFileReader<RegisterRequest> blockFileReader,
                                  MultipartFileReader<OriginalSampleRegisterRequest> originalSampleFileReader,
                                  Transactor transactor) {
        this.sectionRegisterService = sectionRegisterService;
        this.blockRegisterService = blockRegisterService;
        this.originalSampleRegisterService = originalSampleRegisterService;
        this.sectionFileReader = sectionFileReader;
        this.blockFileReader = blockFileReader;
        this.originalSampleFileReader = originalSampleFileReader;
        this.transactor = transactor;
    }

    /**
     * Reads the file and performs the registration.
     * @param user the user responsible for the request
     * @param multipartFile the file data
     * @param fileReader service to read the particular request information from the file
     * @param service service method to validate and perform the request
     * @param existingExternalNames null or a list of tissue external names referenced in the request
     *        that are known to already exist
     * @param ignoreExternalNames null or a list of tissue external names referenced in the request that should be ignored
     * @return the result of the registration request
     * @param <Req> the type of request to read from the file
     * @param <Res> the type of result expected from the request
     * @exception ValidationException the request fails validation
     * @exception UncheckedIOException there was an IOException reading the file
     */
    protected <Req, Res> Res register(User user, MultipartFile multipartFile, MultipartFileReader<Req> fileReader,
                                      BiFunction<User, Req, Res> service, List<String> existingExternalNames, List<String> ignoreExternalNames)
            throws ValidationException {
        Req req;
        try {
            req = fileReader.read(multipartFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!nullOrEmpty(ignoreExternalNames) && req instanceof RegisterRequest) {
            updateToRemove((RegisterRequest) req, ignoreExternalNames);
        }
        if (!nullOrEmpty(existingExternalNames) && req instanceof RegisterRequest) {
            updateWithExisting((RegisterRequest) req, existingExternalNames);
        }
        return transactor.transact("register", () -> service.apply(user, req));
    }

    /**
     * Updates the blocks in the given request to specify that they are existing tissue if they
     * are found in the given list of existing external names.
     * @param request a block register request
     * @param existingExternalNames list of known existing external names
     */
    public void updateWithExisting(RegisterRequest request, List<String> existingExternalNames) {
        if (nullOrEmpty(existingExternalNames) || nullOrEmpty(request.getBlocks())) {
            return;
        }
        Set<String> externalNamesUC = existingExternalNames.stream()
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(toSet());
        for (BlockRegisterRequest_old block : request.getBlocks()) {
            if (block != null && !nullOrEmpty(block.getExternalIdentifier())
                    && externalNamesUC.contains(block.getExternalIdentifier().toUpperCase())) {
                block.setExistingTissue(true);
            }
        }
    }

    /**
     * Updates the blocks in the given request to remove those matching the given list of external names.
     * @param request block register request
     * @param externalNames external names to remove
     */
    public void updateToRemove(RegisterRequest request, List<String> externalNames) {
        if (nullOrEmpty(externalNames) || nullOrEmpty(request.getBlocks())) {
            return;
        }
        Set<String> ignoreUC = externalNames.stream()
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(toSet());
        List<BlockRegisterRequest_old> blocks = request.getBlocks().stream()
                .filter(block -> block==null || block.getExternalIdentifier()==null || !ignoreUC.contains(block.getExternalIdentifier().toUpperCase()))
                .toList();
        request.setBlocks(blocks);
    }

    @Override
    public RegisterResult registerSections(User user, MultipartFile multipartFile) throws ValidationException {
        return register(user, multipartFile, sectionFileReader, sectionRegisterService::register, null, null);
    }

    @Override
    public RegisterResult registerBlocks(User user, MultipartFile multipartFile, List<String> existingExternalNames,
                                         List<String> ignoreExternalNames) throws ValidationException, UncheckedIOException {
        return register(user, multipartFile, blockFileReader, blockRegisterService::register, existingExternalNames, ignoreExternalNames);
    }

    @Override
    public RegisterResult registerOriginal(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException {
        return register(user, multipartFile, originalSampleFileReader, originalSampleRegisterService::register, null, null);
    }
}
