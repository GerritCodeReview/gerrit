/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// TODO(dmfilippov): remove bundled-polymer.js imports when the following issue
// https://github.com/Polymer/polymer-resin/issues/9 is resolved.
// Because gr-diff.js is a shared component, it shouldn' pollute global
// variables. If an application wants to use Polymer global variable -
// the app must assign/import it and do not rely on the Polymer variable
// exposed by shared gr-diff component.
import '../api/embed';
import '../scripts/bundled-polymer';
import './diff-old/gr-diff/gr-diff';
import './diff-old/gr-diff-cursor/gr-diff-cursor';
import {TokenHighlightLayer} from './diff/gr-diff-builder/token-highlight-layer';
import {GrDiffCursor} from './diff-old/gr-diff-cursor/gr-diff-cursor';
import {GrAnnotation} from './diff-old/gr-diff-highlight/gr-annotation';

// Setup global variables for existing usages of this component
window.grdiff = {
  GrAnnotation,
  GrDiffCursor,
  TokenHighlightLayer,
};
