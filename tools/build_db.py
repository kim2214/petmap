#!/usr/bin/env python3
"""
공공데이터 CSV → 프리빌트 Room DB(app/src/main/assets/petmap.db) 생성 스크립트.

앱이 Room(`createFromAsset`)으로 그대로 로드하므로, 스키마/인덱스/room_master_table
(identity_hash)/user_version 을 앱의 Room 스키마와 동일하게 맞춘다.

사용법:
    python3 tools/build_db.py "<CSV 경로>" ["<CSV 경로 2>" ...] [출력경로.db]
    # 마지막 인자가 .db 로 끝나면 출력경로, 아니면 전부 입력 CSV
    # 출력경로 기본값: app/src/main/assets/petmap.db

입력 CSV 규칙:
- 여러 파일을 주면 순서대로 합친다. 같은 장소(이름+좌표)는 먼저 온 파일이 우선.
- 인코딩은 UTF-8(-sig) → CP949 순으로 자동 판별한다.
- 반려동물 동반 가능 여부 컬럼("반려동물 동반 가능정보" 또는 "애완동물 출입 여부")이
  **반드시 있어야 하며, 값이 Y 인 행만 넣는다.** 컬럼이 없는 데이터셋(예: 캠핑)은
  동반 가능 여부를 판별할 수 없으므로 에러로 거부한다.
- 전화번호/홈페이지는 데이터셋별 컬럼명 차이를 흡수한다(시설 전화번호, 웹사이트).

버전/FTS 주의: 에셋은 v3 로 생성하며 FTS 색인(places_fts)을 **동봉**한다.
색인을 여기서 만들어 두면 신규 설치가 첫 실행에 2.4만 행을 재색인하는 비용이 사라진다.
- fts_index() 는 앱의 PlaceFts.index()(글자 단위 유니그램)와 반드시 동일해야 한다.
  어긋나면 검색이 조용히 빈 결과를 반환한다 (androidTest 의 PetMapDatabaseAssetTest 가 검증).
- MIGRATION_2_3 은 구버전 앱(v2 DB, FTS 없음)에서 업그레이드하는 기존 설치용으로 남아 있다.

identity_hash 주의: 엔티티(PlaceEntity/FavoriteEntity) 스키마가 바뀌면 ROOM_IDENTITY_HASH 도
바뀐다. 기준값은 app/schemas/com.kimdev.petmap.data.local.PetMapDatabase/<version>.json 의
"identityHash" (exportSchema=true 로 빌드 시 자동 갱신됨). 동기화가 어긋나면
androidTest 의 PetMapDatabaseAssetTest 가 실패한다.
"""
import csv
import os
import sqlite3
import sys

ROOM_IDENTITY_HASH = "fc2696544a8c13596c2946b867cb4d61"  # app/schemas/**/3.json 의 identityHash
DB_VERSION = 3  # FTS 동봉(v3). 앱 @Database version 과 동일

DDL = [
    "CREATE TABLE android_metadata (locale TEXT)",
    "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "CREATE TABLE `favorites` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, "
    "`roadAddress` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `phone` TEXT, PRIMARY KEY(`id`))",
    "CREATE TABLE `places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, "
    "`roadAddress` TEXT NOT NULL, `lotAddress` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, "
    "`phone` TEXT, `operatingTime` TEXT, `closedDays` TEXT, `homepage` TEXT, `allowedPetSize` TEXT, "
    "`restriction` TEXT, `indoorAllowed` INTEGER NOT NULL, `outdoorAllowed` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX `index_places_lat_lng` ON `places` (`lat`, `lng`)",
    "CREATE INDEX `index_places_category` ON `places` (`category`)",
    "CREATE INDEX `index_places_name` ON `places` (`name`)",
]

BLANK = {"", "정보없음", "해당없음", "없음", "-"}


def meaningful(s):
    s = (s or "").strip()
    return None if s in BLANK else s


# 액티비티 데이터셋(레저/체육/공원)의 카테고리3 값들 → SPORTS
SPORTS_HINTS = (
    "골프", "탁구", "테니스", "낚시", "볼링", "풋살", "농구", "야구", "클라이밍",
    "배드민턴", "스케이트", "수상스키", "유람선", "래프팅", "패러글라이딩",
    "서바이벌", "사격", "방탈출", "레일바이크", "VR", "당구", "수영",
)


def category_name(raw, raw_group=""):
    """카테고리3(+보조로 카테고리1) → PlaceCategory enum name 매핑."""
    r = (raw or "").strip()
    exact = {
        "동물병원": "HOSPITAL", "동물약국": "PHARMACY", "반려동물용품": "SHOP", "미용": "GROOMING",
        "카페": "CAFE", "식당": "RESTAURANT", "펜션": "ACCOMMODATION", "호텔": "ACCOMMODATION",
        "박물관": "CULTURE", "미술관": "CULTURE", "문예회관": "CULTURE", "여행지": "TRAVEL", "위탁관리": "CARE",
    }
    if r in exact:
        return exact[r]
    if not r:
        return "ETC"
    if "병원" in r: return "HOSPITAL"
    if "약국" in r: return "PHARMACY"
    if "용품" in r: return "SHOP"
    if "미용" in r: return "GROOMING"
    if "카페" in r: return "CAFE"
    if "식당" in r or "음식" in r: return "RESTAURANT"
    if "펜션" in r or "호텔" in r or "숙박" in r: return "ACCOMMODATION"
    if "박물" in r or "미술" in r or "문예" in r: return "CULTURE"
    if "여행" in r: return "TRAVEL"
    if any(k in r for k in SPORTS_HINTS): return "SPORTS"
    g = (raw_group or "").strip()
    if "레저" in g or "체육" in g: return "SPORTS"
    return "ETC"


