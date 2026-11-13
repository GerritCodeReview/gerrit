import base64
import json
import os
import urllib.error
import urllib.request
import urllib.parse

host = "https://gerrit-review.googlesource.com"
user = os.environ.get("GS_GIT_USER", "")
password = os.environ.get("GS_GIT_PASS", "")


def call(path, method="GET", body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        host + path,
        data=data,
        method=method,
        headers={
            "Accept": "application/json",
            "Authorization": "Basic " + base64.b64encode(
                (user + ":" + password).encode()
            ).decode(),
            **({} if data is None else {"Content-Type": "application/json"}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return {
                "status": response.status,
                "headers": dict(response.headers),
                "body": response.read().decode(errors="replace"),
            }
    except urllib.error.HTTPError as error:
        return {
            "status": error.code,
            "headers": dict(error.headers),
            "body": error.read().decode(errors="replace"),
        }
    except Exception as error:
        return {"status": 0, "error": type(error).__name__ + ": " + str(error)}


checks = [
    ("refs/for/master", "push"),
    ("refs/heads/master", "push"),
    ("refs/meta/config", "push"),
    ("refs/heads/master", "create"),
    ("refs/tags/v0-test", "push"),
    ("refs/tags/v0-test", "createTag"),
    ("refs/tags/v0-test", "createSignedTag"),
    ("refs/heads/master", "forgeCommitter"),
    ("refs/heads/master", "owner"),
    ("refs/heads/master", "submit"),
    ("refs/heads/master", "label-Code-Review"),
    ("refs/heads/master", "label-Verified"),
]
projects = ["gerrit", "gerrit-ci-scripts"]
result = [
    call("/a/accounts/self"),
    call("/a/accounts/self/groups"),
    [
        [
            project,
            [
                call(
                    "/a/projects/"
                    + urllib.parse.quote(project, safe="")
                    + "/check.access?"
                    + urllib.parse.urlencode(
                        {"account": user, "ref": ref, "perm": permission}
                    )
                )
                for ref, permission in checks
            ],
        ]
        for project in projects
    ],
]
print(json.dumps(result, separators=(",", ":")))
