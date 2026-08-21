package kr.bibi.hintshop.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 서버 고유 ID 의 고정성과 오프라인 유예 기록의 무결성 검사 */
class LicenseStateTest {

    @TempDir
    File folder;

    @Test
    void 서버_ID_는_한_번_만들고_재시작해도_유지한다() throws Exception {
        String first = new LicenseState(folder).getOrCreateServerId();
        String second = new LicenseState(folder).getOrCreateServerId();
        assertNotNull(first);
        assertEquals(first, second, "재시작(새 인스턴스)에도 같은 ID 여야 라이센스 귀속이 유지된다");
        assertTrue(new File(folder, "server-id.txt").isFile());
    }

    @Test
    void 성공_기록이_있어야_유예_시간이_계산된다() throws Exception {
        LicenseState state = new LicenseState(folder);
        String id = state.getOrCreateServerId();

        assertEquals(-1, state.hoursSinceLastSuccess("KEY1", id), "기록이 없으면 -1");
        assertTrue(state.recordSuccess("KEY1", id));
        assertEquals(0, state.hoursSinceLastSuccess("KEY1", id), "방금 기록했으면 0시간");
        assertEquals(-1, state.hoursSinceLastSuccess("KEY2", id), "다른 키의 기록은 인정하지 않는다");
    }

    @Test
    void 손편집된_기록은_무효다() throws Exception {
        LicenseState state = new LicenseState(folder);
        String id = state.getOrCreateServerId();
        state.recordSuccess("KEY1", id);
        File cache = new File(folder, "license-cache.txt");

        // 시각을 미래로 바꾸면 해시가 깨진다 — 유예 시간 조작 방지
        String original = Files.readString(cache.toPath(), StandardCharsets.UTF_8);
        String tampered = (System.currentTimeMillis() + 999_999_999L) + original.substring(original.indexOf(':'));
        Files.writeString(cache.toPath(), tampered, StandardCharsets.UTF_8);
        assertEquals(-1, state.hoursSinceLastSuccess("KEY1", id));

        Files.writeString(cache.toPath(), "garbage", StandardCharsets.UTF_8);
        assertEquals(-1, state.hoursSinceLastSuccess("KEY1", id));
    }
}
