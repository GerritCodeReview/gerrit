# Eslint rules for polygerrit
This directory contains custom eslint rules for polygerrit.

## ts-imports-js
This rule must be used only for `.ts` files.
The rule ensures that:
* All import paths either a relative paths or module imports.
```typescript
// Correct imports
import './file1'; // relative path
import '../abc/file2'; // relative path
import 'module_name/xyz'; // import from the module_name

// Incorrect imports
import '/usr/home/file3'; // absolute path
```
* All *relative* import paths has a short form (i.e. don't include extension):
```typescript
// Correct imports
import './file1'; // relative path without extension
import data from 'module_name/file2.json'; // file in a module, can be anything

// Incorrect imports
import './file1.js'; // relative path with extension
```

* Imported `.js` and `.d.ts` files both exists (only for a relative import path):

Example:
```
polygerrit-ui/app
 |- ex.ts
 |- abc
     |- correct_ts.ts
     |- correct_js.js
     |- correct_js.d.ts
     |- incorrect_1.js
     |- incorrect_2.d.ts
```
```typescript
// The ex.ts file:
// Correct imports
import {x} from './abc/correct_js'; // correct_js.js and correct_js.d.ts exist
import {x} from './abc/correct_ts'; // import from .ts - d.ts is not required

// Incorrect imports
import {x} from './abc/incorrect_1'; // incorrect_1.d.ts doesn't exist
import {x} from './abc/incorrect_2'; // incorrect_2.js doesn't exist
```

To fix the last two imports 2 files must be added: `incorrect_1.d.ts` and
`incorrect_2.js`.

## goog-module-id
Enforce correct usage of goog.declareModuleId:
* The goog.declareModuleId must be used only in `.js` files which have
associated `.d.ts` files.
* The module name is correct. The correct module name is constructed from the
file path using the folowing rules
rules:
  1. Get part of the path after the '/polygerrit-ui/app/':
    `/usr/home/gerrit/polygerrit-ui/app/elements/shared/x/y.js` ->
    `elements/shared/x/y.js`
  2. Discard `.js` extension and replace all `/` with `.`:
    `elements/shared/x/y.js` -> `elements.shared.x.y`
  3. Add `polygerrit.` prefix:
    `elements.shared.x.y` -> `polygerrit.elements.shared.x.y`
    The last string is a module name.

Example:
```javascript
// polygerrit-ui/app/elements/shared/x/y.js
goog.declareModuleId('polygerrit.elements.shared.x.y');
```
