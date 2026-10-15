#!/usr/bin/env python3
"""
식약처(식품안전나라) "반려동물 동반 가능 업소 현황" → 지오코딩 → build_db.py 입력 CSV 생성.

2026-03-01 시행된 반려동물 동반출입 음식점 제도에 등록한 업소 목록(일반음식점·휴게음식점·제과점,
약 2,700곳)이다. 원본에는 업소명·업종·주소만 있고 좌표가 없어 지오코딩이 필요하다.

사용법:
    python3 tools/fetch_restaurants.py [--out=<CSV 경로>] [--cache=<JSON 경로>] [--xlsx=<로컬 xlsx>]
    # 기본 출력: tools/data/restaurants.csv (build_db.py 의 두 번째 이후 인자로 넘긴다)
    # 기본 캐시: tools/data/geocode_cache.json (주소 → 좌표. 재실행 시 이미 푼 주소는 다시 묻지 않는다)

지오코딩 키 (하나 이상 필요, local.properties 또는 환경변수):
    naver.map.clientId + naver.map.clientSecret   → 네이버 클라우드 Maps Geocoding
        (콘솔 > Application > 해당 앱 > "Geocoding" 을 켜야 한다. Client Secret 은 같은 화면에 있다)
    kakao.restApiKey                              → 카카오 로컬 주소 검색
    환경변수 이름: NAVER_CLIENT_ID / NAVER_CLIENT_SECRET / KAKAO_REST_API_KEY
    두 종류가 모두 있으면 네이버를 먼저 쓰고 실패한 주소만 카카오로 재시도한다.

출력 CSV 컬럼은 한국문화정보원 CSV 와 같은 이름을 쓴다(시설명·카테고리3·도로명주소·위도·경도·
반려동물 동반 가능정보 등). 실내/실외·크기·제한사항 컬럼은 비워 두면 build_db.py 가 NULL(정보 없음)로 넣는다.
카테고리3: 일반음식점 → 식당, 휴게음식점 → 카페, 제과점영업 → 제과점(→ CAFE).
"""
import csv
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

LIST_PAGE = "https://www.foodsafetykorea.go.kr/portal/petKorea.do"
EXCEL_URL = "https://www.foodsafetykorea.go.kr/portal/petKorea/downloadExcel.do"
UA = "Mozilla/5.0 (PetMap build tool)"

CATEGORY3 = {"일반음식점": "식당", "휴게음식점": "카페", "제과점영업": "제과점"}

OUT_COLUMNS = [
    "시설명", "카테고리1", "카테고리2", "카테고리3", "위도", "경도", "도로명주소", "지번주소",
    "전화번호", "홈페이지", "휴무일", "운영시간", "주차 가능여부", "입장(이용료)가격 정보",
    "반려동물 동반 가능정보", "입장 가능 동물 크기", "반려동물 제한사항",
    "장소(실내) 여부", "장소(실외)여부", "기본 정보_장소설명", "애견 동반 추가 요금", "최종작성일",
]


def read_props(path="local.properties"):
    props = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def download_excel(path):
    req = urllib.request.Request(EXCEL_URL, method="POST", headers={"User-Agent": UA, "Referer": LIST_PAGE})
    with urllib.request.urlopen(req, timeout=60) as res, open(path, "wb") as fh:
        fh.write(res.read())