def fts_index(field):
    """앱 PlaceFts.index() 와 동일: 글자(한글/영문/숫자) 유니그램을 공백으로 분리, 소문자화."""
    if not field:
        return ""
    return " ".join(ch.lower() for ch in field if ch.isalnum())


def build_fts(db):
    """places 전체로 places_fts(FTS4) 색인을 생성한다. DDL 은 앱 PlaceFts.create() 와 동일."""
    db.execute("CREATE VIRTUAL TABLE places_fts USING fts4(name, roadAddress)")
    rows = db.execute("SELECT rowid, name, roadAddress FROM places").fetchall()
    db.executemany(
        "INSERT INTO places_fts(docid, name, roadAddress) VALUES (?,?,?)",
        ((rid, fts_index(name), fts_index(road)) for rid, name, road in rows),
    )


# 데이터셋별로 이름이 다른 동반 가능 여부 컬럼 (하나는 반드시 존재해야 함)
PET_ALLOWED_COLUMNS = ("반려동물 동반 가능정보", "애완동물 출입 여부")


def read_rows(csv_path):
    """UTF-8(-sig) → CP949 순으로 인코딩을 판별해 전체 행을 읽는다."""
    last_err = None
    for enc in ("utf-8-sig", "cp949"):
        try:
            with open(csv_path, encoding=enc, newline="") as fh:
                return list(csv.DictReader(fh))
        except UnicodeDecodeError as e:
            last_err = e
    raise SystemExit(f"오류: {csv_path} 를 UTF-8/CP949 로 읽을 수 없음 ({last_err})")


def first_col(r, *names):
    """데이터셋별 컬럼명 차이를 흡수한다 (첫 번째로 존재하는 컬럼 값)."""
    for n in names:
        if n in r:
            return r[n]
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    args = sys.argv[1:]
    if args[-1].endswith(".db"):
        csv_paths, out_path = args[:-1], args[-1]
    else:
        csv_paths, out_path = args, "app/src/main/assets/petmap.db"
    if not csv_paths:
        print(__doc__)
        sys.exit(1)

    rows = {}  # id -> tuple (dedup: 첫 등장 유지 — 먼저 준 파일이 우선)
    for csv_path in csv_paths:
        file_rows = read_rows(csv_path)
        if not file_rows:
            raise SystemExit(f"오류: {csv_path} 에 데이터 행이 없음")
        pet_col = next((c for c in PET_ALLOWED_COLUMNS if c in file_rows[0]), None)
        if pet_col is None:
            raise SystemExit(
                f"오류: {csv_path} 에 동반 가능 여부 컬럼({' / '.join(PET_ALLOWED_COLUMNS)})이 없음 — "
                "동반 가능 장소만 담는 앱 취지상 이 데이터셋은 넣을 수 없다"
            )
        added = filtered = 0
        for r in file_rows:
            if (r.get(pet_col) or "").strip() != "Y":
                filtered += 1
                continue
            name = (r.get("시설명") or "").strip()
            lat_s = (r.get("위도") or "").strip()
            lng_s = (r.get("경도") or "").strip()
            if not name or not lat_s or not lng_s:
                continue
            try:
                lat = float(lat_s); lng = float(lng_s)
            except ValueError:
                continue
            pid = f"{name}_{lat_s}_{lng_s}"
            if pid in rows:
                continue
            road = meaningful(r.get("도로명주소")) or (r.get("지번주소") or "").strip()
            rows[pid] = (
                pid, name, category_name(r.get("카테고리3"), r.get("카테고리1")), road,
                (r.get("지번주소") or "").strip(), lat, lng,
                meaningful(first_col(r, "전화번호", "시설 전화번호")), meaningful(r.get("운영시간")),
                meaningful(r.get("휴무일")), meaningful(first_col(r, "홈페이지", "웹사이트")),
                meaningful(r.get("입장 가능 동물 크기")), meaningful(r.get("반려동물 제한사항")),
                1 if (r.get("장소(실내) 여부") or "").strip() == "Y" else 0,
                1 if (r.get("장소(실외)여부") or "").strip() == "Y" else 0,
            )
            added += 1
        print(f"  {os.path.basename(csv_path)}: 동반가능 Y 이외 제외 {filtered}건, 신규 반영 {added}건")

    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    db = sqlite3.connect(out_path)
    for ddl in DDL:
        db.execute(ddl)
    db.execute("INSERT INTO android_metadata VALUES ('ko_KR')")
    db.execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)", (ROOM_IDENTITY_HASH,))
    db.executemany(
        "INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows.values()
    )
    build_fts(db)
    db.execute(f"PRAGMA user_version={DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"✓ {out_path} 생성: 고유 장소 {len(rows)}건")


if __name__ == "__main__":
    main()
