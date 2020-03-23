/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {GrDiffBuilder} from './gr-diff-builder.js';

(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffBuilderBinary) { return; }

  /** @constructor */
  function GrDiffBuilderBinary(diff, prefs, outputEl) {
    GrDiffBuilder.call(this, diff, prefs, outputEl);
  }

  GrDiffBuilderBinary.prototype = Object.create(GrDiffBuilder.prototype);
  GrDiffBuilderBinary.prototype.constructor = GrDiffBuilderBinary;

  // This method definition is a no-op to satisfy the parent type.
  GrDiffBuilderBinary.prototype.addColumns = function(outputEl, fontSize) {};

  GrDiffBuilderBinary.prototype.buildSectionElement = function() {
    const section = this._createElement('tbody', 'binary-diff');
    const row = this._createElement('tr');
    const cell = this._createElement('td');
    const label = this._createElement('label');
    label.textContent = 'Difference in binary files';
    cell.appendChild(label);
    row.appendChild(cell);
    section.appendChild(row);
    return section;
  };

  window.GrDiffBuilderBinary = GrDiffBuilderBinary;
})(window);
