#!/usr/bin/env python3
"""로컬 전환 중 목록 응답을 표본으로 기록한다. 용량·가용성 보장 시험은 아니다."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_root", type=Path)
    args = parser.parse_args()
    root = args.run_root.resolve()
    if not (root / "state.json").is_file():
        raise ValueError("초기화한 실험 폴더가 필요합니다.")
    stop = root / "observer.stop"
    observations = root / "evidence/observations.jsonl"
    counts = Counter()
    # 한 회차의 원자료를 덮어쓰거나 이전 중지 파일을 자동 삭제하지 않는다.
    if stop.exists():
        raise ValueError("이미 중지한 회차입니다.")
    with observations.open("x", buffering=1) as output:
        while not stop.exists():
            started = time.monotonic()
            probe = "observe-" + uuid.uuid4().hex
            phase_path = root / "phase.json"
            record = {"at": datetime.now(timezone.utc).isoformat(), "probe_id": probe,
                      "phase": json.loads(phase_path.read_text())["phase"] if phase_path.exists() else "initial"}
            request = urllib.request.Request("http://127.0.0.1:18000/api/listings",
                                             headers={"X-Request-Id": probe, "Connection": "close"})
            try:
                with urllib.request.urlopen(request, timeout=3) as response:
                    record.update(status=response.status, headers=dict(response.headers),
                                  body=json.loads(response.read()))
                    record["valid_list"] = isinstance(record["body"], dict) and isinstance(record["body"].get("items"), list)
            except urllib.error.HTTPError as error:
                record.update(status=error.code, error=error.read().decode())
            except (OSError, ValueError) as error:
                record.update(status=0, error=f"{type(error).__name__}: {error}")
            record["elapsed_ms"] = round((time.monotonic() - started) * 1000, 3)
            counts[str(record["status"])] += 1
            output.write(json.dumps(record, ensure_ascii=False) + "\n")
            time.sleep(0.5)
    print(json.dumps(dict(counts)))


if __name__ == "__main__":
    main()
