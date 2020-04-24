# Gerrit JavaScript style guide

Gerrit frontend follows [recommended eslint rules](https://eslint.org/docs/rules/)
and [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html).
Eslint is used to automate rules checking where possible. You can find exact eslint rules
[here](https://gerrit.googlesource.com/gerrit/+/refs/heads/master/polygerrit-ui/app/.eslintrc.js).

Gerrit JavaScript code uses ES6 modules and doesn't use goog.module files.

Additionally to the rules above, Gerrit frontend uses the following rules (some of them have automated checks,
some doesn't):

 
- No default export
- Use destructuring imports only
- Do not create static container classes
- Use classes and services for storing and manipulating global state

## No default export
Use only named exports in ES6 modules and do not use default export.
Default exports assumes that developer must assign some name everytime when the default export is imported.
This makes refactoring and renaming files much more harder - to keep code constistent, developer usually
must manually updates all imports to match importing name and the filename. With named imports this task can
be done by IDE. 

**Example:**
```JavaScript
//some-element.js
export const someElement = ...;
```

**Disallowed:**:
```JavaScript
// some-element.js
export default someElement;
```

# Use destructuring imports only


  




