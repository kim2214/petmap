#!/usr/bin/env python3
"""
공공데이터 CSV → 프리빌트 Room DB(app/src/main/assets/petmap.db) 생성 스크립트.

앱이 Room(`createFromAsset`)으로 그대로 로드하므로, 스키마/인덱스/room_master_table
(identity_hash)/user_version 을 앱의 Room 스키마와 동일하게 맞춘다.

사용법:
    python3 tools/build_db.py [--data-date=YYYY-MM-DD] "<CSV 경로>" ["<CSV 경로 2>" ...] [출력경로.db]
    # 마지막 인자가 .db 로 끝나면 출력경로, 아니면 전부 입력 CSV
    # 출력경로 기본값: app/src/main/assets/petmap.db
    # --data-date: 수록 데이터 기준일(설정 화면 "데이터 갱신일"에 표시). 생략 시 오늘 날짜

입력 CSV 규칙:
- 여러 파일을 주면 순서대로 합친다. 같은 장소(이름+좌표)는 먼저 온 파일이 우선.
- 인코딩은 UTF-8(-sig) → CP949 순으로 자동 판별한다.
- 반려동물 동반 가능 여부 컬럼("반려동물 동반 가능정보" 또는 "애완동물 출입 여부")이
  **반드시 있어야 하며, 값이 Y 인 행만 넣는다.** 컬럼이 없는 데이터셋(예: 캠핑)은
  동반 가능 여부를 판별할 수 없으므로 에러로 거부한다.
- 전화번호/홈페이지는 데이터셋별 컬럼명 차이를 흡수한다(시설 전화번호, 웹사이트).
- 실내/실외 여부("장소(실내) 여부"/"장소(실외)여부")는 Y→1, N→0, 그 외·컬럼 없음 → NULL(정보 없음).
  v5 까지는 NOT NULL 0 이라 컬럼이 없는 데이터셋(액티비티)이 "동반 불가"로 표시됐다.
  제한사항이 "야외만/실외만 동반 가능"이면 실내=0·실외=1 로 정규화한다(원본 34건이 실내 Y 로 모순).
- 식약처 "반려동물 동반출입 음식점" 목록은 tools/fetch_restaurants.py 가 지오코딩해 같은 컬럼 형식의
  CSV 로 만들어 준다. 좌표가 지오코딩 결과라 기본 데이터셋과 좌표가 조금 달라도 같은 가게일 수 있으므로,
  **정규화한 이름이 같고 150m 안**이면 중복으로 보고 먼저 온 파일을 유지한다.

버전 주의 (데이터 갱신 = 버전 업): 앱은 마이그레이션 없이 **DB 버전이 오르면 기존 파일을 지우고
에셋을 재복사**한다(PetMapDatabase.VERSION / RECREATED_FROM, 즐겨찾기는 미러로 보존·복원).
따라서 장소 데이터만 바뀌어도 기존 설치에 전달하려면 DB_VERSION 과 앱의 PetMapDatabase.VERSION 을
함께 올리고, 직전 버전을 RECREATED_FROM 에 추가해야 한다. 버전을 그대로 두면 신규 설치만 새 데이터를 받는다.

FTS 주의: FTS 색인(places_fts)을 **동봉**한다. 색인을 여기서 만들어 두면 신규 설치가 첫 실행에
2.2만 행을 재색인하는 비용이 사라진다. fts_index() 는 앱의 PlaceFts.index()(글자 단위 유니그램)와
반드시 동일해야 한다. 어긋나면 검색이 조용히 빈 결과를 반환한다 (androidTest 의 PetMapDatabaseAssetTest 가 검증).

meta 테이블: data_date(기준일)·built_at(생성일)을 기록한다. 앱은 data_date 를 설정 화면에 표시한다
(MetaEntity.KEY_DATA_DATE 와 키 이름이 같아야 함).

identity_hash 주의: 엔티티(PlaceEntity/FavoriteEntity) 스키마가 바뀌면 ROOM_IDENTITY_HASH 도
바뀐다. 기준값은 app/schemas/com.kimdev.petmap.data.local.PetMapDatabase/<version>.json 의
"identityHash" (exportSchema=true 로 빌드 시 자동 갱신됨). 동기화가 어긋나면
androidTest 의 PetMapDatabaseAssetTest 가 실패한다.
"""
import csv
import datetime
import math
import os
import re
import sqlite3
import sys

ROOM_IDENTITY_HASH = "569b4fd80162fae214d1c81784d21f2e"  # app/schemas/**/6.json 의 identityHash
DB_VERSION = 6  # v6: indoorAllowed/outdoorAllowed nullable(정보 없음) + 식약처 음식점. 앱 PetMapDatabase.VERSION 과 동일

# 앱이 읽는 meta 키 (MetaEntity.KEY_DATA_DATE)
META_KEY_DATA_DATE = "data_date"

