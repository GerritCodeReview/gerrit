#!/usr/bin/env python3

from __future__ import annotations

import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import TypedDict

# Hetzner Stage
ISSUER_URL = "https://meroton-yka6f8.eu1.zitadel.cloud"
CLIENT_ID = "388823577480246894"


SCOPE = " ".join(  # noqa: FLY002
    [
        "openid",
        "offline_access",
        "profile",
        "email",
        "urn:zitadel:iam:org:project:role:gerrit:dev",
        "urn:zitadel:iam:org:project:role:gerrit:ci",
        "urn:zitadel:iam:org:project:role:gerrit:admin",
        "urn:zitadel:iam:org:id:398292855807856172",
    ]
)
CREDENTIALS_PATH = "~/.config/rbe/creds-gerrit.json"

WARNING_NO_REFRESH_TOKEN = """
WARNING: The identity provider did not supply a refresh token, this may require
a large amount of reauthentications. Please contact your OIDC administrator.
"""


class AuthRequestData(TypedDict):
    device_code: str
    user_code: str
    verification_uri_complete: str
    interval: int
    expires_in: int


class TokenData(TypedDict):
    access_token: str
    refresh_token: str
    id_token: str
    expires_in: int


class IdTokenClaims(TypedDict):
    aud: str
    exp: float
    iat: float


class OIDCEndpoints(TypedDict):
    device_authorization_endpoint: str
    token_endpoint: str


def discover_endpoints(issuer_url: str) -> OIDCEndpoints | None:
    config_url = f"{issuer_url}/.well-known/openid-configuration"
    try:
        with urllib.request.urlopen(config_url, timeout=10) as response:
            config_data = json.loads(response.read().decode("utf-8"))

        device_auth_endpoint = config_data.get("device_authorization_endpoint")
        token_endpoint = config_data.get("token_endpoint")

        if not device_auth_endpoint:
            print(
                "ERROR: 'device_authorization_endpoint' not found in OIDC configuration.",
                file=sys.stderr,
            )
            return None
        if not token_endpoint:
            print(
                "ERROR: 'token_endpoint' not found in OIDC configuration.",
                file=sys.stderr,
            )
            return None

        return config_data
    except (urllib.error.URLError, json.JSONDecodeError) as e:
        print(f"ERROR: Could not retrieve OIDC configuration: {e}", file=sys.stderr)
        return None


def try_decode_jwt_payload(token: str):
    try:
        payload_b64 = token.split(".")[1]
        payload_b64 += "=" * (-len(payload_b64) % 4)
        decoded_payload = base64.urlsafe_b64decode(payload_b64).decode("utf-8")
        return json.loads(decoded_payload)
    except Exception:
        return None


def poll_for_token(
    auth_data: AuthRequestData, token_endpoint: str, client_id: str
) -> TokenData | None:
    device_code = auth_data["device_code"]
    user_code = auth_data["user_code"]
    verification_uri = auth_data.get("verification_uri_complete") or auth_data.get(
        "verification_uri"
    )
    interval = auth_data.get("interval", 5)
    expires_in = auth_data["expires_in"]

    print(f"Please open a browser and enter the code {user_code} at:")
    print(f"  {verification_uri}")

    polling_start_time = time.time()
    while True:
        elapsed_time = time.time() - polling_start_time
        if elapsed_time > expires_in:
            print(
                "ERROR: The device code has expired. Please try again.", file=sys.stderr
            )
            return None
        time.sleep(interval)

        token_payload = {
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            "device_code": device_code,
            "client_id": client_id,
        }
        data = urllib.parse.urlencode(token_payload).encode("utf-8")
        req = urllib.request.Request(token_endpoint, data=data, method="POST")

        try:
            with urllib.request.urlopen(req) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            try:
                error_data = json.loads(e.read().decode("utf-8"))
                error = error_data.get("error")
                if error == "authorization_pending":
                    continue
                else:
                    print(
                        f"ERROR: Unexpected error when polling for tokens: {error}",
                        file=sys.stderr,
                    )
                    print(
                        f"Details: {error_data.get('error_description')}",
                        file=sys.stderr,
                    )
                    return None
            except (json.JSONDecodeError, AttributeError):
                print(f"Network error while polling: HTTP {e.code}", file=sys.stderr)
                return None
        except urllib.error.URLError as e:
            print(f"Network error while polling: {e}")
    return None


