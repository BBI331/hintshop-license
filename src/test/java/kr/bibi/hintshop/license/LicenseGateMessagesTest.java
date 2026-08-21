package kr.bibi.hintshop.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 콘솔 문구 현지화 검증. 무효화된 코드로 부팅했을 때 구매자가 설정한 언어로
 * "만료되었습니다 / 다시 다운로드" 안내가 나와야 한다.
 */
class LicenseGateMessagesTest {

    @Test
    void 언어_코드를_정규화한다() {
        assertEquals("ko", LicenseGate.norm("ko"));
        assertEquals("ko", LicenseGate.norm("KO "));
        assertEquals("jp", LicenseGate.norm("jp"));
        assertEquals("jp", LicenseGate.norm("ja"));
        assertEquals("ch", LicenseGate.norm("ch"));
        assertEquals("ch", LicenseGate.norm("zh-CN"));
        assertEquals("en", LicenseGate.norm("en"));
        assertEquals("en", LicenseGate.norm(null));
        assertEquals("en", LicenseGate.norm("deutsch"));
    }

    @Test
    void 만료_안내가_설정_언어로_나온다() {
        String ko = LicenseGate.msg("ko", "superseded");
        assertTrue(ko.contains("만료"), ko);
        assertTrue(ko.contains("힌트샵") && ko.contains("다시 다운로드"), ko);
        assertTrue(LicenseGate.msg("jp", "superseded").contains("有効期限"));
        assertTrue(LicenseGate.msg("ch", "superseded").contains("过期"));
        assertTrue(LicenseGate.msg("en", "superseded").contains("expired"));
    }

    @Test
    void 모든_키가_네_언어에서_비어있지_않다() {
        String[] keys = {"no_license", "not_found", "wrong_role", "superseded", "seats_full",
                "net_grace", "net_blocked", "verified", "verified_first", "runtime_off", "disable"};
        for (String lang : new String[] {"ko", "en", "jp", "ch"}) {
            for (String key : keys) {
                String text = LicenseGate.msg(lang, key);
                assertFalse(text.isBlank(), lang + ":" + key);
            }
        }
    }

    @Test
    void 모르는_언어는_영어로_떨어진다() {
        assertEquals(LicenseGate.msg("en", "superseded"), LicenseGate.msg("fr", "superseded"));
    }
}
