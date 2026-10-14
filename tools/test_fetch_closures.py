#!/usr/bin/env python3
"""fetch_closures.py / build_db 폐업 제외 테스트: python3 -m unittest tools/test_fetch_closures.py"""
import csv
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_db as b  # noqa: E402
import fetch_closures as fc  # noqa: E402

# 광화문동물병원 좌표(EPSG:5174) — test_fetch_hospitals 와 같은 기준점
X, Y = 197383.197504794, 452972.118568389


def src(name, status, x=X, y=Y, closed_date=""):
    return {"사업장명": name, "영업상태명": status, "좌표정보(X)": str(x), "좌표정보(Y)": str(y), "폐업일자": closed_date}


class ClosedRowsTest(unittest.TestCase):
    def test_closed_without_open_record_nearby(self):
        rows = fc.closed_rows([src("행복동물병원", "폐업", closed_date="2024-01-02")], "HOSPITAL")
        self.assertEqual(len(rows), 1)
        self.assertEqual((rows[0]["category"], rows[0]["closedDate"]), ("HOSPITAL", "2024-01-02"))

    def test_reopened_at_same_place_is_not_closed(self):
        rows = fc.closed_rows([src("행복 동물병원", "폐업"), src("행복동물병원", "영업/정상", x=X + 30)], "HOSPITAL")
        self.assertEqual(rows, [])

    def test_open_record_far_away_does_not_cancel(self):
        # 같은 이름의 다른 지점(수 km)은 이 폐업 기록과 무관
        rows = fc.closed_rows([src("행복동물병원", "폐업"), src("행복동물병원", "영업/정상", x=X + 5000)], "HOSPITAL")
        self.assertEqual(len(rows), 1)

    def test_suspended_is_not_closed(self):
        self.assertEqual(fc.closed_rows([src("행복동물병원", "휴업")], "HOSPITAL"), [])
        self.assertEqual(len(fc.closed_rows([src("행복동물병원", "취소/말소/만료/정지/중지")], "HOSPITAL")), 1)


class BuildDbClosedTest(unittest.TestCase):
    def test_is_closed_matches_category_name_and_distance(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "closed.csv")
            with open(path, "w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, fieldnames=fc.OUT_COLUMNS)
                w.writeheader()
                w.writerow({"category": "PHARMACY", "name": "(주)온누리 약국", "lat": "37.5", "lng": "127.0",
                            "status": "폐업", "closedDate": "2025-01-01"})
            closed = b.read_closed(path)
        self.assertTrue(b.is_closed(closed, "PHARMACY", "온누리약국", 37.5005, 127.0))   # 약 55m
        self.assertFalse(b.is_closed(closed, "PHARMACY", "온누리약국", 37.502, 127.0))   # 약 220m
        self.assertFalse(b.is_closed(closed, "HOSPITAL", "온누리약국", 37.5, 127.0))     # 다른 업종
        self.assertFalse(b.is_closed(closed, "PHARMACY", "새온누리약국", 37.5, 127.0))   # 다른 이름


if __name__ == "__main__":
    unittest.main()
