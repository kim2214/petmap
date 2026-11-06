#!/usr/bin/env python3
"""build_db.py 의 순수 함수 테스트: python3 -m unittest tools/test_build_db.py"""
import contextlib
import csv
import io
import json
import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_db as b  # noqa: E402


class ParseMaxPetKgTest(unittest.TestCase):
    def test_all_and_variants(self):
        self.assertEqual(b.parse_max_pet_kg("모두 가능"), 999.0)
        self.assertEqual(b.parse_max_pet_kg("모두 가능, 고양이, 포유류 특수동물"), 999.0)
        self.assertEqual(b.parse_max_pet_kg("12kg 이하, 금요일은 모두 가능"), 999.0)

    def test_kg_bounds(self):
        self.assertEqual(b.parse_max_pet_kg("10kg 이하"), 10.0)
        self.assertEqual(b.parse_max_pet_kg("10kg 미만"), 9.99)
        self.assertEqual(b.parse_max_pet_kg("5KG 미만"), 4.99)
        self.assertEqual(b.parse_max_pet_kg("10kg 이내"), 10.0)
        self.assertEqual(b.parse_max_pet_kg("5kg 미만 소형"), 4.99)
        self.assertEqual(b.parse_max_pet_kg("10kg, 체고 40cm 이하"), 10.0)
        # 주말/평일 등 숫자가 여럿이면 보수적으로 최소값
        self.assertEqual(b.parse_max_pet_kg("주말 8kg, 평일 12kg 이하"), 8.0)
        self.assertEqual(b.parse_max_pet_kg("20Kg 이하 소형,중형"), 20.0)

    def test_size_words(self):
        self.assertEqual(b.parse_max_pet_kg("소형"), 10.0)
        self.assertEqual(b.parse_max_pet_kg("소형(안을 수 있는 크기)"), 10.0)
        self.assertEqual(b.parse_max_pet_kg("소형/중형"), 25.0)
        self.assertEqual(b.parse_max_pet_kg("소형/대형"), 999.0)
        self.assertEqual(b.parse_max_pet_kg("대형"), 999.0)

    def test_unknown(self):
        for t in (None, "", "고양이", "8kg이상", "15kg 이상", "6~18kg", "25~30kg", "말, 소, 염소",
                  "개, 고양이", "포유류 특수동물", "해양동물"):
            self.assertIsNone(b.parse_max_pet_kg(t), t)


class NormalizeSidoTest(unittest.TestCase):
    def test_renames_only_leading_token(self):
        self.assertEqual(b.normalize_sido("강원도 강릉시 가작로 275"), "강원특별자치도 강릉시 가작로 275")
        self.assertEqual(b.normalize_sido("전라북도 전주시 덕진구 추탄로 50"), "전북특별자치도 전주시 덕진구 추탄로 50")
        self.assertEqual(b.normalize_sido("광주광역시 북구 대천로 119-2"), "전남광주통합특별시 북구 대천로 119-2")
        self.assertEqual(b.normalize_sido("전라남도 여수시 무선로 179"), "전남광주통합특별시 여수시 무선로 179")

    def test_keeps_current_names_and_blank(self):
        self.assertEqual(b.normalize_sido("강원특별자치도 속초시 중앙로 1"), "강원특별자치도 속초시 중앙로 1")
        self.assertEqual(b.normalize_sido("서울특별시 강남구 테헤란로 1"), "서울특별시 강남구 테헤란로 1")
        self.assertEqual(b.normalize_sido(""), "")
        self.assertEqual(b.normalize_sido(None), "")


