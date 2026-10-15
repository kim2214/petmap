#!/usr/bin/env python3
"""publish_db.py 테스트: python3 -m unittest tools/test_publish_db.py"""
import gzip
import hashlib
import json
import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_db as b  # noqa: E402
import publish_db as p  # noqa: E402


def make_db(path, data_date="2026-10-14", identity_hash=b.ROOM_IDENTITY_HASH, places=3):
    db = sqlite3.connect(path)
    for ddl in b.DDL:
        db.execute(ddl)
    db.execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)", (identity_hash,))
    for i in range(places):
        db.execute(
            "INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (f"id{i}", f"카페{i}", "CAFE", "서울특별시 중구 1", "", 37.5, 127.0,
             None, None, None, None, None, None, 1, 1, None, None, None, None, None, f"카페{i}"),
        )
    b.build_fts(db)
    db.execute("INSERT INTO meta (`key`, `value`) VALUES (?, ?)", (b.META_KEY_DATA_DATE, data_date))
    db.commit()
    db.close()


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.site = os.path.join(self.tmp.name, "site")
        os.makedirs(os.path.join(self.site, ".git"))
        self.db = os.path.join(self.tmp.name, "petmap.db")

    def tearDown(self):
        self.tmp.cleanup()

    def test_writes_manifest_matching_gz(self):
        make_db(self.db)
        out_dir, manifest = p.publish(self.site, self.db)
        self.assertEqual(out_dir, os.path.join(self.site, "petmap", "data", b.ROOM_IDENTITY_HASH))
        with open(os.path.join(out_dir, "manifest.json"), encoding="utf-8") as f:
            self.assertEqual(json.load(f), manifest)
        gz = os.path.join(out_dir, manifest["file"])
        self.assertEqual(manifest["file"], "petmap-2026-10-14.db.gz")
        self.assertEqual(manifest["placeCount"], 3)
        self.assertEqual(manifest["size"], os.path.getsize(gz))
        with open(gz, "rb") as f:
            self.assertEqual(manifest["sha256"], hashlib.sha256(f.read()).hexdigest())
        # 풀면 원본과 같다
        with gzip.open(gz, "rb") as f, open(self.db, "rb") as orig:
            self.assertEqual(f.read(), orig.read())

    def _gz_files(self, out_dir):
        return sorted(f for f in os.listdir(out_dir) if f.endswith(".gz"))

    def _rebuild(self, data_date, places=3):
        os.remove(self.db)
        make_db(self.db, data_date=data_date, places=places)

    def test_same_db_republish_is_allowed_and_gives_same_sha(self):
        make_db(self.db, data_date="2026-09-10")
        _, first = p.publish(self.site, self.db)
        _, again = p.publish(self.site, self.db)
        self.assertEqual(first["sha256"], again["sha256"])

    def test_keeps_previous_gz_once_then_removes_older(self):
        make_db(self.db, data_date="2026-09-10")
        p.publish(self.site, self.db)
        self._rebuild("2026-10-14")
        out_dir, _ = p.publish(self.site, self.db)
        # CDN 이 옛 매니페스트를 캐시하는 동안 직전 gz 가 404 가 되지 않게 남긴다
        self.assertEqual(self._gz_files(out_dir), ["petmap-2026-09-10.db.gz", "petmap-2026-10-14.db.gz"])
        self._rebuild("2026-10-20")
        p.publish(self.site, self.db)
        self.assertEqual(self._gz_files(out_dir), ["petmap-2026-10-14.db.gz", "petmap-2026-10-20.db.gz"])

    def test_rejects_date_not_after_published_unless_forced(self):
        make_db(self.db, data_date="2026-10-14")
        out_dir, published = p.publish(self.site, self.db)
        # 같은 날짜인데 내용이 다르면 앱이 받지 않으므로 거부 (게시물은 그대로)
        self._rebuild("2026-10-14", places=4)
        with self.assertRaises(SystemExit):
            p.publish(self.site, self.db)
        self.assertEqual(p.read_manifest(out_dir), published)
        self.assertFalse(any(f.endswith(".tmp") for f in os.listdir(out_dir)))
        # 더 이른 날짜도 거부
        self._rebuild("2026-10-01")
        with self.assertRaises(SystemExit):
            p.publish(self.site, self.db)
        # --force 면 덮어쓴다
        _, forced = p.publish(self.site, self.db, force=True)
        self.assertEqual(forced["dataDate"], "2026-10-01")

    def test_rejects_identity_hash_mismatch(self):
        make_db(self.db, identity_hash="other")
        with self.assertRaises(SystemExit):
            p.publish(self.site, self.db)

    def test_rejects_missing_fts_rows(self):
        make_db(self.db)
        db = sqlite3.connect(self.db)
        db.execute("DELETE FROM places_fts")
        db.commit()
        db.close()
        with self.assertRaises(SystemExit):
            p.publish(self.site, self.db)


if __name__ == "__main__":
    unittest.main()
