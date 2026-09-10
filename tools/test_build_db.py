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
