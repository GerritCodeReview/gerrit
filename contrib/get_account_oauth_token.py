#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Retrieve the caller's OAuth access token from Gerrit for Git-over-HTTP.

This is a reference utility, and it is entirely
provider-independent: it talks only to core Gerrit's

    GET /accounts/self/oauthtoken   (GetOAuthToken)

which returns the OAuth access token Gerrit already obtained for the user at
web-UI login and caches in OAuthTokenCache. It does not run its own OAuth
handshake with the identity provider; it reads back the token Gerrit already
holds.

Authentication uses the Gerrit *browser session cookie* (GerritAccount), not the
access token. The cookie outlives the access token, so retrieval keeps working
after the token has expired: GetOAuthToken refreshes an expired token on read
(RFC 6749 section 6) from its refresh token before serving it.

Get the cookie value once from a signed-in browser (DevTools -> Application/
Storage -> Cookies -> GerritAccount), then:

    # print a fresh access token
    get_account_oauth_token.py token \\
        --host http://localhost:8080 --cookie GerritAccount=<value>

    # store the cookie for git (one-off; validates it against /oauthtoken)
    get_account_oauth_token.py init \\
        --host http://localhost:8080 --gerrit-username <name> \\
        --cookie GerritAccount=<value>
    git config --global credential.http://localhost:8080.helper ""
    git config --global --add \\
        credential.http://localhost:8080.helper \\
        /abs/path/get_account_oauth_token.py
    git config --global credential.http://localhost:8080.username <name>

    get_account_oauth_token.py forget --host http://localhost:8080

With that config, git supplies the password itself on every clone/fetch/push --
git runs the helper, which returns the token; you never type a password. (Git
invokes the helper with the credential operation; retrieval is the default, so
the config is only the helper path, no `get`.)

The stored cookie is written to a 0600 file under ~/.config, never passed on the
command line during git operations (so it does not appear in `ps`).
"""

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

STORE_NAME = "get_account_oauth_token"
XSSI_PREFIX = ")]}'"


def log(msg):
    print(msg, file=sys.stderr)


def die(msg, code=1):
    log("error: " + msg)
    sys.exit(code)


# --- 0600 per-host store, like git's own credential-store --------------------


def _store_dir():
    base = os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config"))
    d = os.path.join(base, STORE_NAME)
    os.makedirs(d, mode=0o700, exist_ok=True)
    os.chmod(d, 0o700)
    return d


def _record_path(host):
    return os.path.join(_store_dir(), hashlib.sha256(host.encode()).hexdigest())


def store_load(host):
    path = _record_path(host)
    if os.path.exists(path):
        with open(path) as f:
            return json.load(f)
    return None


def store_save(host, data):
    path = _record_path(host)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write(json.dumps(data))
    os.chmod(path, 0o600)


def store_delete(host):
    path = _record_path(host)
    if os.path.exists(path):
        os.remove(path)


# --- Gerrit REST -------------------------------------------------------------


def _normalize_cookie(cookie):
    # Accept either "GerritAccount=abc" or the bare value "abc".
    cookie = cookie.strip()
    return cookie if "=" in cookie else "GerritAccount=" + cookie


def fetch_oauth_token(host, cookie):
    """Return (access_token, username) from GET /accounts/self/oauthtoken."""
    url = host.rstrip("/") + "/accounts/self/oauthtoken"
    req = urllib.request.Request(url, headers={"Cookie": _normalize_cookie(cookie)})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            die("no OAuth token for this account (404): sign in via the browser "
                "with an OAuth provider first")
        if e.code in (401, 403):
            die("not authenticated (%d): the GerritAccount cookie is missing, "
                "expired, or for another account" % e.code)
        die("GET /accounts/self/oauthtoken failed: HTTP %d" % e.code)
    if raw.startswith(XSSI_PREFIX):
        raw = raw[len(XSSI_PREFIX):]
    info = json.loads(raw)
    token = info.get("access_token")
    if not token:
        die("response contained no access_token")
    return token, info.get("username", "")


# --- git credential protocol -------------------------------------------------


def read_git_attrs():
    attrs = {}
    for line in sys.stdin:
        line = line.rstrip("\n")
        if not line:
            break
        k, _, v = line.partition("=")
        attrs[k] = v
    return attrs


def host_from_attrs(attrs):
    return "%s://%s" % (attrs.get("protocol", "https"), attrs.get("host", ""))


def cmd_get():
    attrs = read_git_attrs()
    host = host_from_attrs(attrs)
    rec = store_load(host)
    if not rec:
        die("no stored cookie for %s; run: %s init --host %s --cookie ..."
            % (host, os.path.basename(sys.argv[0]), host))
    token, endpoint_username = fetch_oauth_token(host, rec["cookie"])
    username = attrs.get("username") or rec.get("gerrit_username") or endpoint_username
    if not username:
        die("no Gerrit username for %s: set credential.<url>.username" % host)
    sys.stdout.write("username=%s\n" % username)
    sys.stdout.write("password=%s\n" % token)


def cmd_store_or_erase():
    # init/forget manage the cookie; ignore git's store/erase payload.
    read_git_attrs()


def cmd_token(args):
    token, username = fetch_oauth_token(args.host, args.cookie)
    if args.with_username:
        print("%s\t%s" % (username, token))
    else:
        print(token)


def cmd_init(args):
    # Validate the cookie now so init fails fast on a bad/expired cookie.
    _, endpoint_username = fetch_oauth_token(args.host, args.cookie)
    store_save(args.host, {
        "cookie": _normalize_cookie(args.cookie),
        "gerrit_username": args.gerrit_username or endpoint_username or "",
    })
    log("Stored session cookie for %s." % args.host)


def cmd_forget(args):
    store_delete(args.host)
    log("Forgot %s." % args.host)


def main():
    argv = sys.argv[1:]
    if argv and argv[0] in ("-h", "--help"):
        print(__doc__)
        return

    # Explicit, human-facing subcommands.
    if argv and argv[0] in ("init", "token", "forget"):
        p = argparse.ArgumentParser(
            prog=os.path.basename(sys.argv[0]),
            description=__doc__,
            formatter_class=argparse.RawDescriptionHelpFormatter,
        )
        sub = p.add_subparsers(dest="command")

        pi = sub.add_parser("init")
        pi.add_argument("--host", required=True)
        pi.add_argument("--cookie", required=True,
                        help="GerritAccount cookie ('GerritAccount=<v>' or bare <v>)")
        pi.add_argument("--gerrit-username", default="")

        pt = sub.add_parser("token")
        pt.add_argument("--host", required=True)
        pt.add_argument("--cookie", required=True)
        pt.add_argument("--with-username", action="store_true")

        pf = sub.add_parser("forget")
        pf.add_argument("--host", required=True)

        args = p.parse_args(argv)
        if args.command == "init":
            cmd_init(args)
        elif args.command == "token":
            cmd_token(args)
        elif args.command == "forget":
            cmd_forget(args)
        return

    # Default: Git credential-helper mode. Git passes get|store|erase as the sole
    # argument; a bare invocation (no argument) defaults to `get`. Extra tokens
    # are ignored, so the credential protocol never breaks on argument shape.
    op = argv[0] if argv else "get"
    if op in ("store", "erase"):
        cmd_store_or_erase()
    else:
        cmd_get()


if __name__ == "__main__":
    main()