def read_excel_rows(path):
    """openpyxl 없이 xlsx(sheet1) 를 읽는다: 연번·업소명·업종·지역·업소주소."""
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        ss = z.read("xl/sharedStrings.xml").decode("utf-8", "replace")
        shared = [unescape(t) for t in re.findall(r"<si>(.*?)</si>", ss, re.S)]
    sheet = z.read("xl/worksheets/sheet1.xml").decode("utf-8", "replace")
    rows = []
    for row in re.findall(r"<row[^>]*>(.*?)</row>", sheet, re.S):
        cells = []
        for m in re.finditer(r"<c([^>]*?)(?:/>|>(.*?)</c>)", row, re.S):
            attrs, inner = m.group(1), m.group(2) or ""
            v = re.search(r"<v>([^<]*)</v>", inner)
            t = re.search(r"<t[^>]*>([^<]*)</t>", inner)
            if 't="s"' in attrs and v:
                cells.append(shared[int(v.group(1))])
            elif t:
                cells.append(unescape(t.group(1)))
            else:
                cells.append(v.group(1) if v else "")
        rows.append(cells)
    header = rows[0]
    idx = {h: i for i, h in enumerate(header)}
    for need in ("업소명", "업종", "업소주소"):
        if need not in idx:
            raise SystemExit(f"오류: 엑셀 헤더에 '{need}' 없음: {header}")
    out = []
    for r in rows[1:]:
        if len(r) <= max(idx.values()):
            continue
        out.append({"name": r[idx["업소명"]].strip(), "type": r[idx["업종"]].strip(),
                    "region": r[idx.get("지역", 0)].strip(), "addr": r[idx["업소주소"]].strip()})
    return out


def unescape(s):
    # sharedStrings 의 <si> 안에는 <t> 가 여러 개(리치 텍스트)일 수 있다
    parts = re.findall(r"<t[^>]*>([^<]*)</t>", s)
    s = "".join(parts) if parts else s
    return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&apos;", "'")


def clean_address(addr):
    """지오코더에 넣을 주소: 괄호 안 상세(층·호·동명)를 떼고 도로명+건물번호까지만."""
    a = re.sub(r"\(.*?\)", " ", addr)
    a = re.sub(r"\s+", " ", a).strip(" ,")
    return a


# Geocoder.geocode 가 일시 오류(429/5xx/네트워크)로 결론을 못 냈을 때 돌려주는 표식 — 캐시하지 않는다
TRANSIENT = object()


class Geocoder:
    def __init__(self, props):
        env = os.environ
        self.naver_id = env.get("NAVER_CLIENT_ID") or props.get("naver.map.clientId")
        self.naver_secret = env.get("NAVER_CLIENT_SECRET") or props.get("naver.map.clientSecret")
        self.kakao_key = env.get("KAKAO_REST_API_KEY") or props.get("kakao.restApiKey")
        self.providers = []
        if self.naver_id and self.naver_secret:
            self.providers.append(("naver", self.naver))
        if self.kakao_key:
            self.providers.append(("kakao", self.kakao))
        if not self.providers:
            raise SystemExit(
                "오류: 지오코딩 키가 없음. local.properties 에 naver.map.clientSecret(+clientId) 또는 "
                "kakao.restApiKey 를 넣거나 NAVER_CLIENT_ID/NAVER_CLIENT_SECRET 또는 KAKAO_REST_API_KEY 환경변수를 설정할 것"
            )

    def _get(self, url, headers):
        req = urllib.request.Request(url, headers={"User-Agent": UA, **headers})
        with urllib.request.urlopen(req, timeout=20) as res:
            return json.load(res)

    def naver(self, addr):
        url = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode?" + urllib.parse.urlencode({"query": addr})
        data = self._get(url, {"x-ncp-apigw-api-key-id": self.naver_id, "x-ncp-apigw-api-key": self.naver_secret,
                               "Accept": "application/json"})
        for a in data.get("addresses") or []:
            if a.get("x") and a.get("y"):
                return float(a["y"]), float(a["x"]), a.get("roadAddress") or "", a.get("jibunAddress") or ""
        return None

    def kakao(self, addr):
        url = "https://dapi.kakao.com/v2/local/search/address.json?" + urllib.parse.urlencode({"query": addr})
        data = self._get(url, {"Authorization": f"KakaoAK {self.kakao_key}"})
        for d in data.get("documents") or []:
            if d.get("x") and d.get("y"):
                road = (d.get("road_address") or {}).get("address_name") or ""
                jibun = (d.get("address") or {}).get("address_name") or ""
                return float(d["y"]), float(d["x"]), road, jibun
        return None

    def geocode(self, addr):
        """(lat, lng, roadAddress, lotAddress), 결과 없음이면 None, 일시 오류로 판단 못 했으면 TRANSIENT.

        429/5xx·네트워크 오류는 잠시 쉬고 한 번 재시도한다. 그래도 실패한 제공자가 있고 아무 데서도 좌표를
        못 얻었으면 TRANSIENT — 호출 측은 캐시에 남기지 않아 다음 실행에서 다시 묻는다(None 은 "없음"으로 영구 캐시).
        """
        transient = False
        for name, fn in self.providers:
            for attempt in range(2):
                try:
                    hit = fn(addr)
                    if hit:
                        return hit
                    break
                except urllib.error.HTTPError as e:
                    if e.code in (401, 403):
                        raise SystemExit(f"오류: {name} 지오코딩 인증 실패 (HTTP {e.code}) — 키/서비스 활성화 확인")
                    if e.code == 429 or e.code >= 500:
                        time.sleep(2.0 * (attempt + 1))
                        if attempt == 1:
                            transient = True
                        continue
                    break
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(1.0)
                    if attempt == 1:
                        transient = True
        return TRANSIENT if transient else None


