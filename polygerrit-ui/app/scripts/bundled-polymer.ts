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

// This file is a replacement for the
// polymer-bridges/polymer/polymer.html file. The polymer.html file loads
// other scripts to setup different global variables. Because plugins
// expects that Polymer is available we must setup all Polymer global
// variables
//
// The bundled-polymer.js imports all scripts in the same order as the
// polymer.html does and must be imported in all es6-modules instead
// of the polymer.html file.

import './js/bundled-polymer-bridges';

import {importHref} from './import-href';

window.Polymer = window.Polymer || {};
window.Polymer.importHref = importHref;
