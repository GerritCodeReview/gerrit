---
name: gerrit-integration-test
description: Use this skill when writing, adding, or fixing Java integration tests (acceptance tests) in the Gerrit codebase. Trigger on requests like "add a test for", "write a test verifying", "add an IT test", "test that a user without X gets Y", or any request to create or modify a *IT.java file in javatests/. Also trigger when the user asks why a test is failing or how to structure a new test class.
---

# Gerrit Integration Test Skill

Gerrit acceptance tests are full end-to-end tests that run against a real in-process Gerrit server. Every test class extends `AbstractDaemonTest`.

## Test Class Skeleton

```java
package com.google.gerrit.acceptance.rest.project; // match directory

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import org.junit.Test;

public class MyFeatureIT extends AbstractDaemonTest {

  @Test
  public void descriptiveName_describesExpectedOutcome() throws Exception {
    // arrange → act → assert
  }
}
```

Name convention: `<ThingUnderTest>IT.java`. Test method names are `scenario_expectedOutcome()`.

## What `AbstractDaemonTest` Provides

Pre-injected fields ready to use (no `@Inject` needed):
- `admin` / `user` — `TestAccount` (pre-created admin and unprivileged user)
- `adminRestSession` / `userRestSession` / `anonymousRestSession` — `RestSession` for HTTP calls
- `project` — `Project.NameKey` of the per-test project (auto-created, auto-reset)
- `testRepo` — `TestRepository<InMemoryRepository>` cloned as admin
- `gApi` — `GerritApi` (Java API, runs as admin)
- `pushFactory` — for creating Git pushes

Fields declared with `@Inject` in the test class itself are also injected by Guice.

## Creating Changes

**Via Git push (most realistic):**
```java
PushOneCommit.Result r = pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master");
r.assertOkStatus();
// r.getChangeId(), r.getCommit(), r.getChange() available
```

**Via `ChangeOperations` (faster, no Git):**
```java
@Inject private ChangeOperations changeOperations;

Change.Id id = changeOperations.newChange().project(project).create();
// Or get full details:
TestChange change = changeOperations.newChange().createAndGet();
```

`ChangeOperations` bypasses permissions — don't use it to test the push/create API itself.

## REST API Testing

```java
// GET with JSON response
RestResponse resp = adminRestSession.get("/projects/" + project.get() + "/random_change");
resp.assertOK();                          // 200
resp.assertNotFound();                    // 404
resp.assertForbidden();                   // 403
resp.assertStatus(409);                   // any code
ChangeInfo info = newGson().fromJson(resp.getReader(), ChangeInfo.class);

// POST / PUT
RestResponse r = adminRestSession.post("/projects/" + project.get() + "/create.change", input);
r.assertCreated();                        // 201
```

Available methods on `RestSession`: `get`, `post`, `put`, `delete`, `getJsonAsList`.

## Java API Testing

```java
// Use gApi as admin, or switch user first (see Switching Users below)
ChangeInfo info = gApi.changes().id(changeId).get();
gApi.changes().id(changeId).abandon();
assertThrows(AuthException.class, () -> gApi.changes().id(changeId).submit());
```

Import `assertThrows` from `com.google.gerrit.testing.GerritJUnit`.

## Switching Users

**For `gApi` and internal calls:**
```java
@Inject private RequestScopeOperations requestScopeOperations;

requestScopeOperations.setApiUser(user.id());
// gApi now runs as 'user'
```

**For `RestSession` calls:** just use `userRestSession` directly (no switch needed).

**Create additional accounts:**
```java
@Inject private AccountOperations accountOperations;

Account.Id bob = accountOperations.newAccount().username("bob").create();
requestScopeOperations.setApiUser(bob);
```

## Permission Setup

```java
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.*;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

@Inject private ProjectOperations projectOperations;

// Block a permission
projectOperations.project(project).forUpdate()
    .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
    .update();

// Grant a permission
projectOperations.project(project).forUpdate()
    .add(allow(Permission.PUSH).ref("refs/*").group(REGISTERED_USERS))
    .update();

// Label permission
projectOperations.project(project).forUpdate()
    .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(-2, 2))
    .update();
```

Permission changes apply for the duration of the test; `ProjectResetter` rolls back `refs/meta/config` after each test.

Key groups: `REGISTERED_USERS`, `ANONYMOUS_USERS`, `PROJECT_OWNERS` (from `SystemGroupBackend`).

## Common Annotations

| Annotation | Effect |
|---|---|
| `@NoHttpd` | Skip HTTP server startup — faster, use for `gApi`-only tests |
| `@Sandboxed` | Isolated server (don't share across tests in class) |
| `@TestProjectInput(createEmptyCommit = false)` | Create the test project without an initial commit |
| `@GerritConfig(name = "section.key", value = "val")` | Override gerrit.config for one test method |
| `@UseClockStep` | Advance clock by 1s on every tick (useful for ordering) |

## BUILD File

Each `*IT.java` file needs a `BUILD` entry. The standard pattern uses the `acceptance_tests` macro:

```python
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["rest"],   # api | git | rest | server | ssh | notedb | edit | pgm
) for f in glob(["*IT.java"])]
```

If the test needs extra deps (e.g., `ProjectOperations`, custom helpers), add them to the `deps` list in the `acceptance_tests` call. The macro always includes `//java/com/google/gerrit/acceptance:lib`, which covers most injection needs.

To run the test after editing BUILD:
```bash
bazelisk test //javatests/com/google/gerrit/acceptance/rest/project:MyFeatureIT
```

## Checklist for a new integration test

1. Extend `AbstractDaemonTest`, add `@NoHttpd` if not using HTTP.
2. Name the class `<Subject>IT` and place it under `javatests/com/google/gerrit/acceptance/`.
3. Inject extra helpers (`ProjectOperations`, `ChangeOperations`, etc.) with `@Inject`.
4. Add an entry in the directory's `BUILD` file.
5. Run `bazelisk test //javatests/...:MyClassIT` to verify.
6. Format with `./tools/gjf.sh run`.
