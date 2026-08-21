# hintshop-license

힌트샵에서 판매하는 마인크래프트 플러그인에 라이센스 검증을 붙이는 작은 모듈입니다.
클래스 3개, 11KB 짜리라 플러그인 jar 에 넣어도 부담이 없습니다.

## 어떻게 동작하나

구매자는 **코드를 받지도, 어디에 적지도 않습니다.**

1. 구매자가 디스코드에서 상품을 다운로드합니다.
2. 힌트샵 봇이 그 구매자 전용 값을 상품 jar 안에 `hintshop.license` 리소스로 심어 보냅니다.
3. 서버가 켜지면 이 모듈이 자기 jar 에서 그 값을 읽어 검증 서버로 보냅니다.
4. 통과하면 플러그인이 뜨고, 아니면 스스로 꺼집니다.

사용 환경은 **접속 IP 기준으로 3곳까지**입니다. 어느 환경인지는 플러그인이 알려주는
것이 아니라 검증 서버가 관측한 IP 로 판단하므로 위조할 수 없습니다. 한 번 잡힌 자리는
서버를 꺼도 저절로 풀리지 않고, 구매자가 디스코드의 라이센스 현황 패널에서 직접
비웁니다.

## 붙이는 법

`build.gradle.kts` 에 JitPack 저장소와 의존성을 넣습니다.

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.BBI331:hintshop-license:1.0.0")
}
```

**그리고 이 모듈이 실제로 jar 안에 들어갔는지 꼭 확인하세요.** Gradle 의 기본 `java`
플러그인은 `implementation` 의존성을 jar 에 넣어주지 않습니다. 그대로 서버에 올리면
부팅하다 `NoClassDefFoundError` 로 죽습니다. 셰이드 플러그인을 안 쓰신다면 `jar`
태스크에 런타임 의존성을 담아주세요.

```kotlin
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { cp ->
        cp.map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
}
```

빌드한 뒤 확인하는 방법입니다. 4가 나와야 합니다.

```bash
unzip -l build/libs/your-plugin.jar | grep -c "kr/bibi/hintshop/license/.*class"
```

Bukkit 은 플러그인마다 클래스로더가 갈리므로, 여러 플러그인이 각자 이 모듈을 품고
있어도 충돌하지 않습니다.

`onEnable` 맨 앞에 한 줄을 넣습니다.

```java
@Override
public void onEnable() {
    if (!LicenseGate.enable(this, "1239778236422946897")) {
        return;   // 검증 실패 — 비활성화까지 이미 끝났다
    }
    // ... 원래 하던 초기화
}
```

두 번째 인자는 **이 플러그인이 파는 상품에 연동된 디스코드 역할 ID** 입니다.
힌트샵의 상품마다 역할이 하나씩 걸려 있고(다운로드 권한이 그 역할로 갈립니다),
검증 서버가 그 값을 대조해서 다른 상품의 라이센스 파일을 가져다 쓰는 것을 막습니다.
디스코드에서 역할 우클릭 → ID 복사하기로 얻습니다.

`onDisable` 에서는 아무것도 부르지 않아도 됩니다. 자리는 서버 종료로 풀리는 것이
아니기 때문입니다.

## 검증에 실패하면

`enable()` 이 `false` 를 돌려주기 전에 플러그인 비활성화까지 끝내고, 콘솔에 사유를
남깁니다. 호출부는 그냥 `return` 하면 됩니다. `onDisable` 이 중간에 불릴 수 있으므로
정리 코드는 전부 null 검사를 해두세요.

| 콘솔 사유 | 뜻 |
| --- | --- |
| carries no license information | 봇을 거치지 않고 얻은 파일 |
| is not registered | 검증 서버에 없는 값 |
| belongs to a different product | 다른 상품의 라이센스 파일 |
| can no longer be used | 무효화된 값 |
| usage-environment limit is full | 3곳을 이미 쓰는 중 |

검증 서버에 **연결이 안 될 때는 끄지 않습니다.** 최근 72시간 안에 성공한 기록이 있으면
유예 모드로 그냥 띄웁니다. 인터넷이 잠깐 끊겼다고 구매자 서버가 멈추면 안 되기
때문입니다. 가동 중에는 10분마다 다시 확인합니다.

## 만드는 쪽에서 알아둘 것

- 검증 서버 주소는 소스에 박아서 빌드합니다. config 로 빼면 구매자가 가짜 서버로
  바꿔치기할 수 있습니다.
- `hintshop.license` 가 없는 jar 은 부팅되지 않습니다. 개발 중에 직접 빌드한 jar 로
  테스트하실 때는 그 파일을 손으로 넣거나, 라이센스를 켜지 않은 상태로 두세요.
- **jar 안의 값은 숨겨지지 않습니다.** jar 은 zip 이라 열어보면 읽힙니다. 이 방식이
  막아주는 것은 "코드를 퍼뜨려 여러 서버에서 돌리는 것"이고, 그 실제 방어선은 IP
  자리 제한입니다.

## 관련 저장소

- [hintshop](https://github.com/BBI331/hintshop) — 디스코드 봇(값을 심는 쪽)과
  검증 서버(Cloudflare Worker)
