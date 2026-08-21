package kr.bibi.hintshop.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 검증 API 클라이언트 — 응답 해석과 "확정 거절 vs 판단 불가(network_error)" 구분을 검사.
 * JDK 내장 HttpServer 로 봇 API 의 응답 형태를 흉내 낸다 (실제 봇 API 전체를 상대로 한
 * 통합 검증은 hintshop 저장소 plugin-license/selftest 에 있다).
 */
class LicenseClientTest {

    private static HttpServer server;
    private static String base;
    private static volatile String lastBody;

    /** 봇의 /라이센스패널생성 때 선택한 역할 ID 형태의 값 (계약: JSON 의 roleId 필드로 전송). */
    private static final String ROLE_ID = "1239778236422946897";

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/license/verify", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody = body;
            int status = 200;
            String response;
            if (body.contains("\"code\":\"\"")) {
                // 실제 API 는 bad_request 를 HTTP 400 + JSON 본문으로 준다 — 에러 스트림 읽기 검증용
                status = 400;
                response = "{\"valid\": false, \"reason\": \"bad_request\"}";
            } else if (body.contains("FRESHCODE0001")) {
                response = "{\"valid\": true, \"reason\": \"first_activation\"}";
            } else if (body.contains("BOUNDCODE0002")) {
                response = "{\"valid\": false, \"reason\": \"seats_full\"}";
            } else if (body.contains("GARBAGE000003")) {
                response = "<html>proxy error page</html>";
            } else {
                response = "{\"valid\": false, \"reason\": \"not_found\"}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void 유효_응답을_해석한다() {
        LicenseClient.Result r = LicenseClient.verify(base, "FRESHCODE0001", ROLE_ID, "boot", 3000);
        assertTrue(r.valid());
        assertEquals("first_activation", r.reason());
        assertFalse(r.isNetworkError());
    }

    @Test
    void 요청_본문에_roleId_필드로_역할을_보낸다() {
        LicenseClient.verify(base, "FRESHCODE0001", ROLE_ID, "boot", 3000);
        assertTrue(lastBody.contains("\"roleId\":\"" + ROLE_ID + "\""),
                "봇 API(역할 기반 계약)는 roleId 필드를 기대한다: " + lastBody);
    }

    @Test
    void 요청_본문에_event_필드로_부팅과_하트비트를_구분해_보낸다() {
        LicenseClient.verify(base, "FRESHCODE0001", ROLE_ID, "boot", 3000);
        assertTrue(lastBody.contains("\"event\":\"boot\""),
                "부팅 검증은 event=boot 로 사용권을 가져와야 한다: " + lastBody);
        LicenseClient.verify(base, "FRESHCODE0001", ROLE_ID, "heartbeat", 3000);
        assertTrue(lastBody.contains("\"event\":\"heartbeat\""),
                "가동 중 재검증은 event=heartbeat 로 유지 확인만 해야 한다: " + lastBody);
    }

    @Test
    void 거절_사유를_해석한다() {
        LicenseClient.Result r = LicenseClient.verify(base, "BOUNDCODE0002", ROLE_ID, "boot", 3000);
        assertFalse(r.valid());
        assertEquals("seats_full", r.reason());
        assertFalse(r.isNetworkError(), "확정 거절은 network_error 가 아니어야 유예 없이 차단된다");
    }

    @Test
    void HTTP_400_의_JSON_본문도_읽는다() {
        LicenseClient.Result r = LicenseClient.verify(base, "", ROLE_ID, "boot", 3000);
        assertFalse(r.valid());
        assertEquals("bad_request", r.reason());
    }

    @Test
    void JSON_이_아닌_응답은_판단_불가로_본다() {
        LicenseClient.Result r = LicenseClient.verify(base, "GARBAGE000003", ROLE_ID, "boot", 3000);
        assertFalse(r.valid());
        assertTrue(r.isNetworkError(), "프록시 오류 페이지 등은 확정 거절이 아니라 유예 대상");
    }

    @Test
    void 접속_불가는_network_error_다() {
        LicenseClient.Result r = LicenseClient.verify("http://127.0.0.1:1", "FRESHCODE0001", ROLE_ID, "boot", 1000);
        assertFalse(r.valid());
        assertTrue(r.isNetworkError());
    }
}
