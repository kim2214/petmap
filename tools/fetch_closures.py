#!/usr/bin/env python3
"""
지방행정 인허가 데이터(LOCALDATA)의 폐업 기록 → build_db.py 의 --closed 입력 CSV 생성.

한국문화정보원 데이터셋은 갱신이 느려 이미 문을 닫은 동물병원·동물약국이 남아 있다(2026-10 기준 약 1,400곳).
인허가 데이터에서 **폐업·취소 기록만 있고 같은 이름의 영업/정상 기록은 근처(100m)에 없는** 사업장을 뽑아,
build_db.py 가 같은 업종·같은 이름·100m 안의 장소를 빼게 한다. 같은 자리에서 이름을 바꿔 다시 연 곳은
새 이름이 영업/정상으로 따로 들어오므로 옛 이름을 빼는 것이 맞다. 휴업은 일시적이라 제외하지 않는다.

사용법:
    python3 tools/fetch_closures.py [--out=<CSV 경로>] [--hospitals=<원본 CSV>] [--pharmacies=<원본 CSV>]
    # 기본 출력: tools/data/closed.csv → build_db.py --closed=tools/data/closed.csv
    # 원본을 주지 않으면 tools/data/<dataset>_source.csv 로 내려받는다(fetch_hospitals.py 와 같은 방식).
"""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import build_db  # noqa: E402
import fetch_hospitals as lo  # noqa: E402  (LOCALDATA 다운로드·좌표 변환 공용)

# LOCALDATA 데이터셋 → 앱 카테고리 (build_db.category_name 결과와 같은 값)
DATASETS = {"animal_hospitals": "HOSPITAL", "animal_pharmacies": "PHARMACY"}
CLOSED_STATUSES = ("폐업", "취소/말소/만료/정지/중지")
OPEN_STATUS = "영업/정상"
NEAR_METERS = 100.0
OUT_COLUMNS = ["category", "name", "lat", "lng", "status", "closedDate"]


def _coords(r):
    try:
        x = float((r.get("좌표정보(X)") or "").strip())
        y = float((r.get("좌표정보(Y)") or "").strip())
    except ValueError:
        return None
    lat, lng = lo.tm5174_to_wgs84(x, y)
    if not (33.0 <= lat <= 38.7 and 124.5 <= lng <= 132.0):
        return None
    return lat, lng


def closed_rows(source_rows, category):
    """폐업 기록 중 같은 이름의 영업/정상 기록이 100m 안에 없는 것만."""
    open_by_name = {}
    closed = []
    for r in source_rows:
        status = (r.get("영업상태명") or "").strip()
        name = (r.get("사업장명") or "").strip()
        pos = _coords(r)
        if not name or pos is None:
            continue
        key = build_db.normalize_name(build_db.clean_name(name))
        if not key:
            continue
        if status == OPEN_STATUS:
            open_by_name.setdefault(key, []).append(pos)
        elif status in CLOSED_STATUSES:
            closed.append((key, name, pos, status, (r.get("폐업일자") or "").strip()))
    out = []
    for key, name, (lat, lng), status, closed_date in closed:
        if any(build_db.near(lat, lng, la, ln, NEAR_METERS) for la, ln in open_by_name.get(key, ())):
            continue  # 같은 자리에서 다시 영업 중 (재개업·재허가)
        out.append({
            "category": category, "name": name, "lat": f"{lat:.7f}", "lng": f"{lng:.7f}",
            "status": status, "closedDate": closed_date,
        })
    return out


def main():
    args = dict(a.split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    out_path = args.get("--out", "tools/data/closed.csv")
    data_dir = os.path.dirname(out_path) or "."
    os.makedirs(data_dir, exist_ok=True)
    rows = []
    for dataset, category in DATASETS.items():
        src = args.get("--" + dataset.split("_", 1)[1])  # --hospitals / --pharmacies
        if not src:
            src = os.path.join(data_dir, f"{dataset}_source.csv")
            print(f"다운로드: {dataset}")
            lo.download(src, dataset)
        found = closed_rows(lo.read_source(src), category)
        print(f"  {dataset}: 폐업(근처 영업 기록 없음) {len(found)}건")
        rows.extend(found)
    with open(out_path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=OUT_COLUMNS)
        w.writeheader()
        w.writerows(rows)
    print(f"✓ {out_path}: {len(rows)}건")


if __name__ == "__main__":
    main()