def save_cache(cache, path):
    """임시 파일에 쓴 뒤 교체 — 쓰는 도중 중단돼도 기존 캐시(20분짜리 지오코딩 결과)가 깨지지 않는다."""
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=0)
    os.replace(tmp, path)


def main():
    args = sys.argv[1:]
    opts = {a.split("=", 1)[0]: a.split("=", 1)[1] for a in args if a.startswith("--") and "=" in a}
    out_path = opts.get("--out", "tools/data/restaurants.csv")
    cache_path = opts.get("--cache", "tools/data/geocode_cache.json")
    xlsx_path = opts.get("--xlsx", "tools/data/restaurants_source.xlsx")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    os.makedirs(os.path.dirname(cache_path) or ".", exist_ok=True)

    if "--xlsx" not in opts or not os.path.exists(xlsx_path):
        print(f"식품안전나라 업소 현황 다운로드 → {xlsx_path}")
        download_excel(xlsx_path)
    rows = read_excel_rows(xlsx_path)
    print(f"  업소 {len(rows)}건 (업종: " + ", ".join(
        f"{t} {n}" for t, n in sorted(__import__('collections').Counter(r['type'] for r in rows).items())) + ")")

    cache = json.load(open(cache_path, encoding="utf-8")) if os.path.exists(cache_path) else {}
    geocoder = None
    pending = [r for r in rows if clean_address(r["addr"]) not in cache]
    if pending:
        geocoder = Geocoder(read_props())
        print(f"  지오코딩 {len(pending)}건 ({'+'.join(n for n, _ in geocoder.providers)}) …")
    today = time.strftime("%Y-%m-%d")
    transient = 0
    for i, r in enumerate(pending, 1):
        q = clean_address(r["addr"])
        hit = geocoder.geocode(q)
        if hit is TRANSIENT:
            transient += 1
        else:
            cache[q] = list(hit) if hit else None
        if i % 100 == 0 or i == len(pending):
            save_cache(cache, cache_path)
            print(f"    {i}/{len(pending)}")
        time.sleep(0.05)
    if transient:
        print(f"  ! 일시 오류 {transient}건은 캐시하지 않음 — 다시 실행하면 그 주소만 재시도한다")

    written = missed = 0
    with open(out_path, "w", encoding="utf-8-sig", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=OUT_COLUMNS)
        w.writeheader()
        for r in rows:
            hit = cache.get(clean_address(r["addr"]))
            if not hit:
                missed += 1
                continue
            lat, lng, road, jibun = hit
            w.writerow({
                "시설명": r["name"], "카테고리1": "반려동물 동반출입 음식점", "카테고리2": r["type"],
                "카테고리3": CATEGORY3.get(r["type"], "식당"),
                "위도": f"{lat:.7f}", "경도": f"{lng:.7f}",
                "도로명주소": road or clean_address(r["addr"]), "지번주소": jibun,
                "반려동물 동반 가능정보": "Y", "최종작성일": today,
            })
            written += 1
    print(f"✓ {out_path}: {written}건 기록, 지오코딩 실패 {missed}건 (캐시 {cache_path})")


if __name__ == "__main__":
    main()
