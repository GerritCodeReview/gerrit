---
name: gerrit-java-dev
description: Generates Java code for the Gerrit project, following project
conventions and best practices. Use when implementing new features, fixing bugs,
or refactoring existing code in Gerrit's backend, this might include prompts
like "Implement a new feature", "Fix a bug", "Add tests" or "Refactor existing
code" in the Gerrit project.
---

# Skill: Writing Java Code for the Gerrit Project

## Overview

Gerrit is a Java web application built with Bazel. Backend uses Google Guice
(DI), JGit (Git), and NoteDb (Git-based storage for code review metadata).

---

## Code Style

- Google Java Style Guide; run `./tools/gjf.sh run` before committing.
- `buildifier` for `BUILD`/`WORKSPACE`/`.bzl`/`.bazel` files.
- Max line length: **100 chars** (commit messages: **72 chars**).
- No wildcard imports.

### `final` Usage

| Context | Rule |
|---|---|
| Instance fields | **Always** `final` |
| Static fields | **Always** `final` |
| Local variables & parameters | **Never** `final` |
| Classes/methods | Only when semantically required |

### `Optional` and `@Nullable`

- Return `Optional<T>` or a non-null value — never return `null`.
- Use `@Nullable` on parameters — **not** `Optional<T>` as argument.

### Class Member Order

1. Copyright header
2. `FluentLogger` (first instance member)
3. Static interfaces → non-static interfaces
4. Static types/fields/methods (decreasing visibility)
5. Instance types/fields
6. Assisted injection factory (if any)
7. Constructors
8. Instance methods

Annotations before language keywords: `@Assisted @Nullable final String name`.

### Copyright Header

```java
// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// ...
```

Replace copyright year with current year; use "The Android Open Source Project"
as the copyright holder for all Gerrit code, including plugins.

---

### Package Layout (`java/com/google/gerrit/`)

| Package | Purpose |
|---|---|
| `acceptance/` | Helpers for integration tests |
| `entities/` | Immutable domain models |
| `extensions/` | Extension points usable by plugins |
| `extensions/api/` | Public extension API |
| `httpd/restapi/` | REST framework: `RestCollection`, `RestResource` |
| `lucene/` | Lucene implementation of indexes |
| `pgm/` | CLI programs |
| `server/` | Core business logic |
| `server/account/` | Account resolution |
| `server/cache/` | Cache backend |
| `server/change/` | Change-specific actions |
| `server/config/` | Config-specific actions |
| `server/events/` | Event system |
| `server/git/` | JGit wrappers, git repository interactions |
| `server/group/` | Group-specific actions |
| `server/index/` | Index definitions |
| `server/notedb/` | NoteDb R/W |
| `server/permissions/` | `PermissionBackend`, authorization |
| `server/plugins/` | plugin management |
| `server/project/` | Project-specific actions |
| `server/query/` | index queries |
| `server/restapi/` | REST API definition |
| `server/update/` | `BatchUpdate` framework |
| `sshd/` | SSHD implementation, SSH CLI command definitions |

---

## Dependency Injection (Guice)

Always use **constructor injection**.

```java
@Singleton
public class MyService {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ChangeNotes.Factory changeNotesFactory;

  @Inject
  MyService(ChangeNotes.Factory changeNotesFactory) {
    this.changeNotesFactory = changeNotesFactory;
  }
}
```

### Modules

```java
public class MyModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SomeInterface.class).to(SomeImpl.class).in(Scopes.SINGLETON);
    DynamicSet.bind(binder(), EventListener.class).to(MyListener.class);
    DynamicItem.bind(binder(), AvatarProvider.class).to(MyProvider.class);
  }
}
```

- `DynamicSet<T>` — multiple implementations (many plugins contribute).
- `DynamicItem<T>` — single replaceable implementation (last binding wins).

---

## REST API Pattern

### Resource

```java
public class ProjectResource implements RestResource {
  public static final TypeLiteral<RestView<ProjectResource>> PROJECT_KIND = new TypeLiteral<>() {};
  private final ProjectState state;
  private final CurrentUser user;
  // constructor + getters
}
```

### Collection

```java
@Singleton
public class ProjectsCollection implements RestCollection<TopLevelResource, ProjectResource> {
  @Inject
  ProjectsCollection(DynamicMap<RestView<ProjectResource>> views, Provider<ListProjects> list) { ... }

  @Override
  public ProjectResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, PermissionBackendException { ... }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() { return views; }
}
```

### Views

- `RestReadView<R>` → GET
- `RestModifyView<R, I>` → POST/PUT/DELETE (`I` = input POJO with public fields)

```java
@Singleton
public class AbandonChange implements RestModifyView<ChangeResource, AbandonInput> {
  @Override
  public ChangeInfo apply(ChangeResource rsrc, AbandonInput input)
      throws RestApiException, UpdateException, PermissionBackendException { ... }
}

public class AbandonInput {
  public String message;
  public NotifyHandling notify;
}
```

### Module Binding

```java
DynamicMap.mapOf(binder(), ProjectResource.PROJECT_KIND);
get(ProjectResource.PROJECT_KIND).to(GetProject.class);
post(ProjectResource.PROJECT_KIND, "ban").to(BanCommit.class);
child(ProjectResource.PROJECT_KIND, "branches").to(BranchesCollection.class);
```

### HTTP Error Mapping

| Exception | HTTP |
|---|---|
| `ResourceNotFoundException` | 404 |
| `AuthException` | 403 |
| `BadRequestException` | 400 |
| `ResourceConflictException` | 409 |
| `MethodNotAllowedException` | 405 |
| `UnprocessableEntityException` | 422 |

Prefer `ResourceNotFoundException` over `AuthException` when the user should not know if the resource exists.

---

## Permission Checking

