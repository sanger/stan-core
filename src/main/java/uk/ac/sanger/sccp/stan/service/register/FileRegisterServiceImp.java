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
import java.util.function.BiFunction;

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

    protected <Req, Res> Res register(User user, MultipartFile multipartFile, MultipartFileReader<Req> fileReader,
                                      BiFunction<User, Req, Res> service)
            throws ValidationException {
        Req req ;
        try {
            req = fileReader.read(multipartFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return transactor.transact("register", () -> service.apply(user, req));
    }

    @Override
    public RegisterResult registerSections(User user, MultipartFile multipartFile) throws ValidationException {
        return register(user, multipartFile, sectionFileReader, sectionRegisterService::register);
    }

    @Override
    public RegisterResult registerBlocks(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException {
        return register(user, multipartFile, blockFileReader, blockRegisterService::register);
    }

    @Override
    public RegisterResult registerOriginal(User user, MultipartFile multipartFile) throws ValidationException, UncheckedIOException {
        return register(user, multipartFile, originalSampleFileReader, originalSampleRegisterService::register);
    }
}
