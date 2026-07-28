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

2. 스크립트 실행:
   ```bash
   python3 tools/build_db.py "<새 CSV 경로>"
   # 출력 기본값: app/src/main/assets/petmap.db
   # 성공 시: "✓ app/src/main/assets/petmap.db 생성: 고유 장소 N건"
   ```
   - 원본에 동일 레코드가 대량 중복 → `이름+좌표` 기준으로 정제한다.
   - 카테고리 원문 → enum 매핑은 `category_name()`이 단일 소스다. CSV의 카테고리 컬럼 값이
     새로 등장하면 이 매핑을 갱신할 것.
   - FTS 색인(`places_fts`)은 스크립트가 함께 생성해 동봉한다. `fts_index()`는 앱의
     `PlaceFts.index()`(글자 단위 유니그램)와 반드시 동일해야 한다 — 어긋나면 검색이 조용히 0건이 된다.

3. 검증 — 계약 테스트(실기기/에뮬레이터)가 로드·개수·FTS 검색을 한 번에 확인한다:
   ```bash
   timeout 580 ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.kimdev.petmap.data.local.PetMapDatabaseAssetTest
   ```
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

1. `@Database` version 을 올리고 마이그레이션을 추가한 뒤 빌드한다(`:app:compileDebugKotlin`).
   `exportSchema=true`라서 `app/schemas/com.kimdev.petmap.data.local.PetMapDatabase/<version>.json`이
   자동 갱신된다.
2. 그 JSON의 `"identityHash"` 값을 `tools/build_db.py`의 `ROOM_IDENTITY_HASH`에,
   새 version 을 `DB_VERSION`에 반영한다. DDL 이 바뀌었으면 `DDL` 목록도 schemas JSON 과 맞춘다.
3. 다시 `python3 tools/build_db.py`로 프리빌트 DB를 재생성한다.
4. **기존 설치 사용자용 마이그레이션 경로**(v2→…→새 version 완전 체인)가 유지되는지 확인한다.
   경로가 없으면 업그레이드 시 예외가 난다(파괴적 폴백은 다운그레이드에만 허용되어 있음).

## 로컬 검증용 sqlite 스니펫

```bash
cp app/src/main/assets/petmap.db test.db
sqlite3 test.db "PRAGMA user_version; SELECT COUNT(*) FROM places; SELECT COUNT(*) FROM places_fts;"
sqlite3 test.db "SELECT DISTINCT category FROM places LIMIT 12;"
```

에셋 DB는 v3 로 FTS 테이블(`places_fts`)을 동봉한다 — `places` 와 행 수가 같아야 한다.
`Migration(2→3)`은 구버전 앱(v2 DB) 설치가 업그레이드할 때만 색인을 만든다.
