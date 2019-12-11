/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
(function() {
  'use strict';

  const TOOLTIP_MAP = new Map([
    [Gerrit.CoverageType.COVERED, 'Covered by tests.'],
    [Gerrit.CoverageType.NOT_COVERED, 'Not covered by tests.'],
    [Gerrit.CoverageType.PARTIALLY_COVERED, 'Partially covered by tests.'],
    [Gerrit.CoverageType.NOT_INSTRUMENTED, 'Not instrumented by any tests.'],
  ]);

  class GrCoverageLayer extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-coverage-layer'; }

    static get properties() {
      return {
      /**
       * Must be sorted by code_range.start_line.
       * Must only contain ranges that match the side.
       *
       * @type {!Array<!Gerrit.CoverageRange>}
       */
        coverageRanges: Array,
        side: String,

        /**
         * We keep track of the line number from the previous annotate() call,
         * and also of the index of the coverage range that had matched.
         * annotate() calls are coming in with increasing line numbers and
         * coverage ranges are sorted by line number. So this is a very simple
         * and efficient way for finding the coverage range that matches a given
         * line number.
         */
        _lineNumber: {
          type: Number,
          value: 0,
        },
        _index: {
          type: Number,
          value: 0,
        },
      };
    }

    /**
     * Layer method to add annotations to a line.
     *
     * @param {!HTMLElement} el Not used for this layer.
     * @param {!HTMLElement} lineNumberEl The <td> element with the line number.
     * @param {!Object} line Not used for this layer.
     */
    annotate(el, lineNumberEl, line) {
      if (!lineNumberEl || !lineNumberEl.classList.contains(this.side)) {
        return;
      }
      const elementLineNumber = parseInt(
          lineNumberEl.getAttribute('data-value'), 10);
      if (!elementLineNumber || elementLineNumber < 1) return;

      // If the line number is smaller than before, then we have to reset our
      // algorithm and start searching the coverage ranges from the beginning.
      // That happens for example when you expand diff sections.
      if (elementLineNumber < this._lineNumber) {
        this._index = 0;
      }
      this._lineNumber = elementLineNumber;

      // We simply loop through all the coverage ranges until we find one that
      // matches the line number.
      while (this._index < this.coverageRanges.length) {
        const coverageRange = this.coverageRanges[this._index];

        // If the line number has moved past the current coverage range, then
        // try the next coverage range.
        if (this._lineNumber > coverageRange.code_range.end_line) {
          this._index++;
          continue;
        }

        // If the line number has not reached the next coverage range (and the
        // range before also did not match), then this line has not been
        // instrumented. Nothing to do for this line.
        if (this._lineNumber < coverageRange.code_range.start_line) {
          return;
        }

        // The line number is within the current coverage range. Style it!
        lineNumberEl.classList.add(coverageRange.type);
        lineNumberEl.title = TOOLTIP_MAP.get(coverageRange.type);
        return;
      }
    }
  }

  customElements.define(GrCoverageLayer.is, GrCoverageLayer);
})();
