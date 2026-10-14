#!/usr/bin/env python3
"""
지방행정 인허가 데이터(LOCALDATA) "동물병원" → build_db.py 입력 CSV 생성.

한국문화정보원 데이터셋에 빠진 동물병원을 채우기 위한 보조 입력이다. 전국 인허가 목록 중
영업/정상 상태이고 좌표가 있는 곳만 넣는다(약 5,300곳). 기본 데이터셋과 이름·150m 이내로 겹치면
build_db.py 가 기본 데이터셋 쪽을 유지하므로 **마지막 인자**로 넘긴다.

사용법:
    python3 tools/fetch_hospitals.py [--out=<CSV 경로>] [--csv=<로컬 원본 CSV>]
    # 기본 출력: tools/data/hospitals.csv
    # 원본: https://file.localdata.go.kr/file/animal_hospitals/info (data.go.kr 15045050 "바로가기")
    #       브라우저 헤더가 없으면 403 이 나서 UA·Referer 를 붙여 받는다.

좌표: 원본은 Bessel 중부원점 TM(EPSG:5174, 보정계수 미적용 — lon_0 127.0028902777778)이다.
pyproj 없이 역 TM → Bessel 지심좌표 → 7변수 Helmert(EPSG:5174 의 towgs84) → WGS84 로 변환한다.
기존 DB 의 같은 이름 병원과 대조해 중앙값 오차 수 m 수준임을 확인했다(test_fetch_hospitals.py).

동물병원은 반려동물 동반이 전제이므로 "반려동물 동반 가능정보"=Y 로 넣고,
크기·실내외·제한사항은 정보 없음(NULL)으로 둔다.
"""
import csv
import math
import os
import sys
import urllib.request

# LOCALDATA 파일 다운로드: /file/<dataset>/info 페이지, /file/download/<dataset>/info 가 CSV
LOCALDATA = "https://file.localdata.go.kr/file"
DATASET = "animal_hospitals"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"

sys.path.insert(0, os.path.dirname(__file__))
from fetch_restaurants import OUT_COLUMNS  # noqa: E402  (build_db 입력 컬럼 규격 공유)

# --- EPSG:5174 → WGS84 -------------------------------------------------------
BESSEL_A = 6377397.155
BESSEL_F = 1 / 299.1528128
WGS84_A = 6378137.0
WGS84_F = 1 / 298.257223563
LAT0 = math.radians(38.0)
LON0 = math.radians(127.0028902777778)
X0, Y0, K0 = 200000.0, 500000.0, 1.0
# towgs84 (position vector): dx, dy, dz[m], rx, ry, rz[초], ds[ppm]
HELMERT = (-115.80, 474.99, 674.11, 1.16, -2.31, -1.63, 6.43)


def _meridian_arc(phi, a, e2):
    e4, e6 = e2 * e2, e2 * e2 * e2
    return a * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
                - (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * math.sin(2 * phi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * math.sin(4 * phi)
                - (35 * e6 / 3072) * math.sin(6 * phi))


def _tm_inverse(easting, northing):
    """Bessel 타원체 횡메르카토르 역변환 (Snyder) → (lat, lon) 라디안."""
    a, f = BESSEL_A, BESSEL_F
    e2 = f * (2 - f)
    ep2 = e2 / (1 - e2)
    m = _meridian_arc(LAT0, a, e2) + (northing - Y0) / K0
    mu = m / (a * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 ** 3 / 256))
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    phi1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu)
            + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
            + (151 * e1 ** 3 / 96) * math.sin(6 * mu)
            + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    s, c, t = math.sin(phi1), math.cos(phi1), math.tan(phi1)
    c1 = ep2 * c * c
    t1 = t * t
    n1 = a / math.sqrt(1 - e2 * s * s)
    r1 = a * (1 - e2) / (1 - e2 * s * s) ** 1.5
    d = (easting - X0) / (n1 * K0)
    lat = phi1 - (n1 * t / r1) * (
        d * d / 2
        - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d ** 4 / 24
        + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d ** 6 / 720)
    lon = LON0 + (d - (1 + 2 * t1 + c1) * d ** 3 / 6
                  + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d ** 5 / 120) / c
    return lat, lon


def _geodetic_to_ecef(lat, lon, a, f):
    e2 = f * (2 - f)
    n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
    return (n * math.cos(lat) * math.cos(lon), n * math.cos(lat) * math.sin(lon), n * (1 - e2) * math.sin(lat))


