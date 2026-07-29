# 작업 인수인계 (2026-07-29 세션)

다른 PC 에서 작업을 이어가기 위한 요약. 완료되면 이 파일은 지워도 된다.
빌드·릴리스·DB 등 영구 규칙은 `CLAUDE.md` 참고 — 여기는 "지금 남은 일"만 담는다.

## 현재 상태

- `main` = 스토어 배포본(v1.5 태그, versionCode 6) + 의존성 범프 + lint 정리, 전부 푸시·CI 통과.
- ads 25.4.0 / location 21.4.0 / coroutines 1.11.0 반영됨. Kotlin 은 2.3.0 유지(2.4.10 PR 은 닫음).
- 에뮬레이터에서 지도(타일·클러스터)·광고 배너·DB 동작 검증 완료. **arm64 실기기는 미검증** —
  다음 릴리스를 내부 테스트 트랙에 먼저 올려 확인할 것.

## 남은 작업 (우선순위순)

1. **프로덕션 Crashlytics 확인** — Firebase 콘솔에서 1.5 의 크래시/이벤트 수신 여부 확인.
   `google-services.json` 없이 빌드하면 조용히 Firebase 전체가 빠진다. 릴리스 빌드 머신에
   그 파일이 있었는지가 관건.
2. **bundleRelease 가드** — keystore/`google-services.json` 없으면 bundleRelease 를 실패시키는
   Gradle 체크 추가 (assembleRelease 는 현행 폴백 유지).
3. **데이터 기준일 표시** — `tools/build_db.py` 가 빌드 날짜를 DB 에 스탬프 → 설정 화면
   "데이터 출처" 섹션에 노출.
4. **Play In-App Updates** — 데이터가 앱 업데이트로만 갱신되는 구조라 업데이트 유도가 곧
   데이터 신선도. `play-review` 와 같은 계열 라이브러리.
5. **DetailViewModel / FavoriteViewModel 단위 테스트** — 즐겨찾기 토글 로직이 유일한 공백.
6. **targetSdk 36 → 37** — Play 정책 마감 전. 실기기 검증 동반.
7. 보류 항목: monochrome 런처 아이콘(벡터 아이콘 제작 필요), firebase-bom 34.x 메이저
   (Crashlytics 등록 이슈 전력 — ca6c694 참고), AGP 전환 플래그 2개(gradle.properties 주석 참고,
   KSP 의 내장 Kotlin 지원 대기), `AdSize.getLarge*` 전환(더 큰 배너 상품 — 수익/UX 결정 필요).

## 새 PC 셋업 시 주의

- `local.properties` 의 `naver.map.clientId` 는 NCP 콘솔의 **Client ID(Key ID, 10자 내외)** 다.
  **Client Secret(40자)을 넣으면 지도가 조용히 빈 격자만 나온다** (401 은 logcat 의 `NaverMap`
  태그에만 찍힘). 2026-07-29 실제 발생.
- Dependabot 은 월간 스캔·동시 3건으로 설정됨. `concurrent-futures` 는 ignore 처리
  (constraints 수동 핀 — `app/build.gradle.kts` 주석 참고).
- GitHub 리포 Settings 에서 **Dependabot 보안 업데이트** 활성 여부 확인 권장
  (버전 업데이트와 별개 기능, `.github/dependabot.yml` 과 무관).
