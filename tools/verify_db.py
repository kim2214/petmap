#!/usr/bin/env python3
"""
에셋 DB(app/src/main/assets/petmap.db)가 앱 Room 스키마와 동기화돼 있는지 기기 없이 검사한다.

    python3 tools/verify_db.py [DB 경로]

검사 항목: user_version == PetMapDatabase.VERSION, room_master_table.identity_hash == schemas JSON,
엔티티별 컬럼(타입·NOT NULL·PK)·인덱스 일치, places 와 places_fts 행 수 일치, meta.data_date 존재.
androidTest PetMapDatabaseAssetTest 의 오프라인 근사치 — 실기기 계약 테스트를 대체하지는 않는다.
"""
import glob
import json
import os
import sqlite3
import sys

SCHEMA_DIR = "app/schemas/com.kimdev.petmap.data.local.PetMapDatabase"


def latest_schema():
    files = sorted(glob.glob(os.path.join(SCHEMA_DIR, "*.json")), key=lambda p: int(os.path.basename(p)[:-5]))
    if not files:
        raise SystemExit(f"오류: {SCHEMA_DIR} 에 스키마 JSON 이 없음 (:app:compileDebugKotlin 먼저)")
    return json.load(open(files[-1]))["database"]


def main():
    db_path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/petmap.db"
    schema = latest_schema()
    db = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    errors = []

    version = db.execute("PRAGMA user_version").fetchone()[0]
    if version != schema["version"]:
        errors.append(f"user_version {version} != schemas JSON version {schema['version']}")
    row = db.execute("SELECT identity_hash FROM room_master_table").fetchone()
    if not row or row[0] != schema["identityHash"]:
        errors.append(f"identity_hash {row and row[0]} != {schema['identityHash']}")

    for e in schema["entities"]:
        t = e["tableName"]
        pk = e["primaryKey"]["columnNames"]
        actual = {r[1]: (r[2], bool(r[3]), r[5]) for r in db.execute(f"PRAGMA table_info(`{t}`)")}
        expected = {
            f["columnName"]: (f["affinity"], f.get("notNull", False), pk.index(f["columnName"]) + 1 if f["columnName"] in pk else 0)
            for f in e["fields"]
        }
        if actual != expected:
            errors.append(f"{t}: 컬럼 불일치\n    실제  {actual}\n    기대  {expected}")
        actual_idx = {r[1] for r in db.execute(f"PRAGMA index_list(`{t}`)") if r[3] == "c"}
        expected_idx = {i["name"] for i in e.get("indices", [])}
        if actual_idx != expected_idx:
            errors.append(f"{t}: 인덱스 불일치 실제 {actual_idx} 기대 {expected_idx}")

    places = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    fts = db.execute("SELECT COUNT(*) FROM places_fts").fetchone()[0]
    if places == 0 or places != fts:
        errors.append(f"places {places} / places_fts {fts} 행 수 불일치")
    meta = dict(db.execute("SELECT `key`, `value` FROM meta").fetchall())
    if not meta.get("data_date"):
        errors.append("meta.data_date 없음")
    db.close()

    if errors:
        print("✗ 에셋 DB 검증 실패:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    print(f"✓ {db_path}: v{version}, hash {schema['identityHash']}, 장소 {places}건, meta {meta}")


if __name__ == "__main__":
    main()
