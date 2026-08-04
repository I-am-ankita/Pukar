package in.pukar.service;

import in.pukar.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${pukar.storage.dir}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory", e);
        }
    }

    /** Stores the file and returns a public URL path (served under /files/**). */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "No file provided");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);
        String stored = UUID.randomUUID() + ext;
        try {
            Path target = root.resolve(stored);
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Could not store file: " + e.getMessage());
        }
        return new StoredFile(original, "/files/" + stored, file.getContentType(), file.getSize());
    }

    public record StoredFile(String originalName, String url, String contentType, long size) {}
}