Use `PermissionBackend` — never implement authorization logic manually.

```java
permissionBackend.user(user).change(notes).check(ChangePermission.READ);
permissionBackend.user(user).project(project).check(ProjectPermission.READ);
permissionBackend.user(user).ref(dest).check(RefPermission.READ);
```

---

## Change Model and NoteDb

### Reading

```java
ChangeNotes notes = changeNotesFactory.createChecked(project, changeId);
Change change = notes.getChange();
ImmutableList<PatchSet> patchSets = notes.getPatchSets().values().asList();
```

### Writing via BatchUpdate

Never write to NoteDb directly from a REST view. Use `BatchUpdateOp`:

```java
public class MyOp implements BatchUpdateOp {
  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    ctx.getUpdate(ctx.getChange().currentPatchSetId()).setChangeMessage("msg");
    return true;
  }
}

try (BatchUpdate bu = updateFactory.create(project, user, TimeUtil.now())) {
  bu.addOp(changeId, new MyOp());
  bu.execute();
}
```

### Typed IDs

Always use typed IDs — never raw `int`/`long`/`String`:

```java
Change.Id changeId = Change.id(123);
PatchSet.Id psId = PatchSet.id(changeId, 2);
Account.Id accountId = Account.id(1000000);
Project.NameKey project = Project.nameKey("my-project");
BranchNameKey branch = BranchNameKey.create(project, "refs/heads/main");
```

---

## Event System

### Posting Events

```java
@Inject MyService(DynamicItem<EventDispatcher> dispatcher) { ... }

dispatcher.get().postEvent(event); // catch StorageException
```

Custom events extend `com.google.gerrit.server.events.Event`; register with `EventTypes.register(MyEvent.TYPE, MyEvent.class)`.

### Consuming Events

```java
@Singleton
public class MyListener implements EventListener {
  @Override
  public void onEvent(Event event) {
    if (event instanceof PatchSetCreatedEvent e) { ... }
  }
}
// In module: DynamicSet.bind(binder(), EventListener.class).to(MyListener.class);
```

### Core Listener Interfaces

| Interface | Trigger |
|---|---|
| `EventListener` | All stream events |
| `UserScopedEventListener` | Events filtered by user visibility |
| `LifecycleListener` | Start/stop |
| `GitReferenceUpdatedListener` | Single ref update |
| `GitBatchRefUpdateListener` | Batch ref update |
| `NewProjectCreatedListener` | Project creation |
| `ProjectDeletedListener` | Project deletion |
| `AccountActivationListener` | Account activated/deactivated |
| `ChangeIndexedListener` | Change index updated |

---

## Git Repository Operations

Always use try-with-resources; open multiple resources in one block:

```java
try (Repository repo = repoManager.openRepository(project);
    RevWalk rw = new RevWalk(repo)) {
  RevCommit commit = rw.parseCommit(repo.resolve("refs/heads/main"));
}
```

---

## Account Operations

```java
Optional<AccountState> account = accountCache.get(accountId);
AccountResolver.Result result = accountResolver.resolve("user@example.com");
```

---

## Testing

Extend `AbstractDaemonTest` for integration tests against a full in-memory Gerrit:

```java
public class MyFeatureIT extends AbstractDaemonTest {
  @Test
  public void testFeatureWorks() throws Exception {
    PushOneCommit.Result r = createChange();
    adminRestSession.post("/changes/" + r.getChangeId() + "/my-action", new MyInput()).assertOK();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }
}
```

| Helper | Purpose |
|---|---|
| `PushOneCommit` | Create and push a review change |
| `adminRestSession` / `userRestSession` | REST calls as admin / regular user |
| `gApi` | Direct Java API calls |
| `accountCreator.create(name)` | Create a test account |
| `projectOperations.newProject().name("x").create()` | Create a test project |
| `requestScopeOperations.setApiUser(id)` | Switch current user in request scope |

Always use teh Google Truth library for assertions.

---

## Common Idioms

### Logging

```java
private static final FluentLogger logger = FluentLogger.forEnclosingClass(); // always first field
logger.atInfo().log("Processing %s", changeId);
logger.atWarning().withCause(e).log("Failed %s", changeId);
```

### Immutable Collections & AutoValue

```java
ImmutableList.of("a", "b");
ImmutableMap.of("key", 1);
ImmutableSet.copyOf(ids);

@AutoValue
public abstract class MyKey {
  public abstract Project.NameKey project();
  public abstract Change.Id changeId();
  public static MyKey create(Project.NameKey p, Change.Id c) { return new AutoValue_MyKey(p, c); }
}
```

### Preconditions

```java
checkNotNull(project, "project");
checkArgument(!name.isEmpty(), "name must not be empty");
AccountState a = accountCache.get(id).orElseThrow(() -> new ResourceNotFoundException("Account " + id));
```

---

## Key Classes Reference

| Class / Interface | Package | Purpose |
|---|---|---|
| `PermissionBackend` | `server/permissions` | Authorization |
| `GitRepositoryManager` | `server/git` | Open/list repos |
| `BatchUpdate.Factory` | `server/update` | Create `BatchUpdate` |
| `ChangeNotes.Factory` | `server/notedb` | Load change data |
| `AccountCache` | `server/account` | Load `AccountState` |
| `AccountResolver` | `server/account` | Resolve by email/name/ID |
| `ProjectCache` | `server` | Load `ProjectState` |
| `DynamicItem<EventDispatcher>` | `extensions/registration` | Post stream events |
| `RestReadView<R>` | `extensions/api/access` | GET handler |
| `RestModifyView<R,I>` | `extensions/api/access` | POST/PUT/DELETE handler |
| `RestCollection<P,C>` | `extensions/api/access` | REST collection |
| `AbstractDaemonTest` | `acceptance` | Integration test base |
