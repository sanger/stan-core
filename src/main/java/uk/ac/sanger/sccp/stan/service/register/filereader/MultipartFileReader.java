package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@FunctionalInterface
public interface MultipartFileReader<Output> {
    Output read(MultipartFile multipartFile) throws IOException;
}
