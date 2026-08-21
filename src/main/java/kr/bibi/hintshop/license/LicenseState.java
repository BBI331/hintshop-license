package kr.bibi.hintshop.license;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * data/ 하위에 남기는 라이센스 로컬 상태 두 가지 (Bukkit 비의존):
 *
 * <ul>
 *   <li><b>server-id.txt</b> — 이 서버 설치본의 고유 ID. 첫 실행 때 UUID 를 만들어
 *       저장하고 이후 재사용한다. 라이센스는 이 ID 에 귀속되므로 <b>이 파일을
 *       지우면 봇 입장에서 "다른 서버"가 되어</b> already_bound 로 막힌다
 *       (디스코드에서 재발급 받으면 해결).</li>
 *   <li><b>license-cache.txt</b> — 마지막으로 검증에 <i>성공한</i> 시각.
 *       검증 서버(봇)에 연결이 안 될 때, 최근 성공 이력이 유예 시간 안에 있으면
 *       임시로 실행을 허용하는 근거가 된다. 키+서버ID+시각을 묶은 해시를 함께
 *       저장해 단순 손편집으로는 시각을 조작할 수 없게 한다.</li>
 * </ul>
 */
public final class LicenseState {

    private static final String SALT = "hintshop-license-v1";

    private final File serverIdFile;
    private final File cacheFile;

    /** @param stateFolder 상태 파일을 둘 폴더. 없으면 첫 기록 때 만들어진다. */
    public LicenseState(File stateFolder) {
        this.serverIdFile = new File(stateFolder, "server-id.txt");
        this.cacheFile = new File(stateFolder, "license-cache.txt");
    }

    /** 서버 고유 ID 를 읽고, 없으면 새로 만들어 저장한다. */
    public String getOrCreateServerId() throws IOException {
        if (serverIdFile.isFile()) {
            String existing = Files.readString(serverIdFile.toPath(), StandardCharsets.UTF_8).trim();
            if (!existing.isEmpty()) {
                return existing;
            }
        }
        File parent = serverIdFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("상태 폴더를 만들 수 없다: " + parent);
        }
        String fresh = UUID.randomUUID().toString();
        Files.writeString(serverIdFile.toPath(), fresh, StandardCharsets.UTF_8);
        return fresh;
    }

    /** 검증 성공 시각을 기록한다. 실패해도 치명적이지 않으므로 성공 여부만 돌려준다. */
    public boolean recordSuccess(String code, String serverId) {
        try {
            long now = System.currentTimeMillis();
            Files.writeString(cacheFile.toPath(), now + ":" + digest(code, serverId, now), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 마지막 검증 성공 후 지난 시간(시간 단위, 내림). 기록이 없거나, 해시가 맞지
     * 않거나(손편집), 기록 시각이 미래이면(시계 되돌림) -1.
     */
    public long hoursSinceLastSuccess(String code, String serverId) {
        if (!cacheFile.isFile()) {
            return -1;
        }
        try {
            String content = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8).trim();
            int sep = content.indexOf(':');
            if (sep <= 0) {
                return -1;
            }
            long recorded = Long.parseLong(content.substring(0, sep));
            if (!digest(code, serverId, recorded).equals(content.substring(sep + 1))) {
                return -1;
            }
            long now = System.currentTimeMillis();
            if (now < recorded) {
                return -1;
            }
            return (now - recorded) / (60L * 60L * 1000L);
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    private static String digest(String code, String serverId, long timestamp) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(
                    (code + "|" + serverId + "|" + timestamp + "|" + SALT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
