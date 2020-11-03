/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

// Mark the file as a module. Otherwise typescript assumes this is a script
// and doesn't allow "declare global".
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

declare global {
  interface Window {
    sourceMapSupport: {
      install(): void;
    };
  }
}

// The karma.conf.js file loads required module before any other modules
// The source-map-support.js can't be imported with import ... statement
window.sourceMapSupport.install();
