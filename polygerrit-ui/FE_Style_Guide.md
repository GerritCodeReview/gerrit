# Gerrit JavaScript style guide

Gerrit frontend follows [recommended eslint rules](https://eslint.org/docs/rules/)
and [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html).
Eslint is used to automate rules checking where possible. You can find exact eslint rules
[here](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/.eslintrc.js).

Gerrit JavaScript code uses ES6 modules and doesn't use goog.module files.

Additionally to the rules above, Gerrit frontend uses the following rules (some of them have automated checks,
some don't):

- [Prefer null over undefined](#prefer-null)
- [Use destructuring imports only](#destructuring-imports-only)
- [Use classes and services for storing and manipulating global state](#services-for-global-state)
- [Pass required services in the constructor for plain classes](#pass-dependencies-in-constructor)

## <a name="prefer-undefined"></a>Prefer `undefined` over `null`

It is more confusing than helpful to work with both `null` and `undefined`. We prefer to only use `undefined` in
our code base. Try to avoid `null`.

Some browser and library APIs are using `null`, so we cannot remove `null` completely from our code base. But even
then try to convert return values and leak as few `nulls` as possible.

## <a name="destructuring-imports-only"></a>Use destructuring imports only

Always use destructuring import statement and specify all required names explicitly (e.g. `import {a,b,c} from '...'`)
where possible.

**Note:** Destructuring imports are not always possible with 3rd-party libraries, because a 3rd-party library
can expose a class/function/const/etc... as a default export. In this situation you can use default import, but please
keep consistent naming across the whole gerrit project. The best way to keep consistency is to search across our
codebase for the same import. If you find an exact match - always use the same name for your import. If you can't
find exact matches - find a similar import and assign appropriate/similar name for your default import. Usually the
name should include a library name and part of the file path.

You can read more about different type of imports
[here](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/import).

**Good:**

```Javascript
// Import from the module in the same project.
import {getDisplayName, getAccount} from './user-utils.js'

// The following default import is allowed only for 3rd-party libraries.
// Please ensure, that all imports have the same name accross gerrit project (downloadImage in this example)
import downloadImage from 'third-party-library/images/download.js'
```

**Bad:**

```Javascript
import * as userUtils from './user-utils.js'
```

## <a name="services-for-global-state"></a>Use classes and services for storing and manipulating global state

You must use classes and services to share global state across the gerrit frontend code. Do not put a state at the
top level of a module.

It is not easy to define precise what can be a shared global state and what is not. Below are some
examples of what can treated as a shared global state:

- Information about enabled experiments
- Information about current user
- Information about current change

**Note:**

Service name must ends with a `Service` suffix.

To share global state across modules in the project, do the following:

- put the state in a class
- add a new service to the
  [appContext](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context.js)
- add a service initialization code to the
  [services/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context-init.js) file.
- add a service or service-mock initialization code to the
  [embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file.
- recommended: add a separate service-mock for testing. Do not use the same mock for testing and for
  the shared gr-diff (i.e. in the `services/app-context-init.js`). Even if the mocks are simple and looks
  identically, keep them separate. It allows to change them independently in the future.

Also see the example below if a service depends on another services.

**Note 1:** Be carefull with the shared gr-diff element. If a service is not required for the shared gr-diff,
the safest option is to provide a mock for this service in the embed/app-context-init.js file. In exceptional
cases you can keep the service uninitialized in
[embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file
, but it is recommended to write a comment why mocking is not possible. In the future we can
review/update rules regarding the shared gr-diff element.

**Good:**

```Javascript
export class CounterService {
    constructor() {
        this._count = 0;
    }
    get count() {
        return this._count;
    }
    inc() {
        this._count++;
    }
}
```

**Bad:**

```Javascript
// module counter.js
// Incorrect: shared state declared at the top level of the counter.js module
let count = 0;
export function getCount() {
    return count;
}
export function incCount() {
    count++;
}
```

## <a name="pass-dependencies-in-constructor"></a>Pass required services in the constructor for plain classes

If a class/service depends on some other service (or multiple services), the class must accept all dependencies
as parameters in the constructor.

Do not use getAppContext() anywhere else in a class.

**Good:**

```Javascript
export class UserService {
    constructor(restApiService) {
        this._restApiService = restApiService;
    }
    getLoggedIn() {
        // Send request to server using this._restApiService
    }
}
```

**Bad:**

```Javascript
import {getAppContext} from "./app-context";

export class UserService {
    constructor() {
        // Incorrect: you must pass all dependencies to a constructor
        this._restApiService = getAppContext().restApiService;
    }
}

export class AdminService {
    isAdmin() {
        // Incorrect: you must pass all dependencies to a constructor
        return getAppContext().restApiService.sendRequest(...);
    }
}

```
