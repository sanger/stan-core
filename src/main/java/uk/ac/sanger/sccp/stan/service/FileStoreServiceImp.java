package uk.ac.sanger.sccp.stan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class FileStoreServiceImp implements FileStoreService {
    private static final Logger log = LoggerFactory.getLogger(FileStoreServiceImp.class);

    private final StanFileConfig config;
    private final Clock clock;
    private final Transactor transactor;
    private final StanFileRepo fileRepo;
    private final WorkRepo workRepo;
    private final WorkEventRepo workEventRepo;

    @Autowired
    public FileStoreServiceImp(StanFileConfig config, Clock clock, Transactor transactor,
                               StanFileRepo fileRepo, WorkRepo workRepo, WorkEventRepo workEventRepo) {
        this.config = config;
        this.clock = clock;
        this.transactor = transactor;
        this.fileRepo = fileRepo;
        this.workRepo = workRepo;
        this.workEventRepo = workEventRepo;
    }

    @Override
    public Iterable<StanFile> save(User user, MultipartFile fileData, List<String> workNumbers) {
        if (nullOrEmpty(workNumbers)) {
            throw new IllegalArgumentException("No work numbers specified.");
        }
        Set<Work> works = workRepo.getSetByWorkNumberIn(workNumbers);
        checkAuthorisation(user, works);

        String filename = getFilename(fileData);
        if (filename.length() > StanFile.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Filename too long: "+repr(filename));
        }
        String san = filename.replaceAll("[^a-zA-Z0-9_-]+", "");
        if (san.isEmpty()) {
            san = "unnamed";
        }

        LocalDateTime now = LocalDateTime.now(clock);

        String savedFilename = now + "_" + san;
        Path path = Paths.get(config.getDir(), savedFilename);
        int len = path.toString().length();
        if (len > StanFile.MAX_PATH_LENGTH) {
            int excess = len - StanFile.MAX_PATH_LENGTH;
            savedFilename = savedFilename.substring(0, savedFilename.length()-excess);
            path = Paths.get(config.getDir(), savedFilename);
        }

        final String pathString = path.toString();

        synchronized (FileStoreServiceImp.class) {
            if (fileRepo.existsByPath(pathString)) {
                throw new IllegalArgumentException("The database already contains a file with path " + pathString);
            }

            final Path fullDestPath = Paths.get(config.getRoot(), config.getDir(), savedFilename);

            try {
                fileData.transferTo(fullDestPath);
            } catch (IOException e) {
                log.error("Saving file failed: {}", fullDestPath);
                throw new UncheckedIOException(e);
            }

            return transactor.transact("updateStanFiles",
                    () -> updateStanFiles(user, filename, works, now, pathString));
        }
    }

    /**
     * Checks that the given user is allowed to upload files to the given works.
     * @param user the user
     * @param works the works
     * @exception InsufficientAuthenticationException if the user is not authorised
     */
    public void checkAuthorisation(User user, Collection<Work> works) {
        if (user.hasRole(User.Role.normal)) {
            return;
        }
        if (!user.hasRole(User.Role.enduser)) {
            throw new InsufficientAuthenticationException("User "+user.getUsername()+" does not have privilege to upload files.");
        }
        List<WorkEvent> events = workEventRepo.findAllByWorkInAndType(works, WorkEvent.Type.create);
        Set<Integer> usersWorkIds = events.stream()
                .filter(e -> user.equals(e.getUser()))
                .map(e -> e.getWork().getId())
                .collect(toSet());

        List<String> otherUsersWorks = works.stream()
                .filter(work -> !usersWorkIds.contains(work.getId()))
                .map(Work::getWorkNumber)
                .collect(toList());
        if (!otherUsersWorks.isEmpty()) {
            throw new InsufficientAuthenticationException("User "+user.getUsername()+
                    pluralise(" does not have privilege to upload files for work number{s} ", otherUsersWorks.size())
                    + otherUsersWorks);
        }
    }

    private Iterable<StanFile> updateStanFiles(User user, String originalName, Collection<Work> works,
                                               LocalDateTime now, String storedPath) {
        List<Integer> workIds = works.stream().map(Work::getId).collect(toList());
        deprecateOldFiles(originalName, workIds, now);
        List<StanFile> newStanFiles = works.stream()
                .map(work -> new StanFile(work, user, originalName, storedPath))
                .collect(toList());
        return fileRepo.saveAll(newStanFiles);
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

    public void deprecateOldFiles(String name, Collection<Integer> workIds, LocalDateTime timestamp) {
        List<StanFile> oldFiles = fileRepo.findAllActiveByWorkIdAndName(workIds, name);
        if (oldFiles.isEmpty()) {
            return;
        }
        for (StanFile f : oldFiles) {
            f.setDeprecated(timestamp);
        }
        fileRepo.saveAll(oldFiles);
    }

    @Override
    public List<StanFile> list(Collection<String> workNumbers) {
        if (workNumbers.isEmpty()) {
            return List.of();
        }
        Set<Work> works = workRepo.getSetByWorkNumberIn(workNumbers);
        List<Integer> workIds = works.stream().map(Work::getId).collect(toList());
        return fileRepo.findAllActiveByWorkIdIn(workIds);
    }
}
