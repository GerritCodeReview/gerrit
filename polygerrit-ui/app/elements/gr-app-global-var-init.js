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

/**
 * @fileoverview This file is a backwards-compatibility shim.
 * Before Polygerrit converted to ES Modules, it exposes some variables out onto
 * the global namespace. Plugins can depend on these variables and we must
 * expose these variables until plugins switch to direct import from polygerrit.
 */

import {GrDisplayNameUtils} from '../scripts/gr-display-name-utils/gr-display-name-utils.js';
import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation.js';
import {GrAttributeHelper} from './plugins/gr-attribute-helper/gr-attribute-helper.js';
import {GrDiffLine} from './diff/gr-diff/gr-diff-line.js';
import {GrDiffGroup} from './diff/gr-diff/gr-diff-group.js';
import {GrDiffBuilder} from './diff/gr-diff-builder/gr-diff-builder.js';
import {GrDiffBuilderSideBySide} from './diff/gr-diff-builder/gr-diff-builder-side-by-side.js';
import {GrDiffBuilderImage} from './diff/gr-diff-builder/gr-diff-builder-image.js';

export function initGlobalVariables() {
  window.GrDisplayNameUtils = GrDisplayNameUtils;
  window.GrAnnotation = GrAnnotation;
  window.GrAttributeHelper = GrAttributeHelper;
  window.GrDiffLine = GrDiffLine;
  window.GrDiffGroup = GrDiffGroup;
  window.GrDiffBuilder = GrDiffBuilder;
  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
  window.GrDiffBuilderImage = GrDiffBuilderImage;
}
