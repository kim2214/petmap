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
   - 카테고리는 `category_name()`이 `PlaceCategory.fromRaw`와 동일하게 매핑한다. CSV의
     카테고리 컬럼 값이 새로 등장하면 이 매핑을 함께 갱신할 것.

3. 검증 — 재생성 후 실제 앱을 빌드·설치해 첫 실행이 크래시 없이 뜨는지 확인:
   ```bash
   timeout 580 ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.kimdev.petmap/.MainActivity
   adb logcat -d | grep -iE "FATAL|AndroidRuntime|Room|identity"
   ```
   FTS 검색(`data/local/PlaceFts.kt`)이 동작하는지 목록 화면 검색으로 확인한다.

## 스키마를 바꿨을 때 (중요)

엔티티(`PlaceEntity`/`FavoriteEntity`)나 `@Database` version을 바꾸면
`ROOM_IDENTITY_HASH`가 달라진다. 이때 `tools/build_db.py`가 만든 옛 DB는 로드 시
"Room cannot verify the data integrity" 크래시를 낸다. 절차:

1. `build_db.py`에서 새 DB를 임시로 만들지 말고, **앱을 먼저 한 번 빌드·실행**해 Room이 생성한
   최신 DB를 얻는다.
2. 그 DB의 값을 확인:
   ```bash
   adb pull /data/data/com.kimdev.petmap/databases/<db이름> current.db   # 또는 앱 데이터 경로
   sqlite3 current.db "SELECT identity_hash FROM room_master_table; PRAGMA user_version;"
   ```
3. 출력값을 `tools/build_db.py`의 `ROOM_IDENTITY_HASH`와 `DB_VERSION`에 반영한다.
   (현재 기준: version 2, identity_hash는 스크립트 상단 상수 참조.)
4. 다시 `python3 tools/build_db.py`로 프리빌트 DB를 재생성한다.

## 로컬 검증용 sqlite 스니펫

```bash
cp app/src/main/assets/petmap.db test.db
sqlite3 test.db "PRAGMA user_version; SELECT COUNT(*) FROM places;"
sqlite3 test.db "SELECT DISTINCT category FROM places LIMIT 12;"
```

FTS 색인은 앱의 `Migration(2→3)`이 첫 실행 때 만든다. 에셋 DB에는 FTS 테이블이 없어도 정상이다.
