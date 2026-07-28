#!/usr/bin/env python3
"""
공공데이터 CSV → 프리빌트 Room DB(app/src/main/assets/petmap.db) 생성 스크립트.

앱이 Room(`createFromAsset`)으로 그대로 로드하므로, 스키마/인덱스/room_master_table
(identity_hash)/user_version 을 앱의 Room 스키마와 동일하게 맞춘다.

사용법:
    python3 tools/build_db.py "<새 CSV 경로>" [출력경로]
    # 출력경로 기본값: app/src/main/assets/petmap.db

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


def category_name(raw):
    """PlaceCategory.fromRaw 와 동일한 카테고리3 → enum name 매핑."""
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


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    csv_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/petmap.db"

    rows = {}  # id -> tuple (dedup: 첫 등장 유지)
    with open(csv_path, encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
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
                pid, name, category_name(r.get("카테고리3")), road,
                (r.get("지번주소") or "").strip(), lat, lng,
                meaningful(r.get("전화번호")), meaningful(r.get("운영시간")),
                meaningful(r.get("휴무일")), meaningful(r.get("홈페이지")),
                meaningful(r.get("입장 가능 동물 크기")), meaningful(r.get("반려동물 제한사항")),
                1 if (r.get("장소(실내) 여부") or "").strip() == "Y" else 0,
                1 if (r.get("장소(실외)여부") or "").strip() == "Y" else 0,
            )

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
