#!/usr/bin/env python3
"""에셋 DB 를 원격 데이터 갱신용으로 GitHub Pages 리포에 게시한다.

사용법:
    python3 tools/publish_db.py <kim2214.github.io 리포 경로> [DB 경로]
    (DB 경로 기본값: app/src/main/assets/petmap.db)

결과 (리포 안):
    petmap/data/<identity_hash>/petmap-<기준일>.db.gz
    petmap/data/<identity_hash>/manifest.json   ← 앱이 읽는 파일

앱(PlaceDataUpdaterImpl)은 하루 한 번 자기 DB 의 identity_hash 경로의 manifest.json 을 읽고,
기준일(dataDate)이 자기 meta.data_date 보다 늦을 때만 gz 를 받아 SHA-256·장소 수를 확인한 뒤 교체한다.
→ 같은 기준일로 다시 게시하면 이미 그 날짜를 가진 설치에는 전달되지 않는다. 고칠 게 있으면 --data-date 를 올려
  build_db.py 로 다시 만든다.
→ 스키마(identity_hash)별로 디렉터리가 나뉘므로 엔티티가 바뀐 새 데이터는 구버전 앱에 가지 않는다.

이 스크립트는 파일만 만든다. 게시는 github.io 리포에서 직접 커밋·푸시한다.
"""
import gzip
import hashlib
import json
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(__file__))
import build_db  # noqa: E402

DATA_DIR = os.path.join("petmap", "data")
MANIFEST = "manifest.json"


def read_db_info(db_path):
    """게시 전 검증 + 매니페스트에 쓸 값 (identity_hash, data_date, place_count)."""
    db = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        check = db.execute("PRAGMA quick_check").fetchone()[0]
        if check != "ok":
            raise SystemExit(f"오류: 무결성 검사 실패 ({check})")
        identity_hash = db.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()[0]
        row = db.execute("SELECT value FROM meta WHERE key = ?", (build_db.META_KEY_DATA_DATE,)).fetchone()
        if row is None:
            raise SystemExit("오류: meta.data_date 없음 — build_db.py 로 만든 DB 인지 확인")
        data_date = row[0]
        place_count = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        fts_count = db.execute("SELECT COUNT(*) FROM places_fts").fetchone()[0]
    finally:
        db.close()
    if identity_hash != build_db.ROOM_IDENTITY_HASH:
        raise SystemExit(f"오류: identity_hash {identity_hash} != build_db.ROOM_IDENTITY_HASH")
    if place_count == 0 or fts_count != place_count:
        raise SystemExit(f"오류: 장소 {place_count}건 / FTS {fts_count}건 — 색인이 맞지 않음")
    return identity_hash, data_date, place_count


def gzip_file(src, dest):
    # mtime=0: 같은 DB 면 같은 gz(같은 SHA-256)가 나오게 해 불필요한 diff 를 막는다
    with open(src, "rb") as fin, open(dest, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as fout:
            while chunk := fin.read(1 << 20):
                fout.write(chunk)


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def publish(site_repo, db_path):
    identity_hash, data_date, place_count = read_db_info(db_path)
    out_dir = os.path.join(site_repo, DATA_DIR, identity_hash)
    os.makedirs(out_dir, exist_ok=True)

    name = f"petmap-{data_date}.db.gz"
    gz_path = os.path.join(out_dir, name)
    gzip_file(db_path, gz_path)
    manifest = {
        "dataDate": data_date,
        "file": name,
        "sha256": sha256_of(gz_path),
        "size": os.path.getsize(gz_path),
        "placeCount": place_count,
    }
    with open(os.path.join(out_dir, MANIFEST), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    # 이전 기준일 파일은 지운다 (리포가 계속 커지지 않게 — 매니페스트가 가리키는 것만 남김)
    for old in os.listdir(out_dir):
        if old.endswith(".db.gz") and old != name:
            os.remove(os.path.join(out_dir, old))
    return out_dir, manifest


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    site_repo = sys.argv[1]
    db_path = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/petmap.db"
    if not os.path.isdir(os.path.join(site_repo, ".git")):
        raise SystemExit(f"오류: {site_repo} 는 git 리포가 아님 (kim2214.github.io 체크아웃 경로를 지정)")
    out_dir, manifest = publish(site_repo, db_path)
    print(f"✓ {out_dir}")
    print(f"  기준일 {manifest['dataDate']} · 장소 {manifest['placeCount']:,}곳 · gz {manifest['size'] / 1e6:.1f}MB")
    print("다음: github.io 리포에서 커밋·푸시하면 설치된 앱이 하루 안에(Wi-Fi) 받아 간다.")


if __name__ == "__main__":
    main()
