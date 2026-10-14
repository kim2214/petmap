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

    def test_same_db_gives_same_sha_and_old_files_are_removed(self):
        make_db(self.db, data_date="2026-09-10")
        _, first = p.publish(self.site, self.db)
        _, again = p.publish(self.site, self.db)
        self.assertEqual(first["sha256"], again["sha256"])

        os.remove(self.db)
        make_db(self.db, data_date="2026-10-14")
        out_dir, _ = p.publish(self.site, self.db)
        self.assertEqual(sorted(f for f in os.listdir(out_dir) if f.endswith(".gz")), ["petmap-2026-10-14.db.gz"])

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