class NameSortTest(unittest.TestCase):
    def test_clean_name(self):
        self.assertEqual(b.clean_name("(주) 세녹코리아"), "세녹코리아")
        self.assertEqual(b.clean_name("(주)커피빈코리아 몽촌토성역점"), "커피빈코리아 몽촌토성역점")
        self.assertEqual(b.clean_name("주식회사 더홈카페 1"), "더홈카페 1")
        self.assertEqual(b.clean_name("㈜엠게임"), "엠게임")
        self.assertEqual(b.clean_name("멍더쿠 (주)"), "멍더쿠")
        self.assertEqual(b.clean_name("1004 약국"), "1004 약국")
        self.assertEqual(b.clean_name("(주)"), "(주)")  # 비면 원문 유지

    def test_sort_key_buckets(self):
        keys = sorted(map(b.sort_key, ["1004 약국", "38 COFFEE", "(주) 세녹코리아", "가온카페", "Always on", "生고기", "？물음표"]))
        self.assertEqual(
            # 앞쪽 기호·한자 등 비(한글/영문/숫자) 글자는 떼고 판정한다("？물음표" → 물음표, "生고기" → 고기)
            ["0가온카페", "0고기", "0물음표", "0세녹코리아", "1always on", "21004 약국", "238 coffee"],
            keys,
        )


class HelpersTest(unittest.TestCase):
    def test_yn(self):
        self.assertEqual(b.yn("Y"), 1)
        self.assertEqual(b.yn(" N "), 0)
        self.assertIsNone(b.yn(""))
        self.assertIsNone(b.yn(None))

    def test_normalize_name_and_near(self):
        self.assertEqual(b.normalize_name("(주)커피빈코리아 몽촌토성역점"), "커피빈코리아몽촌토성역점")
        self.assertEqual(b.normalize_name("카페 써니힐(1층)"), "카페써니힐")
        self.assertTrue(b.near(37.5, 127.0, 37.5010, 127.0))
        self.assertFalse(b.near(37.5, 127.0, 37.5020, 127.0))


class SameBuildingDupTest(unittest.TestCase):
    def test_core_name_strips_generic_words(self):
        self.assertEqual(b.core_name("24시 동대문 루시드 동물메디컬센터"), "동대문루시드")
        self.assertEqual(b.core_name("웰니스클리닉(청계천점)"), "웰니스클리닉청계천점")
        self.assertEqual(b.core_name("동물병원"), "")  # 일반어뿐이면 판정 안 함

    def test_same_or_prefix_is_duplicate(self):
        self.assertTrue(b.same_building_dup(b.core_name("그랜드동물병원"), b.core_name("그랜드24시동물병원")))
        self.assertTrue(b.same_building_dup(b.core_name("웰니스클리닉 청계천점"), b.core_name("웰니스클리닉")))
        self.assertTrue(b.same_building_dup(b.core_name("치료해주오 동물병원 중랑묵동점"), b.core_name("치료해 주오")))

    def test_shared_place_name_is_not_duplicate(self):
        # 지명만 겹치는 다른 병원: 앞부분이 아니거나 짧은 쪽이 3글자 미만
        self.assertFalse(b.same_building_dup(b.core_name("아이러브펫동물병원 신도림점"), b.core_name("신도림동물병원")))
        self.assertFalse(b.same_building_dup(b.core_name("대구동물메디컬센터"), b.core_name("대구24시 야간응급연합동물병원")))
        self.assertFalse(b.same_building_dup(b.core_name("꼬마동물병원"), b.core_name("초이스동물병원")))

    def test_address_key(self):
        self.assertEqual(b.address_key("부산광역시 금정구 장전온천천로 48, 부산대지하철역 1층 (장전동)"),
                         b.address_key("부산광역시  금정구 장전온천천로 48"))
        self.assertEqual(b.address_key("강원도 춘천시 중앙로 1 (2층)"), "강원특별자치도 춘천시 중앙로 1")
        self.assertEqual(b.address_key(None), "")

    def test_same_building_dup_pharmacy(self):
        def dup(x, y):
            return b.same_building_dup(b.core_name(x, "PHARMACY"), b.core_name(y, "PHARMACY"), "PHARMACY")
        # 체인 접두어·'동물약국' 표기 차이
        self.assertTrue(dup("휴베이스 안녕약국", "안녕약국"))
        self.assertTrue(dup("메디팜빛고을약국", "빛고을약국"))
        self.assertTrue(dup("셀메드건강약국", "건강약국"))
        self.assertTrue(dup("대저 동물약국 혜유약국", "혜유약국"))
        self.assertTrue(dup("튼튼동물약국", "튼튼약국"))
        # 이름이 다른 이웃 약국은 묶지 않는다
        self.assertFalse(dup("봄약국", "지약국"))
        self.assertFalse(dup("튼튼약국", "세계로약국"))
        self.assertTrue(dup("조은온누리약국", "조은약국"))
        # 2글자 동네 이름으로 시작하는 다른 약국은 묶지 않는다
        self.assertFalse(dup("별내우리약국", "별내약국"))
        self.assertFalse(dup("삼산현대약국", "삼산 동물약국"))
        self.assertFalse(dup("반월당약국", "반월당경북약국"))
        # 병원 규칙은 그대로(뒷부분 일치 불인정)
        self.assertFalse(b.same_building_dup(b.core_name("아이러브펫신도림"), b.core_name("신도림동물병원")))
        self.assertTrue(b.pharmacy_alias_dup("부평 동물약국"))
        self.assertFalse(b.pharmacy_alias_dup("새정성약국"))



