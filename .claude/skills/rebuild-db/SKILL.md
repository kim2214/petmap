---
name: rebuild-db
description: 공공데이터 CSV로 프리빌트 Room DB(app/src/main/assets/petmap.db)를 재생성한다. 반려동물 동반 장소 데이터셋이 갱신되어 앱 내장 DB를 새로 만들어야 할 때 사용.
---

# rebuild-db — 프리빌트 Room DB 재생성

앱은 `app/src/main/assets/petmap.db`를 Room `createFromAsset`으로 로드한다. 파싱/시딩이 없으므로
**DB 파일의 스키마·인덱스·`room_master_table`(identity_hash)·`user_version`이 앱의 Room 스키마와
정확히 일치해야** 첫 실행이 크래시 없이 로드된다. `tools/build_db.py`가 이를 재현한다.

## 재생성 절차

1. 새 CSV 확보 — [공공데이터포털](https://www.data.go.kr) "전국 반려동물 동반 가능 문화시설" (UTF-8).
   추가 데이터셋: "전국 문화 여가 활동 시설(액티비티)" (CP949, 인코딩 자동 판별됨).
   로그인 없이 받을 수 있다 (2026-09 기준 직접 다운로드 URL — 바뀌면 데이터셋 페이지 15111389 / 15111393 에서 다시 찾는다):
   ```bash
   curl -sL -A 'Mozilla/5.0' -o pet.csv 'https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003214426&fileDetailSn=1'
   curl -sL -A 'Mozilla/5.0' -o act.csv 'https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003000511&fileDetailSn=1'
   ```
   선택: 식약처 "반려동물 동반출입 음식점"(2026-03 제도, 약 2,700곳, 좌표 없음) →
   `python3 tools/fetch_restaurants.py` 가 다운로드·지오코딩해 `tools/data/restaurants.csv` 를 만든다
   (네이버 Geocoding Client Secret 또는 카카오 REST 키 필요, 좌표 캐시 `tools/data/geocode_cache.json` 재사용).
   이 파일은 **세 번째 인자**로 넘긴다(기본 데이터셋과 이름·150m 중복이면 기본 쪽이 유지된다).

2. **DB 버전을 올린다 (데이터만 바뀌어도 필수).** 앱은 마이그레이션 없이 버전이 오르면 기존 파일을
   지우고 에셋을 재복사하므로, 버전을 그대로 두면 기존 설치는 새 데이터를 받지 못한다.
   - `PetMapDatabase.VERSION` +1, 직전 버전을 `RECREATED_FROM`에 추가
   - `tools/build_db.py`의 `DB_VERSION`을 같은 값으로
   - 엔티티가 바뀌지 않았으면 `identityHash`는 그대로다(버전은 해시에 포함되지 않음). 바뀌었으면 아래 절 참고.
   - 즐겨찾기는 `FavoriteBackup` 미러로 보존·복원되므로 별도 마이그레이션 코드는 필요 없다.

3. 스크립트 실행 (여러 CSV 가능 — **기본 데이터셋을 먼저** 줘야 중복 시 기본 메타가 우선):
   ```bash
   python3 tools/build_db.py --data-date=YYYY-MM-DD "<반려동물 CSV>" ["<액티비티 CSV>" ...]
   # --data-date: 공공데이터 기준일(설정 화면 "데이터 갱신일"). 생략 시 오늘
   # 출력 기본값: app/src/main/assets/petmap.db (마지막 인자가 .db 면 출력경로)
   # 성공 시: "✓ app/src/main/assets/petmap.db 생성: 고유 장소 N건, 기준일 ..., vN"
   ```
   - 원본에 동일 레코드가 대량 중복 → `이름+좌표` 기준으로 정제한다.
   - **동반 가능 컬럼("반려동물 동반 가능정보"/"애완동물 출입 여부")이 Y 인 행만 수록**된다.
     이 컬럼이 없는 데이터셋(예: 캠핑)은 에러로 거부된다.
   - 카테고리 원문 → enum 매핑은 `category_name()`이 단일 소스다. CSV의 카테고리 컬럼 값이
     새로 등장하면 이 매핑을 갱신할 것.
   - 실내/실외 여부는 Y/N → 1/0, 그 외·컬럼 없음 → **NULL(정보 없음)**. 앱은 NULL 을 "불가"가 아니라
     미표시/안내로 다룬다. 제한사항이 "야외만/실외만"이면 실내 0·실외 1 로 정규화한다.
   - FTS 색인(`places_fts`)은 스크립트가 함께 생성해 동봉한다. `fts_index()`는 앱의
     `PlaceFts.index()`(글자 단위 유니그램)와 반드시 동일해야 한다 — 어긋나면 검색이 조용히 0건이 된다.

4. 검증 — 계약 테스트(실기기/에뮬레이터)가 로드·개수·FTS 검색·meta 와 구버전 재생성을 확인한다:
   ```bash
   timeout 580 ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.package=com.kimdev.petmap.data.local
   ```
   기기 없이 스키마 동기화만 빠르게 확인하려면 `python3 tools/verify_db.py` (최신 schemas JSON 대조).
   추가로 실제 앱을 설치해 첫 실행과 목록 검색을 확인:
   ```bash
   timeout 580 ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.kimdev.petmap/.MainActivity
   adb logcat -d | grep -iE "FATAL|AndroidRuntime|Room|identity"
   ```

## 스키마를 바꿨을 때 (중요)

엔티티(`PlaceEntity`/`FavoriteEntity`) 스키마를 바꾸면 `ROOM_IDENTITY_HASH`가 달라진다.
이때 `tools/build_db.py`가 만든 옛 DB는 로드 시 "Room cannot verify the data integrity"
크래시를 낸다. 절차:

1. `PetMapDatabase.VERSION` 을 올리고(`RECREATED_FROM` 에 직전 버전 추가) 빌드한다(`:app:compileDebugKotlin`).
   `exportSchema=true`라서 `app/schemas/com.kimdev.petmap.data.local.PetMapDatabase/<version>.json`이
   자동 갱신된다. 마이그레이션 클래스는 만들지 않는다 — 구버전은 에셋 재복사로 처리된다.
2. 그 JSON의 `"identityHash"` 값을 `tools/build_db.py`의 `ROOM_IDENTITY_HASH`에,
   새 version 을 `DB_VERSION`에 반영한다. DDL 이 바뀌었으면 `DDL` 목록도 schemas JSON 과 맞춘다.
3. 다시 `python3 tools/build_db.py`로 프리빌트 DB를 재생성한다.
4. `FavoriteEntity` 컬럼을 바꿨다면 `FavoriteBackup` 의 JSON 읽기/쓰기와 `readLegacyFavorites`(구버전 컬럼 폴백)도
   맞춘다 — 즐겨찾기가 살아남는 유일한 경로다.

## 로컬 검증용 sqlite 스니펫

```bash
cp app/src/main/assets/petmap.db test.db
sqlite3 test.db "PRAGMA user_version; SELECT COUNT(*) FROM places; SELECT COUNT(*) FROM places_fts;"
sqlite3 test.db "SELECT DISTINCT category FROM places LIMIT 12;"
```

에셋 DB는 FTS 테이블(`places_fts`)을 동봉한다 — `places` 와 행 수가 같아야 한다.
`meta` 테이블에 `data_date`(기준일)·`built_at`(생성일)이 있어야 한다.

## CSV 없이 버전/메타만 갱신할 때

원본 CSV 가 없고 스키마·버전만 바꿔야 하면(예: 엔티티 추가) 기존 에셋을 sqlite3 로 직접 손본다:
`CREATE TABLE`(schemas JSON 의 createSql 그대로) → `UPDATE room_master_table SET identity_hash=…` →
`PRAGMA user_version=N` → `VACUUM`. 끝나면 반드시 `python3 tools/verify_db.py` 로 대조한다.
