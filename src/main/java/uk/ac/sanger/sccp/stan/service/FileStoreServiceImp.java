package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.StanFileRepo;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.List;

/**
 * @author dr6
 */
@Service
public class FileStoreServiceImp implements FileStoreService {
    private final StanFileConfig config;
    private final Clock clock;
    private final Transactor transactor;
    private final StanFileRepo fileRepo;
    private final WorkRepo workRepo;

    @Autowired
    public FileStoreServiceImp(StanFileConfig config, Clock clock, Transactor transactor,
                               StanFileRepo fileRepo, WorkRepo workRepo) {
        this.config = config;
        this.clock = clock;
        this.transactor = transactor;
        this.fileRepo = fileRepo;
        this.workRepo = workRepo;
    }

    @Override
    public StanFile save(User user, MultipartFile fileData, String workNumber) {
        Work work = workRepo.getByWorkNumber(workNumber);

        String filename = getFilename(fileData);
        String san = filename.replaceAll("[^a-zA-Z0-9_-]+", "");
        if (san.isEmpty()) {
            san = "unnamed";
        }

        LocalDateTime now = LocalDateTime.now(clock);

        final String savedFilename = now + "_" + san;
        Path path = Paths.get(config.getDir(), savedFilename);

        try {
            fileData.transferTo(Paths.get(config.getRoot(), config.getDir(), savedFilename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return transactor.transact("updateStanFiles",
                () -> updateStanFiles(user, filename, work, now, path.toString()));
    }

    private StanFile updateStanFiles(User user, String originalName, Work work, LocalDateTime now, String storedPath) {
        deprecateOldFiles(originalName, work.getId(), now);
        return fileRepo.save(new StanFile(work, user, originalName, storedPath));
    }

    private String getFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name!=null) {
            int c = name.lastIndexOf('/');
            if (c >= 0) {
                name = name.substring(c+1);
            }
        }
        return (name==null || name.isEmpty() ? "unnamed" : name);
    }

    @Override
    public Resource loadResource(StanFile stanFile) {
        if (!stanFile.isActive()) {
            throw new IllegalStateException("File is inactive: "+stanFile.getPath());
        }
        Path path = Paths.get(config.getRoot(), stanFile.getPath());
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public StanFile lookUp(Integer id) {
        return fileRepo.getById(id);
    }

    public void deprecateOldFiles(String name, Integer workId, LocalDateTime timestamp) {
        List<StanFile> oldFiles = fileRepo.findAllActiveByWorkIdAndName(workId, name);
        if (oldFiles.isEmpty()) {
            return;
        }
        for (StanFile f : oldFiles) {
            f.setDeprecated(timestamp);
        }
        fileRepo.saveAll(oldFiles);
    }

    @Override
    public List<StanFile> list(String workNumber) {
        Work work = workRepo.getByWorkNumber(workNumber);
        return fileRepo.findAllActiveByWorkId(work.getId());
    }
}
