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


if __name__ == "__main__":
    unittest.main()
