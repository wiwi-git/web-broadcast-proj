#!/usr/bin/env python3
"""duse.dev 첫 화면이 읽는 서비스 상태 파일(status.json)을 만든다.

/etc/duse/services.json에 적힌 서비스마다 docker compose 컨테이너 상태와
헬스 체크 주소를 확인해서 /var/www/duse.dev/status.json에 쓴다.
duse-status.timer가 1분마다 실행한다. 웹에는 상태만 보이고, 켜고 끄는 건 SSH로만 한다.
"""
import json
import os
import subprocess
import tempfile
import urllib.request
from datetime import datetime, timezone

SERVICES_FILE = "/etc/duse/services.json"
OUTPUT_FILE = "/var/www/duse.dev/status.json"


def container_states(service):
    command = ["docker", "compose"]
    for name in service.get("compose_files", []):
        command += ["-f", name]
    for profile in service.get("profiles", []):
        command += ["--profile", profile]
    command += ["ps", "-a", "--format", "json"]
    result = subprocess.run(
        command, cwd=service["compose_dir"], capture_output=True, text=True, timeout=20
    )
    if result.returncode != 0:
        return None
    text = result.stdout.strip()
    if not text:
        return []
    # compose는 버전에 따라 한 줄에 하나씩(NDJSON) 또는 배열 하나로 출력한다
    if text.startswith("["):
        rows = json.loads(text)
    else:
        rows = [json.loads(line) for line in text.splitlines() if line.strip()]
    return [row.get("State", "") for row in rows]


def healthy(url):
    if not url:
        return True
    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            return response.status == 200
    except Exception:
        return False


def status_of(service):
    states = container_states(service)
    if states is None:
        return "unknown", 0, 0
    total = len(states)
    running = sum(1 for state in states if state == "running")
    if total == 0 or running == 0:
        return "off", running, total
    if running < total:
        return "partial", running, total
    if not healthy(service.get("health_url")):
        return "starting", running, total
    return "on", running, total


def main():
    with open(SERVICES_FILE) as file:
        services = json.load(file)
    cards = []
    for service in services:
        status, running, total = status_of(service)
        cards.append({
            "id": service["id"],
            "name": service["name"],
            "description": service.get("description", ""),
            "url": service.get("url", ""),
            "status": status,
            "running": running,
            "total": total,
        })
    payload = {
        "updated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "services": cards,
    }
    # 페이지가 반쯤 쓴 파일을 읽지 않도록 임시 파일에 쓰고 바꿔 끼운다
    directory = os.path.dirname(OUTPUT_FILE)
    handle, temp_path = tempfile.mkstemp(dir=directory, prefix=".status-")
    with os.fdopen(handle, "w") as file:
        json.dump(payload, file, ensure_ascii=False)
    os.chmod(temp_path, 0o644)
    os.replace(temp_path, OUTPUT_FILE)


if __name__ == "__main__":
    main()
