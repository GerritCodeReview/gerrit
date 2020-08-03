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

window.Gerrit = window.Gerrit || {};
// We need to use goog.declareModuleId internally in google for TS-imports-JS
// case. To avoid errors when goog is not available, the empty implementation is
// added.
window.goog = window.goog || {declareModuleId(name) {}};
// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
// Because gr-diff.js is a shared component, it shouldn' pollute global
// variables. If an application wants to use Polymer global variable -
// the app must assign/import it and do not rely on the Polymer variable
// exposed by shared gr-diff component.
import '../scripts/bundled-polymer.js';
import '../elements/diff/gr-diff/gr-diff.js';
import '../elements/diff/gr-diff-cursor/gr-diff-cursor.js';
import {initDiffAppContext} from './gr-diff-app-context-init.js';
import {GrDiffLine, GrDiffLineType} from '../elements/diff/gr-diff/gr-diff-line.js';
import {GrAnnotation} from '../elements/diff/gr-diff-highlight/gr-annotation.js';

// Setup appContext for diff.
// TODO (dmfilippov): find a better solution
initDiffAppContext();
// Setup global variables for existing usages of this component
window.GrDiffLine = GrDiffLine;
window.GrDiffLineType = GrDiffLineType;
window.GrAnnotation = GrAnnotation;
