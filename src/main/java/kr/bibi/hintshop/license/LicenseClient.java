package kr.bibi.hintshop.license;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 힌트샵 봇의 라이센스 검증 API({@code POST /api/license/verify})를 호출하는 순수
 * 자바 클라이언트. Bukkit 에 의존하지 않으므로 서버 없이 단독 테스트할 수 있다
 * (hintshop 저장소 plugin-license/selftest 참조).
 */
public final class LicenseClient {

    /** 확정 거절이 아니라 "서버에 물어보지 못했다"는 뜻 — 유예(grace) 판단 대상. */
    public static final String REASON_NETWORK_ERROR = "network_error";

    /** 검증 결과 — {@code valid} 가 false 면 {@code reason} 이 사유를 담는다. */
    public record Result(boolean valid, String reason) {

        public boolean isNetworkError() {
            return REASON_NETWORK_ERROR.equals(reason);
        }
    }

    private static final Pattern VALID_PATTERN = Pattern.compile("\"valid\"\\s*:\\s*(true|false)");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"");

    private LicenseClient() {
    }

    /**
     * 코드를 한 번 검증한다. 네트워크 문제(접속 불가, 타임아웃, 이해할 수 없는 응답)는
     * 전부 {@link #REASON_NETWORK_ERROR} 로 돌아온다 — 확정 거절과 구분해서 다뤄야
     * 하므로 예외를 던지지 않는다.
     *
     * @param apiUrl 예: {@code https://검증서버주소} (경로 없이 호스트까지만)
     * @param code   jar 안에 심긴 라이센스 값 ({@code hintshop.license} 리소스)
     * @param roleId 디스코드 역할 ID — 상품에 연동된 첫 번째 역할과 정확히 일치해야 함
     * @param event  {@code "boot"}(부팅 검증) 또는 {@code "heartbeat"}(가동 중 재검증)
     *
     * <p>이 서버가 어느 환경인지는 보내지 않는다. 검증 서버가 접속 IP 로 직접 판단하며,
     * 그래야 서버가 스스로 신원을 지어내 자리를 늘리는 일이 불가능하다.</p>
     */
    public static Result verify(String apiUrl, String code, String roleId, String event, int timeoutMs) {
        String endpoint = apiUrl.replaceAll("/+$", "") + "/api/license/verify";
        String body = "{\"code\":\"" + escapeJson(code)
                + "\",\"roleId\":\"" + escapeJson(roleId) + "\",\"event\":\"" + escapeJson(event) + "\"}";

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            // bad_request(400)도 JSON 본문이 있으므로 에러 스트림까지 읽는다
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return new Result(false, REASON_NETWORK_ERROR);
            }
            String response = readAll(in);

            Matcher validMatcher = VALID_PATTERN.matcher(response);
            if (!validMatcher.find()) {
                // 우리 API 의 응답 형태가 아니다 (프록시 오류 페이지 등) — 판단 불가로 취급
                return new Result(false, REASON_NETWORK_ERROR);
            }
            Matcher reasonMatcher = REASON_PATTERN.matcher(response);
            boolean valid = "true".equals(validMatcher.group(1));
            String reason = reasonMatcher.find() ? reasonMatcher.group(1) : "";
            return new Result(valid, reason);
        } catch (IOException | IllegalArgumentException e) {
            return new Result(false, REASON_NETWORK_ERROR);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
