---
name: gerrit-rest-api
description: Implements REST API entrypoints in Gerrit's Java backend. Use when adding new REST endpoints, REST collections, or REST views, or when working with RestResource, RestCollection, RestReadView, RestModifyView, or module bindings for REST API in the Gerrit project.
---

# Skill: Writing REST API Entrypoints in Gerrit

All interfaces live in `com.google.gerrit.extensions.restapi`; implementations
in `com.google.gerrit.server.restapi.<domain>`.

URL pattern: `/{collection}/{id}[/{child-collection}/{id}][/{action}]`

---

## Core Interfaces

| Interface | Purpose |
|---|---|
| `RestResource` | Marker; wraps a parsed URL segment |
| `RestCollection<P,R>` | Parses `R` from an `IdString`; owns the `DynamicMap` of views |
| `ChildCollection<P,R>` | Sub-collection extending `RestCollection` |
| `RestReadView<R>` | GET handler (`apply(R)`) |
| `RestModifyView<R,I>` | POST/PUT/DELETE handler (`apply(R, I)`) |
| `RestCollectionModifyView<P,R,I>` | POST directly on a collection |
| `RestCollectionCreateView<P,R,I>` | PUT to create a new child resource |
| `AcceptsCreate<I>` | Collection that allows POST to create |
| `NeedsParams` | View that receives raw query parameters |

---

## 1. Resource

```java
public class MyResource implements RestResource {
  public static final TypeLiteral<RestView<MyResource>> MY_KIND = new TypeLiteral<>() {};

  private final MyData data;
  private final CurrentUser user;

  public MyResource(MyData data, CurrentUser user) {
    this.data = data;
    this.user = user;
  }

  public MyData getData() { return data; }
  public CurrentUser getUser() { return user; }
}
```

---

## 2. Collection

```java
@Singleton
public class MyCollection implements RestCollection<TopLevelResource, MyResource> {
  private final DynamicMap<RestView<MyResource>> views;
  private final Provider<ListMy> list;   // lazy; avoids circular deps
  private final MyStore store;

  @Inject
  MyCollection(
      DynamicMap<RestView<MyResource>> views,
      Provider<ListMy> list,
      MyStore store) {
    this.views = views;
    this.list = list;
    this.store = store;
  }

  @Override
  public RestView<MyResource> list() throws ResourceNotFoundException {
    return list.get();  // called for GET /{collection}
  }

  @Override
  public MyResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, PermissionBackendException {
    MyData data = store.get(id.get());
    if (data == null) {
      throw new ResourceNotFoundException(id);
    }
    return new MyResource(data, parent.getUser());
  }

  @Override
  public DynamicMap<RestView<MyResource>> views() { return views; }
}
```

For a **child collection**, implement `ChildCollection<ParentResource, MyResource>` instead.

---

## 3. Views

### GET — `RestReadView<R>`

```java
@Singleton
public class GetMy implements RestReadView<MyResource> {
  @Inject
  GetMy() {}

  @Override
  public Response<MyInfo> apply(MyResource rsrc)
      throws RestApiException, PermissionBackendException {
    return Response.ok(toInfo(rsrc.getData()));
  }

  private static MyInfo toInfo(MyData data) { ... }
}
```

### POST/PUT/DELETE — `RestModifyView<R, I>`

```java
@Singleton
public class CreateMy implements RestModifyView<TopLevelResource, CreateMyInput> {
  @Inject
  CreateMy(MyStore store) { this.store = store; }

  @Override
  public Response<MyInfo> apply(TopLevelResource parent, CreateMyInput input)
      throws RestApiException {
    if (input.name == null || input.name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    MyData data = store.create(input.name);
    return Response.created(toInfo(data));  // 201
  }
}

public class CreateMyInput {   // input: public fields, no getters
  public String name;
  public String description;
}
```

### POST on a collection — `RestCollectionModifyView<P, R, I>`

```java
@Singleton
public class PostMyAction
    implements RestCollectionModifyView<TopLevelResource, MyResource, MyActionInput> {
  @Override
  public Response<MyInfo> apply(TopLevelResource parent, MyActionInput input)
      throws RestApiException { ... }
}
```

---

## 4. Module Binding

