package com.holub.life;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Keeps persistence and the built-in catalogue out of the Swing controller. */
public final class PatternLibrary {
    private static final String HEADER = "LIFE_PATTERN 1";
    private final Path directory;
    private final List<PatternTemplate> patterns = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public PatternLibrary(Path directory) {
        this.directory = directory;
        patterns.add(PatternTemplate.fromRows("Glider", ".O.", "..O", "OOO"));
        patterns.add(PatternTemplate.fromRows("Blinker", "OOO"));
        patterns.add(PatternTemplate.fromRows("Block", "OO", "OO"));
        patterns.add(PatternTemplate.fromRows("Toad", ".OOO", "OOO."));
        patterns.add(PatternTemplate.fromRows("Beacon", "OO..", "OO..", "..OO", "..OO"));
        if (!Files.exists(directory)) return;
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".lifepattern"))
                    .sorted().forEach(path -> {
                        try {
                            PatternTemplate pattern = read(path);
                            if (containsName(pattern.name())) throw new IOException("중복 이름");
                            patterns.add(pattern);
                        } catch (IOException | IllegalArgumentException e) {
                            warnings.add(path.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            warnings.add("템플릿 폴더를 읽지 못했습니다: " + e.getMessage());
        }
    }

    public List<PatternTemplate> patterns() { return Collections.unmodifiableList(patterns); }
    public List<String> warnings() { return Collections.unmodifiableList(warnings); }
    private boolean containsName(String name) {
        return patterns.stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
    }

    public void save(PatternTemplate pattern) throws IOException {
        if (pattern.liveCount() == 0) throw new IllegalArgumentException("살아 있는 셀이 없는 영역입니다.");
        if (containsName(pattern.name())) throw new IllegalArgumentException("같은 이름의 템플릿이 있습니다.");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".capture-", ".tmp");
        Path destination = directory.resolve(UUID.randomUUID() + ".lifepattern");
        try {
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            lines.add(pattern.name());
            lines.add(pattern.width() + " " + pattern.height());
            for (int y = 0; y < pattern.height(); y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < pattern.width(); x++) row.append(pattern.isAlive(x, y) ? 'O' : '.');
                lines.add(row.toString());
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination);
            }
            patterns.add(pattern);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static PatternTemplate read(Path path) throws IOException {
        if (Files.size(path) > 20000) throw new IOException("파일이 너무 큽니다.");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() < 4 || !HEADER.equals(lines.get(0))) throw new IOException("지원하지 않는 형식입니다.");
        try {
            String[] dimensions = lines.get(2).split(" ");
            if (dimensions.length != 2) throw new IllegalArgumentException("잘못된 크기입니다.");
            int width = Integer.parseInt(dimensions[0]);
            int height = Integer.parseInt(dimensions[1]);
            if (width < 1 || width > PatternTemplate.MAX_SIZE || height < 1
                    || height > PatternTemplate.MAX_SIZE || lines.size() != height + 3) {
                throw new IllegalArgumentException("잘못된 크기입니다.");
            }
            String[] rows = lines.subList(3, lines.size()).toArray(new String[0]);
            for (String row : rows) if (row.length() != width) throw new IllegalArgumentException("행 길이가 다릅니다.");
            PatternTemplate result = PatternTemplate.fromRows(lines.get(1), rows);
            if (result.liveCount() == 0) throw new IllegalArgumentException("빈 패턴입니다.");
            return result;
        } catch (IllegalArgumentException e) {
            throw new IOException("잘못된 패턴 파일: " + e.getMessage(), e);
        }
    }
}
