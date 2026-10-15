#!/usr/bin/env python3
"""fetch_restaurants.py 테스트: python3 -m unittest tools/test_fetch_restaurants.py"""
import json
import os
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))
import fetch_restaurants as fr  # noqa: E402

HIT = (37.5, 127.0, "서울특별시 중구 세종대로 110", "서울특별시 중구 태평로1가 31")


def http_error(code):
    return urllib.error.HTTPError("https://example", code, "err", {}, None)


def geocoder(*providers):
    g = fr.Geocoder({"naver.map.clientId": "id", "naver.map.clientSecret": "secret"})
    g.providers = [(f"p{i}", fn) for i, fn in enumerate(providers)]
    return g


@mock.patch.object(fr.time, "sleep", lambda _: None)
class GeocodeTest(unittest.TestCase):
    def test_hit(self):
        self.assertEqual(geocoder(lambda a: HIT).geocode("주소"), HIT)

    def test_no_result_is_none_so_it_is_cached(self):
        self.assertIsNone(geocoder(lambda a: None).geocode("주소"))

    def test_server_error_twice_is_transient(self):
        def fail(a):
            raise http_error(503)
        self.assertIs(geocoder(fail).geocode("주소"), fr.TRANSIENT)

    def test_network_error_twice_is_transient(self):
        def fail(a):
            raise urllib.error.URLError("timeout")
        self.assertIs(geocoder(fail).geocode("주소"), fr.TRANSIENT)

    def test_retry_once_then_hit(self):
        calls = []

        def flaky(a):
            calls.append(a)
            if len(calls) == 1:
                raise http_error(429)
            return HIT
        self.assertEqual(geocoder(flaky).geocode("주소"), HIT)

    def test_fallback_provider_hit_wins_over_transient(self):
        def fail(a):
            raise http_error(500)
        self.assertEqual(geocoder(fail, lambda a: HIT).geocode("주소"), HIT)

    def test_auth_error_stops(self):
        def denied(a):
            raise http_error(401)
        with self.assertRaises(SystemExit):
            geocoder(denied).geocode("주소")


class SaveCacheTest(unittest.TestCase):
    def test_atomic_write_leaves_no_tmp(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "cache.json")
            fr.save_cache({"주소": list(HIT), "없음": None}, path)
            with open(path, encoding="utf-8") as f:
                self.assertEqual(json.load(f), {"주소": list(HIT), "없음": None})
            self.assertEqual(os.listdir(d), ["cache.json"])


if __name__ == "__main__":
    unittest.main()
