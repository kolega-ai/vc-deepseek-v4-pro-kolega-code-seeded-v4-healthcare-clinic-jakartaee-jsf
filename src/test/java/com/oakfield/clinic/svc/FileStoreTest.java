package com.oakfield.clinic.svc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FileStore and filename sanitization checks. */
class FileStoreTest {

    @Test
    void resolveKeepsPathsInsideBaseDirectory() {
        java.nio.file.Path resolved = FileStore.resolve(FileStore.Area.UPLOADS, "abc123.pdf");
        assertThat(resolved.toString()).startsWith(FileStore.dataDir().toString());
        assertThat(resolved.getFileName().toString()).isEqualTo("abc123.pdf");
    }

    @Test
    void traversalAttemptsAreRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        FileStore.resolve(FileStore.Area.UPLOADS, "../../etc/passwd"))
                .isInstanceOf(com.oakfield.clinic.decision.AccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        FileStore.resolve(FileStore.Area.EXPORTS, "a/../../b"))
                .isInstanceOf(com.oakfield.clinic.decision.AccessDeniedException.class);
    }

    @Test
    void sanitizeFileNameStripsPathsAndControlCharacters() {
        assertThat(DocumentService.sanitizeFileName("/etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitizeFileName("..\\..\\windows.txt")).isEqualTo("windows.txt");
        assertThat(DocumentService.sanitizeFileName("report\u0000.pdf")).isEqualTo("report.pdf");
        assertThat(DocumentService.sanitizeFileName(null)).isEqualTo("document");
    }

    @Test
    void sha256IsStable() {
        byte[] data = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(DocumentService.sha256Hex(data))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
