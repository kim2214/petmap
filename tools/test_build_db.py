#!/usr/bin/env python3
"""build_db.py 의 순수 함수 테스트: python3 -m unittest tools/test_build_db.py"""
import os
import sys
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


if __name__ == "__main__":
    unittest.main()
