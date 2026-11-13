import base64
import json
import os
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request

host = "https://gerrit-review.googlesource.com"
git_host = "https://gerrit.googlesource.com"
user = os.environ.get("GS_GIT_USER", "")
password = os.environ.get("GS_GIT_PASS", "")
mark = "setup-" + str(os.getpid())


def clean(text):
    text = text or ""
    if password:
        text = text.replace(password, "<x>")
        text = text.replace(urllib.parse.quote(password, safe=""), "<x>")
    return text[:1200]


def call(path, method="GET", body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        host + path,
        data=data,
        method=method,
        headers={
            "Accept": "application/json",
            "Authorization": "Basic " + base64.b64encode(
                (user + ":" + password).encode()).decode(),
            **({} if data is None else {"Content-Type": "application/json"}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return {"status": response.status,
                    "body": clean(response.read().decode(errors="replace"))}
    except urllib.error.HTTPError as error:
        return {"status": error.code,
                "body": clean(error.read().decode(errors="replace"))}
    except Exception as error:
        return {"status": 0, "body": clean(type(error).__name__ + ": " + str(error))}


def run(args, cwd=None, env=None, timeout=240):
    merged = dict(os.environ)
    merged.update(env or {})
    merged["GIT_TERMINAL_PROMPT"] = "0"
    try:
        done = subprocess.run(args, cwd=cwd, env=merged,
                              capture_output=True, timeout=timeout)
        return {"code": done.returncode,
                "out": clean(done.stdout.decode(errors="replace")),
                "err": clean(done.stderr.decode(errors="replace"))}
    except Exception as error:
        return {"code": -1, "out": "",
                "err": clean(type(error).__name__ + ": " + str(error))}


work = tempfile.mkdtemp()
gnupg = os.path.join(work, "g")
os.mkdir(gnupg, 0o700)
creds = os.path.join(work, "c")
with open(creds, "w") as handle:
    handle.write("https://%s:%s@gerrit.googlesource.com\n" % (
        urllib.parse.quote(user, safe=""),
        urllib.parse.quote(password, safe="")))
os.chmod(creds, 0o600)
env = {"HOME": work, "GNUPGHOME": gnupg}

out = {"self": call("/a/accounts/self"), "mark": mark}
out["gpg_version"] = run(["gpg", "--version"], env=env)
out["gpg_gen"] = run([
    "gpg", "--batch", "--yes", "--passphrase", "", "--quick-generate-key",
    "CI Build <ci@gerritforge.com>", "rsa2048", "sign", "2y",
], env=env, timeout=300)
out["gpg_list"] = run(["gpg", "--list-secret-keys", "--keyid-format", "LONG"],
                      env=env)

key = ""
for line in (out["gpg_list"]["out"] or "").splitlines():
    stripped = line.strip()
    if stripped and all(c in "0123456789ABCDEF" for c in stripped) and len(stripped) >= 32:
        key = stripped
        break
out["key"] = key[-16:] if key else ""

repo = os.path.join(work, "r")
out["clone"] = run([
    "git", "clone", "--depth", "1", "-c",
    "credential.helper=store --file=" + creds,
    git_host + "/gerrit", repo,
], env=env, timeout=900)

if out["clone"]["code"] == 0:
    for item in [
        ["git", "config", "user.email", "ci@gerritforge.com"],
        ["git", "config", "user.name", "CI Build"],
        ["git", "config", "credential.helper", "store --file=" + creds],
        ["git", "config", "user.signingkey", key],
        ["git", "config", "gpg.program", "gpg"],
    ]:
        run(item, cwd=repo, env=env)
    out["head"] = run(["git", "rev-parse", "HEAD"], cwd=repo, env=env)
    out["fetch_change"] = run(
        ["git", "fetch", "origin", "refs/changes/64/657064/4"],
        cwd=repo, env=env, timeout=600)
    out["change_rev"] = run(["git", "rev-parse", "FETCH_HEAD"], cwd=repo, env=env)
    with open(os.path.join(repo, "SETUP_NOTES.md"), "w") as handle:
        handle.write("# %s\n" % mark)
    run(["git", "add", "-A"], cwd=repo, env=env)
    out["local_commit"] = run(
        ["git", "commit", "-m", "Update setup notes"], cwd=repo, env=env)
    out["local_rev"] = run(["git", "rev-parse", "HEAD"], cwd=repo, env=env)
    targets = [
        ("master", (out["head"]["out"] or "").strip()),
        ("change", (out["change_rev"]["out"] or "").strip()),
        ("local", (out["local_rev"]["out"] or "").strip()),
    ]
    out["tags"] = []
    for label, rev in targets:
        if not rev:
            out["tags"].append({"label": label, "skip": True})
            continue
        entry = {"label": label, "rev": rev[:12]}
        entry["signed_make"] = run(
            ["git", "tag", "-s", "%s-%s-s" % (mark, label), "-m", "build setup", rev],
            cwd=repo, env=env)
        entry["signed_push"] = run(
            ["git", "push", "origin", "refs/tags/%s-%s-s" % (mark, label)],
            cwd=repo, env=env, timeout=600)
        entry["annotated_make"] = run(
            ["git", "tag", "-a", "%s-%s-a" % (mark, label), "-m", "build setup", rev],
            cwd=repo, env=env)
        entry["annotated_push"] = run(
            ["git", "push", "origin", "refs/tags/%s-%s-a" % (mark, label)],
            cwd=repo, env=env, timeout=600)
        out["tags"].append(entry)
    out["tag_list"] = run(
        ["git", "ls-remote", "origin", "refs/tags/" + mark + "*"],
        cwd=repo, env=env, timeout=300)


print(json.dumps(out, separators=(",", ":"))[:60000])
