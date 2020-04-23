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

// Note: we do not install global Polymer variables here.
// The usage of gr-diff suggests, that we shouldn't pollute global
// namespace. If an application uses Polymer, the app have to import it
// and must not expect that gr-diff setup global Polymer methods.
window.Gerrit = window.Gerrit || {};
import '../elements/diff/gr-diff/gr-diff.js';
import '../elements/diff/gr-diff-cursor/gr-diff-cursor.js';
import {initDiffAppContext} from './app-context-init.js';
import {GrDiffLine} from '../elements/diff/gr-diff/gr-diff-line.js';
import {GrAnnotation} from '../elements/diff/gr-diff-highlight/gr-annotation.js';

// Setup appContext for diff.
// TODO (dmfilippov): find a better solution
initDiffAppContext();
// Setup global variables for existing usages of this component
window.GrDiffLine = GrDiffLine;
window.GrAnnotation = GrAnnotation;
