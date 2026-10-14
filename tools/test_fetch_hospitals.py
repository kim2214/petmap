#!/usr/bin/env python3
"""fetch_hospitals.py 테스트: python3 -m unittest tools/test_fetch_hospitals.py"""
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import fetch_hospitals as f  # noqa: E402


def meters(lat1, lng1, lat2, lng2):
    return math.hypot((lat1 - lat2) * 111_000, (lng1 - lng2) * 111_000 * math.cos(math.radians(lat1)))


class TmConversionTest(unittest.TestCase):
    # 인허가 원본 좌표(EPSG:5174) ↔ 한국문화정보원 데이터셋의 같은 병원 위경도 (서로 독립된 출처)
    CASES = [
        ("광화문동물병원", 197383.197504794, 452972.118568389, 37.57904361, 126.971158),
        ("폴라동물병원", 201451.464239337, 453137.690825347, 37.58053865, 127.017216),
        ("라이프동물병원(제주)", 151223.623332152, -1403.5303111279, 33.48264785, 126.476003),
        ("희망종합동물병원(부산)", 385046.215020992, 179539.723961796, 35.09797296, 129.030146),
    ]

    def test_matches_independent_coordinates_within_5m(self):
        for name, x, y, lat, lng in self.CASES:
            got_lat, got_lng = f.tm5174_to_wgs84(x, y)
            self.assertLess(meters(got_lat, got_lng, lat, lng), 5.0, name)


class ToRowsTest(unittest.TestCase):
    def src(self, **kw):
        row = {"영업상태명": "영업/정상", "사업장명": "테스트동물병원", "좌표정보(X)": "197383.1975 ",
               "좌표정보(Y)": "452972.1186", "도로명주소": "서울특별시 종로구 자하문로 35-3",
               "지번주소": "서울특별시 종로구 통인동 1", "전화번호": "02-000-0000", "최종수정시점": "2025-01-02 03:04:05"}
        row.update(kw)
        return row

    def test_keeps_open_hospitals_as_pet_allowed_hospital_rows(self):
        rows, skipped_status, skipped_coord = f.to_rows([self.src()])
        self.assertEqual((len(rows), skipped_status, skipped_coord), (1, 0, 0))
        r = rows[0]
        self.assertEqual(r["카테고리3"], "동물병원")
        self.assertEqual(r["반려동물 동반 가능정보"], "Y")
        self.assertEqual(r["최종작성일"], "2025-01-02")
        self.assertAlmostEqual(float(r["위도"]), 37.579, places=2)

    def test_skips_closed_and_missing_coordinates(self):
        rows, skipped_status, skipped_coord = f.to_rows([
            self.src(영업상태명="폐업"),
            self.src(**{"좌표정보(X)": ""}),
            self.src(**{"좌표정보(X)": "-900000", "좌표정보(Y)": "0"}),  # 한국 범위 밖 좌표
        ])
        self.assertEqual((len(rows), skipped_status, skipped_coord), (0, 1, 2))

    def test_falls_back_to_lot_address(self):
        rows, _, _ = f.to_rows([self.src(도로명주소="")])
        self.assertEqual(rows[0]["도로명주소"], "서울특별시 종로구 통인동 1")


if __name__ == "__main__":
    unittest.main()
