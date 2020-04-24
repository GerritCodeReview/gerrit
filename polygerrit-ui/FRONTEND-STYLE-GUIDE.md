# Gerrit JavaScript style guide

Gerrit frontend follows [recommended eslint rules](https://eslint.org/docs/rules/)
and [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html).
Eslint is used to automate rules checking where possible. You can find exact eslint rules
[here](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/.eslintrc.js).

Gerrit JavaScript code uses ES6 modules and doesn't use goog.module files.

Additionally to the rules above, Gerrit frontend uses the following rules (some of them have automated checks,
some don't):

- [No default export](#no-default-export)
- [Use destructuring imports only](#destructuring-imports-only)
- [Do not create static container classes and objects](#no-static-containers)
- [Use classes and services for storing and manipulating global state](#services-for-global-state)
- [Pass required services in the constructor for plain classes](#pass-dependencies-in-constructor)
- [Assign required services in a HTML/Polymer element constructor](#assign-dependencies-in-html-element-constructor)


## <a name="no-default-export"></a>No default export
Use only named exports in ES6 modules. Do not use default export.

Default export assumes that developer must assign a name in each place where a default export is imported.
Such imports makes refactoring and renaming files much more harder - to keep the code consistent, developer
must manually update all imports to match import name and the filename. With named imports this task can
be done by IDE.

**Good:**
```JavaScript
//some-element.js
export const someElement = ...;
```

**Bad:**
```JavaScript
// some-element.js
export default someElement;

// usage.js
// If you rename the 'some-element.js' file, you have to rename someElement
// in the following import statement to keep code consistent.
import * as someElement from './some-element.js';
```

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

## <a name="no-static-containers"></a>Do not create static container classes and objects

Do not use container classes or objects with static methods or properties for the sake of namespacing.
Instead, export individual constants, functions, variables, etc...

**Good:**
```Javascript
export function getDisplayName(user) {
  // ...
  return name;
}

export function getEmail(user) {
  // ...
  return email;
}
```

**Bad:**
```Javascript
// Incorrect: UserUtils is used for namespacing only
export class UserUtils {
    static getDisplayName(user) {
        // ...
        return name;
    }
    static getEmail(user) {
        // ...
        return email;
    }
}
```

## <a name="services-for-global-state"></a>Use classes and services for storing and manipulating global state

You must use classes and services to share global state across the gerrit frontend code. Do not put a state at the
top level of a module.

**Note:** 

Service name must ends with a `Service` suffix.

To share global state across modules in the project, do the following:
- put the state in a class
- add a new service to the
[appContext](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context.js)
- add a service initialization code to the
[services/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context-init.js) file.
- add a service/mock initialization code to the
[embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file.

Also see the example below if a service depends on another services.

**Note:** Be carefull with the shared gr-diff element. If a service is not required for the shared gr-diff,
the safest option is to provide a mock for this service in the embed/app-context-init.js file. In exceptional
cases you can keep the service uninitialized in
[embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file
, but it is recommended to write a comment
why mocking is not possible. In the future we can review/update rules regarding the shared gr-diff element.

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

// app-context.js
export const appContext = {
    //...
    mouseClickCounterService: null,
    keypressCounterService: null,
};

// services/app-context-init.js
export function initAppContext() {
    //...
    // Add the following line before the Object.defineProperties(appContext, registeredServices);
    addService('mouseClickCounterService', () => new CounterService());
    addService('keypressCounterService', () => new CounterService());
    // If a service depends on other services, pass dependencies as shown below
    // If circular dependencies exist, app-init-context tests fail with timeout or stack overflow
    // (we are  going to improve it in the future)
    addService('analyticService', () =>
        new CounterService(appContext.mouseClickCounterService, appContext.keypressCounterService));
    //...
    // This following line must remains the last one in the initAppContext
    Object.defineProperties(appContext, registeredServices);
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

Do not use appContext anywhere in a class.

**Note:** This rule doesn't apply for HTML/Polymer elements classes. A browser creates instances of such classes
implicitly and calls the constructor without parameters. See
[Assign required services in a HTML/Polymer element constructor](#assign-dependencies-in-html-element-constructor)

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
import {appContext} from "./app-context";

export class UserService {
    constructor() {
        // Incorrect: you must pass all dependencies to a constructor
        this._restApiService = appContext.restApiService;
    }
}

export class AdminService {
    isAdmin() {
        // Incorrect: you must pass all dependencies to a constructor
        return appContext.restApiService.sendRequest(...);
    }
}

```

## <a name="assign-dependencies-in-html-element-constructor"></a>Assign required services in a HTML/Polymer element constructor
If a class is a custom HTML/Polymer element, the class must assign all required services in the constructor.
A browser creates instances of such classes implicitly, so it is impossible to pass anything as a parameter to
the element's class constructor.

Do not use appContext anywhere except the constructor of the class.

**Note for legacy elements:** If a polymer element extends a LegacyElementMixin and overrides the `created()` method,
move all code from this method to a constructor right after the call to a `super()`
([example](#assign-dependencies-legacy-element-example)). The `created()`
method is [deprecated](https://polymer-library.polymer-project.org/2.0/docs/about_20#lifecycle-changes) and is called
when a super (i.e. base) class constructor is called. If you are unsure about moving the code from the `created` method
to the class constructor, consult with the source code:
[`LegacyElementMixin._initializeProperties`](https://github.com/Polymer/polymer/blob/v3.4.0/lib/legacy/legacy-element-mixin.js#L318)
and
[`PropertiesChanged.constructor`](https://github.com/Polymer/polymer/blob/v3.4.0/lib/mixins/properties-changed.js#L177)



**Good:**
```Javascript
import {appContext} from `.../services/app-context.js`;

export class MyCustomElement extends ...{
    constructor() {
        super(); //This is mandatory to call parent constructor
        this._userService = appContext.userService;
    }
    //...
    _getUserName() {
        return this._userService.activeUserName();
    }
}
```

**Bad:**
```Javascript
import {appContext} from `.../services/app-context.js`;

export class MyCustomElement extends ...{
    created() {
        // Incorrect: assign all dependencies in the constructor
        this._userService = appContext.userService;
    }
    //...
    _getUserName() {
        // Incorrect: use appContext outside of a constructor
        return appContext.userService.activeUserName();
    }
}
```

<a name="assign-dependencies-legacy-element-example"></a>
**Legacy element:**

Before:
```Javascript
export class MyCustomElement extends ...LegacyElementMixin(...) {
    constructor() {
        super();
        someAction();
    }
    created() {
        super();
        createdAction1();
        createdAction2();
    }
}
```

After:
```Javascript
export class MyCustomElement extends ...LegacyElementMixin(...) {
    constructor() {
        super();
        // Assign services here
        this._userService = appContext.userService;
        // Code from the created method - put it before existing actions in constructor
        createdAction1();
        createdAction2();
        // Original constructor code
        someAction();
    }
    // created method is removed
}
```