DDL = [
    "CREATE TABLE android_metadata (locale TEXT)",
    "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "CREATE TABLE `favorites` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, "
    "`roadAddress` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `phone` TEXT, "
    "`memo` TEXT, `addedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
    "CREATE TABLE `places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, "
    "`roadAddress` TEXT NOT NULL, `lotAddress` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, "
    "`phone` TEXT, `operatingTime` TEXT, `closedDays` TEXT, `homepage` TEXT, `allowedPetSize` TEXT, "
    "`restriction` TEXT, `indoorAllowed` INTEGER, `outdoorAllowed` INTEGER, "
    "`parkingAvailable` INTEGER, `fee` TEXT, `petFee` TEXT, `description` TEXT, PRIMARY KEY(`id`))",
    "CREATE INDEX `index_places_lat_lng` ON `places` (`lat`, `lng`)",
    "CREATE INDEX `index_places_category` ON `places` (`category`)",
    "CREATE INDEX `index_places_name` ON `places` (`name`)",
    "CREATE TABLE `meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
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
        "카페": "CAFE", "식당": "RESTAURANT", "제과점": "CAFE", "펜션": "ACCOMMODATION", "호텔": "ACCOMMODATION",
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
    if "카페" in r or "제과" in r or "베이커리" in r: return "CAFE"
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


def yn(value):
    """Y/N → 1/0, 그 외(빈칸·컬럼 없음) → None(정보 없음)."""
    return {"Y": 1, "N": 0}.get((value or "").strip())


OUTDOOR_ONLY_HINTS = ("야외만", "실외만")


def normalize_name(name):
    """중복 판정용 이름: 공백·괄호 내용·법인 표기·기호 제거, 소문자."""
    n = re.sub(r"\(.*?\)", "", name)
    n = re.sub(r"\(주\)|㈜|주식회사", "", n)
    n = re.sub(r"[^0-9A-Za-z가-힣]", "", n)
    return n.lower()


def near(lat1, lng1, lat2, lng2, meters=150.0):
    """대략적 평면 거리(한반도 위도에서 충분)."""
    dlat = (lat1 - lat2) * 111_000.0
    dlng = (lng1 - lng2) * 111_000.0 * math.cos(math.radians(lat1))
    return math.hypot(dlat, dlng) <= meters


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
    data_date = datetime.date.today().isoformat()
    for a in [a for a in args if a.startswith("--data-date")]:
        args.remove(a)
        if "=" not in a:
            raise SystemExit("오류: --data-date=YYYY-MM-DD 형식으로 지정")
        value = a.split("=", 1)[1]
        try:
            data_date = datetime.date.fromisoformat(value).isoformat()
        except ValueError:
            raise SystemExit(f"오류: 잘못된 --data-date 값 {value!r} (YYYY-MM-DD)")
    if not args:
        print(__doc__)
        sys.exit(1)
    if args[-1].endswith(".db"):
        csv_paths, out_path = args[:-1], args[-1]
    else:
        csv_paths, out_path = args, "app/src/main/assets/petmap.db"
    if not csv_paths:
        print(__doc__)
        sys.exit(1)

    rows = {}  # id -> tuple (dedup: 첫 등장 유지 — 먼저 준 파일이 우선)
    by_name = {}  # normalize_name -> [(lat, lng)] (좌표가 다른 같은 가게 판정용)
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
        added = filtered = near_dups = 0
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
            key = normalize_name(name)
            if key and any(near(lat, lng, la, ln) for la, ln in by_name.get(key, ())):
                near_dups += 1
                continue
            road = meaningful(r.get("도로명주소")) or (r.get("지번주소") or "").strip()
            # 주차: Y/N → 1/0, 그 외(컬럼 없음 포함) → NULL
            parking_raw = (first_col(r, "주차 가능여부", "주차장 여부") or "").strip()
            parking = {"Y": 1, "N": 0}.get(parking_raw)
            # 요금: "변동"이 실측 63k/64k 로 정보가 없다시피 하다 → 노이즈로 보고 제외
            fee = meaningful(r.get("입장(이용료)가격 정보"))
            if fee == "변동":
                fee = None
            # 설명: 카테고리명을 반복한 값("동물약국" 등)이 대부분 → 실질 설명만 남긴다
            desc = meaningful(r.get("기본 정보_장소설명"))
            if desc == (r.get("카테고리3") or "").strip():
                desc = None
            restriction = meaningful(r.get("반려동물 제한사항"))
            indoor = yn(r.get("장소(실내) 여부"))
            outdoor = yn(r.get("장소(실외)여부"))
            if restriction and any(h in restriction for h in OUTDOOR_ONLY_HINTS):
                indoor, outdoor = 0, 1
            rows[pid] = (
                pid, name, category_name(r.get("카테고리3"), r.get("카테고리1")), road,
                (r.get("지번주소") or "").strip(), lat, lng,
                meaningful(first_col(r, "전화번호", "시설 전화번호")), meaningful(r.get("운영시간")),
                meaningful(r.get("휴무일")), meaningful(first_col(r, "홈페이지", "웹사이트")),
                meaningful(r.get("입장 가능 동물 크기")), restriction,
                indoor, outdoor,
                parking, fee, meaningful(r.get("애견 동반 추가 요금")), desc,
            )
            if key:
                by_name.setdefault(key, []).append((lat, lng))
            added += 1
        print(f"  {os.path.basename(csv_path)}: 동반가능 Y 이외 제외 {filtered}건, 신규 반영 {added}건, "
              f"이름·150m 중복 제외 {near_dups}건")

    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    db = sqlite3.connect(out_path)
    for ddl in DDL:
        db.execute(ddl)
    db.execute("INSERT INTO android_metadata VALUES ('ko_KR')")
    db.execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)", (ROOM_IDENTITY_HASH,))
    db.executemany(
        "INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows.values()
    )
    build_fts(db)
    db.executemany(
        "INSERT INTO meta (`key`, `value`) VALUES (?, ?)",
        [(META_KEY_DATA_DATE, data_date), ("built_at", datetime.date.today().isoformat())],
    )
    db.execute(f"PRAGMA user_version={DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"✓ {out_path} 생성: 고유 장소 {len(rows)}건, 기준일 {data_date}, v{DB_VERSION}")


if __name__ == "__main__":
    main()
