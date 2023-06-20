package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author dr6
 */
@Service
public class FileSectionRegisterServiceImp implements FileSectionRegisterService {
    private final SectionRegisterService sectionRegisterService;
    private final SectionRegisterFileReader fileReader;

    @Autowired
    public FileSectionRegisterServiceImp(SectionRegisterService sectionRegisterService,
                                         SectionRegisterFileReader fileReader) {
        this.sectionRegisterService = sectionRegisterService;
        this.fileReader = fileReader;
    }

    @Override
    public RegisterResult register(User user, MultipartFile multipartFile) throws ValidationException {
        SectionRegisterRequest request;
        try {
            request = fileReader.read(multipartFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sectionRegisterService.register(user, request);
    }
}
