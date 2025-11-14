/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../api/embed';
import '../scripts/bundled-polymer';
import './diff/gr-diff/gr-diff';
import './gr-textarea';
import './diff/gr-diff-cursor/gr-diff-cursor';
import {TokenHighlightLayer} from './diff/gr-diff-builder/token-highlight-layer';
import {GrDiffCursor} from './diff/gr-diff-cursor/gr-diff-cursor';
import {GrAnnotationImpl as GrAnnotation} from './diff/gr-diff-highlight/gr-annotation';
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
