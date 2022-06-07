/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