def get_auth_data(client_id, scope, device_auth_url) -> AuthRequestData | None:
    try:
        device_auth_payload = {
            "client_id": client_id,
            "scope": scope,
        }
        data = urllib.parse.urlencode(device_auth_payload).encode("utf-8")
        req = urllib.request.Request(device_auth_url, data=data, method="POST")
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print(
            f"ERROR: Could not start device authorization: HTTP {e.code}",
            file=sys.stderr,
        )
        try:
            print(f"ERROR: Response body: {e.read().decode('utf-8')}", file=sys.stderr)
        except Exception:
            print("ERROR: No response body.", file=sys.stderr)
    except urllib.error.URLError as e:
        print(f"ERROR: Could not start device authorization: {e}", file=sys.stderr)
    return None


def save_tokens(credentials_path: str, token_data: TokenData):
    try:
        cred_path = Path(credentials_path).expanduser()
        cred_path.parent.mkdir(parents=True, exist_ok=True)
        with open(cred_path, "w") as f:
            json.dump(token_data, f, indent=2)
        cred_path.chmod(0o600)
    except Exception as e:
        print(f"ERROR: Failed to save tokens: {e}", file=sys.stderr)
        sys.exit(1)


def try_refresh_token(refresh_token, token_endpoint, client_id) -> TokenData | None:
    payload = {
        "grant_type": "refresh_token",
        "refresh_token": refresh_token,
        "client_id": client_id,
    }
    data = urllib.parse.urlencode(payload).encode("utf-8")
    req = urllib.request.Request(token_endpoint, data=data, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception:
        return None


def try_existing_credentials(credentials_path, client_id, issuer_url) -> bool:
    path = Path(credentials_path).expanduser()
    exp_buffer = 300
    try:
        with open(path, "r") as f:
            token_data: TokenData = json.load(f)

        id_token = token_data["id_token"]
        claims = try_decode_jwt_payload(id_token)

        if claims["exp"] - exp_buffer > time.time():
            return True

        refresh_token = token_data["refresh_token"]
        oidc_urls = discover_endpoints(issuer_url)
        if not oidc_urls:
            return False

        token_data = try_refresh_token(
            refresh_token, oidc_urls["token_endpoint"], client_id
        )
        if not token_data:
            return False

        save_tokens(credentials_path, token_data)
        return True
    except Exception:
        return False


def main():
    # No interactive login in CI
    if os.getenv("CI") is not None:
        return 0
    if try_existing_credentials(CREDENTIALS_PATH, CLIENT_ID, ISSUER_URL):
        return 0
    if not ISSUER_URL:
        print(
            f"The environment variable BAZEL_OIDC_ISSUER_URL is not set",
            file=sys.stderr,
        )
        return 1
    if not CLIENT_ID:
        print(
            f"The environment variable BAZEL_OIDC_CLIENT_ID is not set", file=sys.stderr
        )
        return 1

    oidc_urls = discover_endpoints(ISSUER_URL)
    if not oidc_urls:
        return 1

    auth_data = get_auth_data(
        CLIENT_ID, SCOPE, oidc_urls["device_authorization_endpoint"]
    )
    if not auth_data:
        return 1

    token_data = poll_for_token(auth_data, oidc_urls["token_endpoint"], CLIENT_ID)
    if not token_data:
        return 1

    id_token = token_data.get("id_token", None)
    if not id_token:
        print(
            "ERROR: Successfully authenticated but the identity provider did not return an id token",
            file=sys.stderr,
        )
        return 1
    if not token_data.get("refresh_token", None):
        print(WARNING_NO_REFRESH_TOKEN)

    save_tokens(CREDENTIALS_PATH, token_data)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("Cancelled by keyboard interrupt")
        sys.exit(1)
