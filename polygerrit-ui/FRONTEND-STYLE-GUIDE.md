# Gerrit JavaScript style guide

Gerrit frontend follows [recommended eslint rules](https://eslint.org/docs/rules/)
and [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html).
Eslint is used to automate rules checking where possible. You can find exact eslint rules
[here](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/.eslintrc.js).

Gerrit JavaScript code uses ES6 modules and doesn't use goog.module files.

Additionally to the rules above, Gerrit frontend uses the following rules (some of them have automated checks,
some doesn't):

 
- [No default export](no-default-export)
- [Use destructuring imports only](destructuring-imports-only)
- [Do not create static container classes and objects](no-static-containers)
- [Use classes and services for storing and manipulating global state](services-for-global-state)
- [Pass required services in the constructor for plain classes](pass-dependencies-in-constructor)
- [Assign required services in a HTML/Polymer element constructor](assign-dependencies-in-html-element-constructor)


## <a id="no-default-export">No default export
Use only named exports in ES6 modules and do not use default export.
Default export assumes that developer must assign some name everytime when the default export is imported.
Such export makes refactoring and renaming files much more harder - to keep code constistent, developer usually
must manually updates all imports to match import name and the filename. With named imports this task can
be done by IDE. 

**Example:**
```JavaScript
//some-element.js
export const someElement = ...;
```

**Disallowed:**
```JavaScript
// some-element.js
export default someElement;

// usage.js
// If you rename the 'some-element.js' file, you have to rename someElement name
// manually in the following import statement to keep code consistent.
import * as someElement from './some-element.js'; 
```

## <a id="destructuring-imports-only">Use destructuring imports only

When you are import several names from the same module never use star import. Instead use destructuring import
statement and specify all required names explicitly.

**Note:** It is not always possible to use destructuring imports with 3rd-party libraries (if a third-party library
exposes a class/function/const/etc... as a default export). In this situation you can use default import, but please
keep consistent naming across the whole gerrit project. The best way to keep consistency is to search across our
codebase for the same import. If you find exactly the same import - use the same name for your import. If you can't
find exact matches - find the similar import and construct appropriate name for your default import. Usually, the
name should includes library name and part of the file path.   

**Example**
```Javascript
// Import from the module in the same project.
import {getDisplayName, getAccount} from './user-utils.js'

// The following default import is allowed only for 3rd-party libraries.
// Please ensure, that all imports have the same name accross gerrit project (downloadImage in this example)
import downloadImage from 'third-party-library/images/download.js'
``` 

**Disallowed:**
```Javascript
import * as userUtils from './user-utils.js'
```

## <a id="no-static-containers">Do not create static container classes and objects

Do not use container classes or objects with static methods or properties for the sake of namespacing.
Instead, export individual constants and functions.

**Example:**
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

**Disallowed:**
```Javascript
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

## <a id="services-for-global-state">Use classes and services for storing and manipulating global state

You must use classes and services to share global state across gerrit frontend code. Do not put a state at the top of
a module.

Service name must ends with a `Service` suffix.

To share global state across modules in the project, do the following:
- put the state in a class
- add your service to the
[appContext](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context.js)
- add service initialization code to the 
[services/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/services/app-context-init.js) file.
- add service/mock initialization code to the
[embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file.


If your service depends on another services - please see the example below.


**Note:** Be carefull with the shared gr-diff element. If your service is not required for the shared gr-diff,
the safest option is to provide a mock for your service in the embed/app-context-init.js file. In exceptional
cases you can keep service uninitialized in
[embed/app-context-init.js](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/embed/app-context-init.js) file
, but it is recommended to write a comment
why you can't mock a service. In the future we can review/update rules regarding the shared gr-diff element.

**Example:**
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
    // If your service depends on other services, pass dependencies as shown below:
    // If you have circulate dependencies, tests fail with timeout or stack overflow
    // (we are  going to improve it in the future)
    addService('analyticService', () =>
        new CounterService(appContext.mouseClickCounterService, appContext.keypressCounterService));
    //...
    // This following line must remains the last one in the initAppContext
    Object.defineProperties(appContext, registeredServices); 
}
```

**Disallowed:**
```Javascript
// module counter.js
let count = 0;
export function getCount() {
    return count;
}
export function incCount() {
    count++;
}
```

## <a id="pass-dependencies-in-constructor">Pass required services in the constructor for plain classes

If your class/service depends on some other service (or multiple services), the class must accept all dependencies
as parameters in the constructor.

Do not use appContext anywhere in your class.

**Note:** This rule doesn't apply for HTML/Polymer elements classes. A browser creates instances of such classes
implicitly and calls constructor without a parameter. See
[Assign required services in a HTML/Polymer element constructor](assign-dependencies-in-html-element-constructor)

**Example:**
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

**Disallowed:**
```Javascript
import {appContext} from "./app-context";

export class UserService {
    constructor() {
        // Incorrect - you must pass all dependencies to a constructor
        this._restApiService = appContext.restApiService;
    }
}

export class AdminService {
    isAdmin() {        
        // Incorrect - you must pass all dependencies to a constructor
        return appContext.restApiService.sendRequest(...);
    }
}

```

## <a id="assign-dependencies-in-html-element-constructor">Assign required services in a HTML/Polymer element constructor 
If your class implements a custom HTML/Polymer element and a browser creates instances of such class
implicitly, the class must assign all dependencies in the constructor.

Do not use appContext anywhere except the constructor of the class. 

**Example:**
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

**Disallowed:**
```Javascript
import {appContext} from `.../services/app-context.js`;

export class MyCustomElement extends ...{
    created() {
        // You must assign all dependencies in the constructor 
        this._userService = appContext.userService;
    }
    //...
    _getUserName() {
        // You must not use appContext outside of a constructor
        return appContext.userService.activeUserName();
    }
}
```