class FtsGoldenTest(unittest.TestCase):
    """앱 PlaceFtsTest 와 같은 골든(tools/fts_golden.json)을 공유한다 — 두 구현이 갈리면 둘 중 하나가 실패한다."""

    def test_matches_shared_golden(self):
        path = os.path.join(os.path.dirname(__file__), "fts_golden.json")
        with open(path, encoding="utf-8") as f:
            cases = json.load(f)["cases"]
        self.assertGreater(len(cases), 5)
        for c in cases:
            self.assertEqual(c["index"], b.fts_index(c["input"]), c["note"])



def place(pid, name, lat, lng, category="CAFE", road=""):
    return {"id": pid, "name": name, "category": category, "road": road, "lat": lat, "lng": lng}


class CarryForwardIdsTest(unittest.TestCase):
    """장소 id 안정화: 다시 만든 DB 에서 같은 장소는 이전 id 를 물려받는다."""

    def run_cf(self, current, previous):
        mapping, stats = b.carry_forward_ids(current, previous)
        self.assertEqual(len(set(mapping.values())), len(mapping), "최종 id 가 겹친다")
        return mapping, stats

    def test_same_id_is_kept(self):
        mapping, stats = self.run_cf([place("a", "행복카페", 37.5, 127.0)], [place("a", "행복카페", 37.5, 127.0)])
        self.assertEqual({"a": "a"}, mapping)
        self.assertEqual(1, stats["exact"])

    def test_name_cleanup_or_regeocode_keeps_id(self):
        # 법인 표기 정제(v9)·좌표 표기/지오코딩 변화로 원문 id 가 바뀌어도 같은 곳이다
        prev = [place("(주)행복카페_37.5_127.0", "(주)행복카페", 37.5, 127.0),
                place("멍멍약국_37.6_127.1", "멍멍약국", 37.6, 127.1, "PHARMACY")]
        cur = [place("행복카페_37.5000000_127.0000000", "행복카페", 37.5, 127.0),
               place("멍멍약국_37.6007_127.1", "멍멍약국", 37.6007, 127.1, "PHARMACY")]  # 약 78m 이동
        mapping, stats = self.run_cf(cur, prev)
        self.assertEqual("(주)행복카페_37.5_127.0", mapping["행복카페_37.5000000_127.0000000"])
        self.assertEqual("멍멍약국_37.6_127.1", mapping["멍멍약국_37.6007_127.1"])
        self.assertEqual(2, stats["near"])

    def test_far_move_with_same_address_keeps_id(self):
        prev = [place("p", "행복카페", 37.5, 127.0, road="서울특별시 강남구 테헤란로 1, 2층")]
        cur = [place("c", "행복카페", 37.51, 127.0, road="서울특별시 강남구 테헤란로 1")]  # 약 1.1km
        mapping, stats = self.run_cf(cur, prev)
        self.assertEqual("p", mapping["c"])
        self.assertEqual(1, stats["address"])

    def test_hospital_label_change_in_same_building_keeps_id(self):
        prev = [place("p", "24시 루시드 동물메디컬센터", 37.5, 127.0, "HOSPITAL")]
        cur = [place("c", "루시드 동물메디컬센터", 37.5001, 127.0, "HOSPITAL")]  # 약 11m
        mapping, stats = self.run_cf(cur, prev)
        self.assertEqual("p", mapping["c"])
        self.assertEqual(1, stats["building"])

    def test_building_rule_is_only_for_hospitals_and_pharmacies(self):
        prev = [place("p", "24시 루시드카페", 37.5, 127.0, "CAFE")]
        cur = [place("c", "루시드카페", 37.5001, 127.0, "CAFE")]
        mapping, _ = self.run_cf(cur, prev)
        self.assertEqual("c", mapping["c"])

    def test_previous_id_is_given_to_the_closest_only(self):
        prev = [place("p", "행복카페", 37.5, 127.0)]
        cur = [place("far", "행복카페", 37.5010, 127.0),   # 약 111m
               place("close", "행복카페", 37.5002, 127.0)]  # 약 22m
        mapping, stats = self.run_cf(cur, prev)
        self.assertEqual("p", mapping["close"])
        self.assertEqual("far", mapping["far"])
        self.assertEqual(1, stats["new"])

    def test_different_place_with_same_name_gets_new_id(self):
        prev = [place("p", "행복카페", 37.5, 127.0, road="서울 A로 1")]
        cur = [place("c", "행복카페", 35.1, 129.0, road="부산 B로 2")]
        mapping, stats = self.run_cf(cur, prev)
        self.assertEqual("c", mapping["c"])
        self.assertEqual(1, stats["new"])


