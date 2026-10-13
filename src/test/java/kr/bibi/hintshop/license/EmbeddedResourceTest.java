package kr.bibi.hintshop.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * jar 에 심긴 리소스 읽기. 봇(package.py 의 license_resource_text)이 넣는 모양과 맞춰야 한다.
 *
 * 첫 줄은 라이센스 값, 둘째 줄부터 "키=값". 상품 코드가 없던 시절 파일은 한 줄뿐이다.
 */
class EmbeddedResourceTest {

    /** embeddedCode/embeddedProduct 가 리소스 본문에서 뽑아내는 규칙과 같은 계산. */
    private static String codeOf(String raw) {
        int nl = raw.indexOf('\n');
        return (nl < 0 ? raw : raw.substring(0, nl)).trim();
    }

    private static String productOf(String raw) {
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("product=")) {
                return trimmed.substring("product=".length()).trim();
            }
        }
        return "";
    }

    @Test
    void readsCodeAndProductFromTwoLines() {
        String raw = "qsUToBBvwfz1zi5ryZYxyTuy\nproduct=HT-A-00001";
        assertEquals("qsUToBBvwfz1zi5ryZYxyTuy", codeOf(raw));
        assertEquals("HT-A-00001", productOf(raw));
    }

    @Test
    void oldOneLineResourceStillGivesTheCode() {
        String raw = "qsUToBBvwfz1zi5ryZYxyTuy";
        assertEquals("qsUToBBvwfz1zi5ryZYxyTuy", codeOf(raw));
        assertEquals("", productOf(raw), "상품 코드가 없으면 빈 값 — 서버가 대조를 건너뛴다");
    }

    @Test
    void windowsLineEndingsDoNotLeakIntoTheCode() {
        String raw = "qsUToBBvwfz1zi5ryZYxyTuy\r\nproduct=HT-B-00002";
        assertEquals("qsUToBBvwfz1zi5ryZYxyTuy", codeOf(raw));
        assertEquals("HT-B-00002", productOf(raw));
    }

    @Test
    void emptyResourceGivesNothing() {
        assertEquals("", codeOf(""));
        assertEquals("", productOf(""));
    }
}
