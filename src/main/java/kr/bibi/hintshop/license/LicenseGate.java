package kr.bibi.hintshop.license;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 플러그인 부팅을 라이센스 검증 뒤로 막는 게이트 — onEnable 초반에
 * {@code if (!LicenseGate.enable(this, ROLE_ID)) return;} 한 줄로 쓴다.
 *
 * <p>roleId 는 이 플러그인이 어느 상품인지를 가리키는 값이다. 힌트샵의 상품마다
 * 디스코드 역할이 하나씩 연동돼 있고(다운로드 권한이 그 역할로 갈린다), 검증 서버가
 * 그 값을 대조해 다른 상품의 라이센스 파일을 가져다 쓰는 것을 막는다. 플러그인 하나가
 * 곧 상품 하나이므로 각 플러그인이 자기 값을 넘기면 된다.</p>
 *
 * <p>라이센스 값은 구매자가 입력하지 않는다. 힌트샵 봇이 다운로드 시점에 이 jar 안에
 * {@code hintshop.license} 리소스로 심어 보내고, 여기서는 그것을 읽어 검증 API 로
 * 보낸다. 구매자에게는 라이센스라는 절차 자체가 보이지 않는다.</p>
 *
 * <p>확정 거절(미발급/다른 상품/무효화됨/사용 환경 한도 초과)이면 플러그인을 끈다.
 * 검증 서버에 연결이 안 되면 최근 {@link #GRACE_HOURS}시간 안에 성공 이력이 있을
 * 때만 임시 허용한다.</p>
 *
 * <p>사용 환경 제한: 코드마다 서로 다른 접속 IP 를 기본 3곳까지 받는다. 어느 환경인지는
 * 이 플러그인이 알려주는 것이 아니라 검증 서버가 접속 IP 로 직접 판단하므로 위조할 수
 * 없다. 한 번 잡힌 자리는 서버를 꺼도 저절로 풀리지 않고, 구매자가 디스코드의
 * 라이센스 현황 패널에서 직접 비워야 한다.</p>
 */
public final class LicenseGate {

    /**
     * 라이센스 검증 서버 주소 — Cloudflare Worker (hintshop 저장소 worker/ 참고).
     * 집 컴퓨터/IP 와 무관하게 24시간 살아있는 고정 주소라 더 이상 포트포워딩·DDNS 가
     * 필요 없다. config.yml 로 빼면 구매자가 가짜 검증 서버로 바꿔치기할 수 있으므로
     * 반드시 코드에 박아서 빌드한다.
     */
    private static final String API_URL = "https://hintshop-license.hintshop.workers.dev";

    /** 봇이 다운로드 때 jar 안에 심어 보내는 라이센스 값 파일 (package.py 의 LICENSE_RESOURCE). */
    private static final String LICENSE_RESOURCE = "hintshop.license";

    /** 검증 서버 접속 불가 시 마지막 성공 검증에서 이 시간까지는 임시 허용. 0 이면 즉시 차단. */
    private static final long GRACE_HOURS = 72;

    /** 가동 중 하트비트(사용권 유지 확인) 주기(분). 다른 서버에 선점당하면 이 주기 안에 꺼진다. */
    private static final long RECHECK_MINUTES = 10;

    private static final int TIMEOUT_MS = 5000;
    private static final String PREFIX = "[License] ";

    private LicenseGate() {
    }

    /**
     * jar 안에 심긴 라이센스 값. 없으면 빈 문자열 — 봇을 거치지 않고 얻은 파일이라는 뜻이다.
     */
    static String embeddedCode() {
        try (InputStream in = LicenseGate.class.getResourceAsStream("/" + LICENSE_RESOURCE)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 부팅 검증. true 면 계속 진행해도 된다. false 면 비활성화 처리까지 끝났으므로
     * 호출부는 그냥 return 하면 된다 (onDisable 은 null 검사를 전제해야 한다).
     *
     * @param plugin 검증에 실패하면 꺼질 플러그인
     * @param roleId 이 플러그인이 파는 상품에 연동된 디스코드 역할 ID
     *               (역할 우클릭 → ID 복사하기). 비워두면 상품 대조를 건너뛴다.
     */
    public static boolean enable(JavaPlugin plugin, String roleId) {
        Logger log = plugin.getLogger();
        String productRole = roleId == null ? "" : roleId.trim();

        String key = embeddedCode();
        if (key.isEmpty()) {
            log.severe(PREFIX + "This file carries no license information.");
            log.severe(PREFIX + "Please download the product directly from the HintSHOP Discord and use that file.");
            return disable(plugin);
        }

        // 상태 파일은 플러그인 데이터 폴더의 data/ 하위에 둔다. 폴더가 없으면 만들어진다.
        // serverId 는 검증 서버로 보내지 않고, 오프라인 유예 기록의 로컬 키로만 쓴다.
        LicenseState state = new LicenseState(new File(plugin.getDataFolder(), "data"));
        String serverId;
        try {
            serverId = state.getOrCreateServerId();
        } catch (IOException e) {
            log.severe(PREFIX + "Could not read or create the server ID file (data/server-id.txt): " + e.getMessage());
            return disable(plugin);
        }

        LicenseClient.Result result = LicenseClient.verify(API_URL, key, productRole, "boot", TIMEOUT_MS);
        if (result.isNetworkError()) {
            // 순간적인 문제일 수 있으니 한 번만 짧게 재시도
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            result = LicenseClient.verify(API_URL, key, productRole, "boot", TIMEOUT_MS);
        }

        if (result.valid()) {
            log.info(PREFIX + ("first_activation".equals(result.reason())
                    ? "Verified. This server environment has been registered as a usage environment."
                    : "Verified."));
            if (!state.recordSuccess(key, serverId)) {
                log.warning(PREFIX + "Failed to save the successful verification record (only affects the offline grace period on next boot).");
            }
            scheduleRecheck(plugin, state, key, productRole, serverId);
            return true;
        }

        if (result.isNetworkError()) {
            long hours = state.hoursSinceLastSuccess(key, serverId);
            if (GRACE_HOURS > 0 && hours >= 0 && hours <= GRACE_HOURS) {
                log.warning(PREFIX + "Could not reach the license verification server. The last successful"
                        + " check was " + hours + " hours ago, so running in grace mode (grace limit "
                        + GRACE_HOURS + " hours).");
                scheduleRecheck(plugin, state, key, productRole, serverId);
                return true;
            }
            log.severe(PREFIX + "Could not reach the license verification server, and there is no recent"
                    + " successful check to allow a grace period.");
            log.severe(PREFIX + "Check this server's internet connection and firewall settings.");
            return disable(plugin);
        }

        explainRejection(log, result.reason());
        return disable(plugin);
    }

    private static void explainRejection(Logger log, String reason) {
        switch (reason) {
            case "not_found" -> {
                log.severe(PREFIX + "This file is not registered.");
                log.severe(PREFIX + "Please download the product directly from the HintSHOP Discord and use that file.");
            }
            case "wrong_role" -> log.severe(PREFIX + "This file belongs to a different product.");
            case "superseded" -> {
                log.severe(PREFIX + "This file can no longer be used.");
                log.severe(PREFIX + "Please download the product again from the HintSHOP Discord.");
            }
            case "seats_full" -> {
                log.severe(PREFIX + "The usage-environment limit is full (other servers are using it).");
                log.severe(PREFIX + "Remove an environment you no longer use from the license status panel"
                        + " in the HintSHOP Discord and this server can boot right away."
                        + " A seat is not freed by shutting the server down.");
            }
            default -> log.severe(PREFIX + "License verification failed (reason: " + reason + ").");
        }
    }

    private static void scheduleRecheck(JavaPlugin plugin, LicenseState state, String key,
            String roleId, String serverId) {
        long intervalTicks = RECHECK_MINUTES * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            LicenseClient.Result r = LicenseClient.verify(API_URL, key, roleId, "heartbeat", TIMEOUT_MS);
            if (r.valid()) {
                state.recordSuccess(key, serverId);
                return;
            }
            if (r.isNetworkError()) {
                return; // 일시적 문제 — 다음 주기에 다시. 부팅 시 유예 판정은 마지막 성공 기록 기준.
            }
            // 확정 무효 (재발급 등) — 비활성화는 메인 스레드에서
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    explainRejection(plugin.getLogger(), r.reason());
                    plugin.getLogger().severe(PREFIX + "Re-verification failed while running, disabling the plugin.");
                    Bukkit.getPluginManager().disablePlugin(plugin);
                });
            } catch (IllegalPluginAccessException e) {
                // 서버 종료 중 등 스케줄 불가 — 다음 부팅에서 어차피 다시 검증된다
            }
        }, intervalTicks, intervalTicks);
    }

    private static boolean disable(JavaPlugin plugin) {
        plugin.getLogger().severe(PREFIX + "Disabling the plugin.");
        Bukkit.getPluginManager().disablePlugin(plugin);
        return false;
    }
}