```java
public class MyApiModule extends AbstractModule {
  @Override
  protected void configure() {
    // 1. Register the DynamicMap for the resource kind
    DynamicMap.mapOf(binder(), MyResource.MY_KIND);

    // 2. Bind views
    get(MyResource.MY_KIND).to(GetMy.class);               // GET /{id}
    get(MyResource.MY_KIND, "detail").to(GetMyDetail.class); // GET /{id}/detail
    put(MyResource.MY_KIND).to(UpdateMy.class);            // PUT /{id}
    delete(MyResource.MY_KIND).to(DeleteMy.class);         // DELETE /{id}
    post(MyResource.MY_KIND, "action").to(MyAction.class); // POST /{id}/action

    // 3. Register top-level collection under the REST root
    get(TopLevelResource.INSTANCE_KIND).to(ListMy.class);  // GET /{collection}
    post(TopLevelResource.INSTANCE_KIND).to(CreateMy.class); // POST /{collection}

    // 4. Child collection
    child(MyResource.MY_KIND, "children").to(MyChildCollection.class);
  }
}
```

`bind(MyCollection.class)` is done in the module that registers the collection
route in `RestApiServlet`; this is typically done in `GerritGlobalModule` or a
sub-module installed there.

---

## 5. Response Types

| Factory method | HTTP status | When to use |
|---|---|---|
| `Response.ok(T)` | 200 | Normal GET/POST result |
| `Response.created(T)` | 201 | Resource created |
| `Response.accepted(T)` | 202 | Async operation started |
| `Response.none()` | 204 | Void operation (DELETE, etc.) |
| `Response.withStatusCode(int, T)` | any | Rarely needed |
| Return `T` directly | 200 | Shorthand for `Response.ok(T)` |

For binary/streaming responses return `BinaryResult`:

```java
return BinaryResult.create(bytes)
    .setContentType("application/json")
    .setContentLength(bytes.length);
```

---

## 6. HTTP Error Mapping

| Exception | HTTP | Guidance |
|---|---|---|
| `ResourceNotFoundException` | 404 | Prefer over `AuthException` when resource existence must be hidden |
| `AuthException` | 403 | When user is known to lack permission |
| `BadRequestException` | 400 | Invalid input |
| `ResourceConflictException` | 409 | State conflict |
| `MethodNotAllowedException` | 405 | Operation not supported |
| `UnprocessableEntityException` | 422 | Valid syntax but semantic error |

---

## 7. Query Parameters (`NeedsParams`)

```java
@Singleton
public class ListMy implements RestReadView<TopLevelResource>, NeedsParams {
  private static final int DEFAULT_LIMIT = 25;
  private int limit = DEFAULT_LIMIT;

  @Override
  public void setParams(ListMultimap<String, String> params) throws BadRequestException {
    if (params.containsKey("limit")) {
      try {
        limit = Integer.parseInt(Iterables.getOnlyElement(params.get("limit")));
      } catch (NumberFormatException e) {
        throw new BadRequestException("limit must be an integer");
      }
    }
  }

  @Override
  public Response<List<MyInfo>> apply(TopLevelResource rsrc) throws RestApiException {
    return Response.ok(store.list(limit));
  }
}
```

---

## 8. `AcceptsCreate` (POST to collection creates child)

```java
@Singleton
public class MyCollection
    implements RestCollection<TopLevelResource, MyResource>, AcceptsCreate<CreateMyInput> {
  // ...parse(), list(), views() as before...

  @Override
  public RestModifyView<TopLevelResource, CreateMyInput> create(
      TopLevelResource parent, IdString id) throws RestApiException {
    return createView;   // injected CreateMy view
  }
}
```

---

## 9. Permissions

Check permissions inside `parse()` or `apply()` using `PermissionBackend`:

```java
permissionBackend.user(user).change(notes).check(ChangePermission.READ);
permissionBackend.user(user).project(project).check(ProjectPermission.WRITE_CONFIG);
```

Throw `ResourceNotFoundException` (not `AuthException`) when the resource must
not be revealed to the caller.

---

## 10. Testing

```java
// In an AbstractDaemonTest subclass:
adminRestSession.get("/my-resources/").assertOK();
adminRestSession.get("/my-resources/1234").assertOK();
adminRestSession.post("/my-resources/1234/action", new MyActionInput()).assertOK();
adminRestSession.delete("/my-resources/1234").assertNoContent();  // 204
userRestSession.get("/my-resources/1234").assertForbidden();       // 403

// Parse response body:
MyInfo info = adminRestSession.get("/my-resources/1234")
    .getEntity(MyInfo.class);
```

---

## Key Rules

- `RestResource` fields must be `final`; pass all state via constructor.
- Input POJOs have `public` fields — no getters, no constructors.
- Output Info classes (`MyInfo`) also have `public` fields.
- Inject `Provider<X>` (lazy) when there's a risk of circular dependency.
- All views must be `@Singleton`.
- Never call `permissionBackend` with a hardcoded user; always use the
  `CurrentUser` from the resource or request context.
- Register the `DynamicMap.mapOf(...)` call exactly once per resource kind.
