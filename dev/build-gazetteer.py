#!/usr/bin/env python3
# Copyright (C) 2026 Verlintas
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the offline China gazetteer shipped in tools/src/main/assets/gazetteer.
# Source: GeoNames dump (CC BY 4.0), https://download.geonames.org/export/dump/CN.zip
#
# Usage:
#   curl -LO https://download.geonames.org/export/dump/CN.zip
#   unzip CN.zip
#   python3 dev/build-gazetteer.py CN.txt
#
# Output: cn-places.tsv (name, pinyin, lat, lng, kind, population) where kind
# is 1=province 2=city 3=county 4=town 5=village. Coordinates stay WGS-84;
# conversion to GCJ-02/BD-09 happens on device.
import io
import re
import sys
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "tools/src/main/assets/gazetteer"
CJK = re.compile(r"^[\u4e00-\u9fff]+$")
# Common traditional variants in place names; used only to prefer simplified
# candidates when GeoNames lists both ("烏蘭浩特" vs "乌兰浩特").
TRADITIONAL = set("縣鎮鄉區東廣寧澳門龍鳳臺灣萬興國內湖雲貴陝甘肅蘇浙皖閩贛鄂湘粵桂瓊蒙藏滿洲鐵錦遼寧")

KIND_PROVINCE = 1
KIND_CITY = 2
KIND_COUNTY = 3
KIND_TOWN = 4
KIND_VILLAGE = 5

ADMIN_KIND = {"ADM1": KIND_PROVINCE, "ADM2": KIND_CITY, "ADM3": KIND_COUNTY, "ADM4": KIND_TOWN}
SEAT_KIND = {"PPLC": KIND_CITY, "PPLA": KIND_CITY, "PPLA2": KIND_CITY,
             "PPLA3": KIND_COUNTY, "PPLA4": KIND_TOWN}
SUFFIX_BY_KIND = {
    KIND_PROVINCE: ("省", "自治区", "市", "特别行政区"),
    KIND_CITY: ("市", "自治州", "地区", "盟"),
    KIND_COUNTY: ("县", "区", "市", "旗"),
    KIND_TOWN: ("镇", "乡", "街道", "苏木"),
}


def pick_name(cols, kind):
    """Pick the most useful Chinese name for a row."""
    candidates = [cols[1]] if CJK.match(cols[1]) else []
    candidates += [part for part in cols[3].split(",") if CJK.match(part)]
    if not candidates:
        return cols[2]
    suffixes = SUFFIX_BY_KIND.get(kind, ())
    simplified = [c for c in candidates if not any(ch in TRADITIONAL for ch in c)]
    pool = simplified or candidates
    for candidate in pool:
        if suffixes and candidate.endswith(suffixes):
            return candidate
    return pool[0]


def build(dump_path):
    rows = {}
    with open(dump_path, encoding="utf-8") as handle:
        for line in handle:
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 15:
                continue
            cls, code = cols[6], cols[7]
            kind = None
            if cls == "A":
                kind = ADMIN_KIND.get(code)
            elif cls == "P":
                kind = SEAT_KIND.get(code)
                if kind is None and code == "PPL":
                    population = int(cols[14] or 0)
                    if population >= 100_000:
                        kind = KIND_CITY
                    elif population >= 20_000:
                        kind = KIND_TOWN
                    elif population >= 1_000:
                        kind = KIND_VILLAGE
            if kind is None:
                continue
            name = pick_name(cols, kind)
            if not name:
                continue
            lat = round(float(cols[4]), 5)
            lng = round(float(cols[5]), 5)
            key = (name, round(lat, 3), round(lng, 3))
            entry = (name, cols[2].replace("\t", " "), f"{lat:.5f}", f"{lng:.5f}",
                     kind, int(cols[14] or 0))
            previous = rows.get(key)
            # An administrative row wins over a populated-place row on collision.
            if previous is None or kind < previous[4]:
                rows[key] = entry
    # Merge same-name entries of the same kind that sit within ~30 km of each
    # other (GeoNames often lists a seat twice); keep the more prominent one.
    ordered = sorted(rows.values(), key=lambda r: (r[4], -r[5], r[0]))
    merged = []
    for entry in ordered:
        duplicate = next(
            (kept for kept in merged
             if kept[0] == entry[0] and kept[4] == entry[4]
             and abs(float(kept[2]) - float(entry[2])) < 0.3
             and abs(float(kept[3]) - float(entry[3])) < 0.3),
            None,
        )
        if duplicate is None:
            merged.append(entry)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    buffer = io.StringIO()
    for entry in merged:
        buffer.write("\t".join(str(part) for part in entry) + "\n")
    payload = buffer.getvalue().encode("utf-8")
    (OUTPUT / "cn-places.tsv").write_bytes(payload)
    (OUTPUT / "ATTRIBUTION.txt").write_text(
        "Place data: GeoNames (https://www.geonames.org/), licensed under CC BY 4.0.\n"
        "Extracted from the CN dump, filtered to provinces, cities, counties,\n"
        "towns, county/town seats and villages with population >= 1000.\n"
        "Coordinates are WGS-84.\n",
        encoding="utf-8",
    )
    print(f"rows: {len(merged)}")
    print(f"places: {len(merged)}, size: {len(payload)/1e6:.2f} MB")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(sys.argv[1])
