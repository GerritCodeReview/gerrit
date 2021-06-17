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

// The @types/reseblejs doesn't expose a global variable resemble and instead
// it exposes the namespace Resemble. Because we don't want to import
// javascript resemblejs library, we should define a global variable in a
// separate .d.ts file; otherwise, the TS tries to import js library too
// and fails.
declare global {
  interface Window {
    resemble?: typeof Resemble;
  }
}