class BuildMainIdStabilityTest(unittest.TestCase):
    """CSV → DB 를 두 번 만든다: 두 번째는 상호 표기와 좌표가 바뀌었어도 덮어쓰는 이전 DB 의 id 를 유지한다."""

    HEADER = ["시설명", "카테고리1", "카테고리3", "위도", "경도", "도로명주소", "지번주소", "반려동물 동반 가능정보"]

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.out = os.path.join(self.dir, "out.db")

    def tearDown(self):
        import shutil
        shutil.rmtree(self.dir)

    def write_csv(self, rows):
        path = os.path.join(self.dir, "in.csv")
        with open(path, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(self.HEADER)
            w.writerows(rows)
        return path

    def build(self, rows, *extra):
        argv = ["build_db.py", "--data-date=2026-11-01", *extra, self.write_csv(rows), self.out]
        old, sys.argv = sys.argv, argv
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                b.main()
        finally:
            sys.argv = old
        with sqlite3.connect(self.out) as db:
            ids = dict(db.execute("SELECT name, id FROM places"))
            fts = db.execute("SELECT COUNT(*) FROM places JOIN places_fts ON places.rowid = places_fts.docid").fetchone()[0]
        return ids, fts

    def test_rebuild_keeps_ids_of_same_places(self):
        first, _ = self.build([
            ["(주)행복카페", "", "카페", "37.5", "127.0", "서울특별시 강남구 테헤란로 1", "", "Y"],
            ["멍멍공원", "", "공원", "37.6", "127.1", "서울특별시 노원구 공원로 2", "", "Y"],
        ])
        second, fts = self.build([
            ["행복카페", "", "카페", "37.50001", "127.00001", "서울특별시 강남구 테헤란로 1", "", "Y"],  # 정제·재지오코딩
            ["멍멍공원", "", "공원", "37.6", "127.1", "서울특별시 노원구 공원로 2", "", "Y"],
            ["새로생긴카페", "", "카페", "37.7", "127.2", "서울특별시 도봉구 새길 3", "", "Y"],
        ])
        self.assertEqual(first["행복카페"], second["행복카페"])
        self.assertEqual(first["멍멍공원"], second["멍멍공원"])
        self.assertEqual("새로생긴카페_37.7_127.2", second["새로생긴카페"])
        self.assertEqual(3, fts)  # FTS 색인도 바뀐 id 의 행과 그대로 붙어 있다

    def test_previous_none_disables_carry_forward(self):
        self.build([["(주)행복카페", "", "카페", "37.5", "127.0", "", "", "Y"]])
        second, _ = self.build([["행복카페", "", "카페", "37.50001", "127.0", "", "", "Y"]], "--previous=none")
        self.assertEqual("행복카페_37.50001_127.0", second["행복카페"])

if __name__ == "__main__":
    unittest.main()
