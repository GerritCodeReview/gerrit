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

// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
// Because gr-diff.js is a shared component, it shouldn' pollute global
// variables. If an application wants to use Polymer global variable -
// the app must assign/import it and do not rely on the Polymer variable
// exposed by shared gr-diff component.
import '../api/embed';
import '../scripts/bundled-polymer';
import './diff/gr-diff/gr-diff';
import './diff/gr-diff-cursor/gr-diff-cursor';
import {TokenHighlightLayer} from './diff/gr-diff-builder/token-highlight-layer';
import {GrDiffCursor} from './diff/gr-diff-cursor/gr-diff-cursor';
import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation';
import {createDiffAppContext} from './gr-diff-app-context-init';
import {injectAppContext} from '../services/app-context';

// Setup appContext for diff.
// TODO (dmfilippov): find a better solution
injectAppContext(createDiffAppContext());
// Setup global variables for existing usages of this component
window.grdiff = {
  GrAnnotation,
  GrDiffCursor,
  TokenHighlightLayer,
};

// TODO(oler): Remove when clients have adjusted to namespaced globals above
window.GrAnnotation = GrAnnotation;