def _ecef_to_geodetic(x, y, z, a, f):
    e2 = f * (2 - f)
    lon = math.atan2(y, x)
    p = math.hypot(x, y)
    lat = math.atan2(z, p * (1 - e2))
    for _ in range(6):
        n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
        lat = math.atan2(z + e2 * n * math.sin(lat), p)
    return lat, lon


def tm5174_to_wgs84(easting, northing):
    """EPSG:5174 (x=동향, y=북향) → (위도, 경도) WGS84 도."""
    lat, lon = _tm_inverse(easting, northing)
    x, y, z = _geodetic_to_ecef(lat, lon, BESSEL_A, BESSEL_F)
    dx, dy, dz, rx, ry, rz, ds = HELMERT
    sec = math.pi / (180 * 3600)
    rx, ry, rz, k = rx * sec, ry * sec, rz * sec, 1 + ds * 1e-6
    # position vector 규약 (proj towgs84 와 같음)
    x2 = dx + k * (x - rz * y + ry * z)
    y2 = dy + k * (rz * x + y - rx * z)
    z2 = dz + k * (-ry * x + rx * y + z)
    lat, lon = _ecef_to_geodetic(x2, y2, z2, WGS84_A, WGS84_F)
    return math.degrees(lat), math.degrees(lon)


# --- 변환 ---------------------------------------------------------------------
def read_source(path):
    for enc in ("utf-8-sig", "cp949"):
        try:
            with open(path, encoding=enc, newline="") as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    raise SystemExit(f"오류: {path} 를 UTF-8/CP949 로 읽을 수 없음")


def to_rows(source_rows):
    """영업/정상 + 좌표 있는 행 → build_db 입력 행. (제외 건수도 함께 돌려준다)"""
    out, skipped_status, skipped_coord = [], 0, 0
    for r in source_rows:
        if (r.get("영업상태명") or "").strip() != "영업/정상":
            skipped_status += 1
            continue
        try:
            x = float((r.get("좌표정보(X)") or "").strip())
            y = float((r.get("좌표정보(Y)") or "").strip())
        except ValueError:
            skipped_coord += 1
            continue
        lat, lng = tm5174_to_wgs84(x, y)
        # 대한민국 범위 밖이면(원본 좌표 오류) 버린다
        if not (33.0 <= lat <= 38.7 and 124.5 <= lng <= 132.0):
            skipped_coord += 1
            continue
        road = (r.get("도로명주소") or "").strip()
        jibun = (r.get("지번주소") or "").strip()
        row = {c: "" for c in OUT_COLUMNS}
        row.update({
            "시설명": (r.get("사업장명") or "").strip(),
            "카테고리1": "반려의료",
            "카테고리2": "반려동물 서비스",
            "카테고리3": "동물병원",
            "위도": f"{lat:.7f}",
            "경도": f"{lng:.7f}",
            # 도로명주소가 없는 인허가 건은 지번주소로 채운다(지역 브라우징이 주소 첫 토큰을 쓴다)
            "도로명주소": road or jibun,
            "지번주소": jibun,
            "전화번호": (r.get("전화번호") or "").strip(),
            "반려동물 동반 가능정보": "Y",
            "최종작성일": (r.get("최종수정시점") or "")[:10],
        })
        if row["시설명"] and row["도로명주소"]:
            out.append(row)
        else:
            skipped_coord += 1
    return out, skipped_status, skipped_coord


def download(path, dataset=DATASET):
    """LOCALDATA 데이터셋 전체 CSV(cp949)를 받는다. 브라우저 UA·Referer 가 없으면 403."""
    req = urllib.request.Request(f"{LOCALDATA}/download/{dataset}/info", headers={
        "User-Agent": UA, "Referer": f"{LOCALDATA}/{dataset}/info", "Accept-Language": "ko-KR,ko",
    })
    with urllib.request.urlopen(req, timeout=120) as resp, open(path, "wb") as f:
        f.write(resp.read())


def main():
    args = dict(a.split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    out_path = args.get("--out", "tools/data/hospitals.csv")
    src = args.get("--csv")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    if not src:
        src = os.path.join(os.path.dirname(out_path) or ".", f"{DATASET}_source.csv")
        print(f"다운로드: {LOCALDATA}/download/{DATASET}/info")
        download(src)
    rows, skipped_status, skipped_coord = to_rows(read_source(src))
    with open(out_path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=OUT_COLUMNS)
        w.writeheader()
        w.writerows(rows)
    print(f"✓ {out_path}: 동물병원 {len(rows)}곳 (영업 외 제외 {skipped_status}, 좌표·주소 없음 제외 {skipped_coord})")


if __name__ == "__main__":
    main()
