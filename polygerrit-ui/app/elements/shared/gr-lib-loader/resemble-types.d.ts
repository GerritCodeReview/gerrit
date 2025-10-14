/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Resemble from 'resemblejs';

// @types/resemblejs does not expose a global variable resemble and instead
// exposes the namespace Resemble. Because Resemble.js should remain an
// optional dependency, we define a global variable in a separate .d.ts file;
// otherwise, the TS compiler tries to import the JS library too and fails.
declare global {
  interface Window {
    resemble?: typeof Resemble;
  }
}
