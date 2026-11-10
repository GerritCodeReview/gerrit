#!/usr/bin/env python3

import base64
import json
import sys
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import TypedDict

CREDENTIALS_PATH = "~/.config/rbe/creds-gerrit.json"


def get_cached_data() -> dict:
    path = Path(CREDENTIALS_PATH).expanduser()
    if not path.exists():
        raise ValueError(
            f"ERROR: Credentials file not found at {path.resolve()}.\n"
            "Run tools/rbe-login.py to log in."
        )

    with open(path, "r") as f:
        return json.load(f)


def update_cached_data(new_data: dict) -> None:
    path = Path(CREDENTIALS_PATH).expanduser()
    path.parent.mkdir(parents=True, exist_ok=True)

    data = get_cached_data() or {}
    data.update(new_data)

    with open(path, "w") as f:
        json.dump(data, f, indent=2)


class OIDCEndpoints(TypedDict):
    device_authorization_endpoint: str
    token_endpoint: str


def discover_endpoints(issuer_url: str) -> OIDCEndpoints | None:
    config_url = f"{issuer_url}/.well-known/openid-configuration"
    with urllib.request.urlopen(config_url, timeout=10) as response:
        endpoints = json.loads(response.read().decode("utf-8"))

    if not endpoints.get("device_authorization_endpoint"):
        raise ValueError(
            "'device_authorization_endpoint' not found in OIDC configuration."
        )
    if not endpoints.get("token_endpoint"):
        raise ValueError("'token_endpoint' not found in OIDC configuration.")

    return endpoints


@dataclass
class OidcIdp:
    issuer_url: str
    client_id: str

    def refresh_id_token(self) -> str:
        oidc_endpoints = discover_endpoints(self.issuer_url)
        if not oidc_endpoints:
            raise RuntimeError("Could not discover oidc endpoints.")

        cached_data = get_cached_data() or {}
        refresh_token = cached_data.get("refresh_token")
        if not refresh_token:
            raise RuntimeError(
                "No refresh token found. Run tools/rbe-login.py to log in again."
            )

        payload = {
            "grant_type": "refresh_token",
            "refresh_token": refresh_token,
            "client_id": self.client_id,
        }
        data = urllib.parse.urlencode(payload).encode("utf-8")
        req = urllib.request.Request(
            oidc_endpoints["token_endpoint"], data=data, method="POST"
        )
        with urllib.request.urlopen(req, timeout=10) as response:
            refreshed_data = json.loads(response.read().decode("utf-8"))

        if not refreshed_data or "id_token" not in refreshed_data:
            raise RuntimeError(
                "Could not refresh token. Run tools/rbe-login.py to log in again."
            )

        update_cached_data(refreshed_data)

        return refreshed_data["id_token"]


IDP_CONFIG_REGISTRY: dict[str, OidcIdp] = {
    "buildbar-stage.meroton.com": OidcIdp(
        issuer_url="https://meroton-yka6f8.eu1.zitadel.cloud",
        client_id="388823577480246894",
    ),
    "buildbar.meroton.com": OidcIdp(
        issuer_url="https://meroton-yka6f8.eu1.zitadel.cloud",
        client_id="390836159007170856",
    ),
}


def get_idp(domain: str) -> OidcIdp:
    if not domain:
        raise ValueError("Domain string cannot be empty.")
    clean_domain = domain.strip().lower()

    if clean_domain in IDP_CONFIG_REGISTRY:
        return IDP_CONFIG_REGISTRY[clean_domain]

    for base_domain, details in IDP_CONFIG_REGISTRY.items():
        if clean_domain.endswith(f".{base_domain}"):
            return details

    supported_domains = ", ".join(IDP_CONFIG_REGISTRY.keys())
    raise ValueError(
        f"Unsupported IDP domain '{domain}'. Supported domains: {supported_domains}"
    )


def get_jwt_claims(token: str) -> dict:
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (-len(payload_b64) % 4)
    decoded_payload = base64.urlsafe_b64decode(payload_b64).decode("utf-8")
    return json.loads(decoded_payload)


def get_auth_target_domain() -> str:
    input_data = sys.stdin.readlines()
    if len(input_data) != 1:
        raise ValueError("Unexpected input format.")

    input_line = input_data[0].strip()

    if input_line == "":
        raise ValueError("No input received from stdin.")

    credential_helper_data = json.loads(input_line)
    raw_url = credential_helper_data["uri"]
    split_result = urllib.parse.urlsplit(raw_url)

    if not split_result.netloc:
        raise ValueError("No domain found in Bazel's request URL.")

    return split_result.netloc


def return_id_token(id_token: str) -> None:
    claims = get_jwt_claims(id_token)
    exp_datetime = datetime.fromtimestamp(claims["exp"], timezone.utc)
    exp_iso3339 = exp_datetime.isoformat().replace("+00:00", "Z")
    json.dump(
        {
            "headers": {
                "Authorization": [f"Bearer {id_token}"],
            },
            "expires": exp_iso3339,
        },
        sys.stdout,
    )


def main() -> int:
    try:
        id_token = get_cached_data().get("id_token")

        # If no token cached or it's within 60 seconds of expiring, attempt refresh/fetch
        if not id_token or (get_jwt_claims(id_token).get("exp", 0) - 60 < time.time()):
            domain = get_auth_target_domain()
            idp = get_idp(domain)
            id_token = idp.refresh_id_token()
            update_cached_data({"id_token": id_token})

        return_id_token(id_token)
        return 0
    except ValueError as e:
        # Makes error messages be visible in the output
        sys.stderr.write(f"\n\n{e}\n\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
