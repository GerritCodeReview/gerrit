/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

  Gerrit.CoverageType = {
    /**
     * start_character and end_character of the range will be ignored for this
     * type.
     */
    COVERED: 'COVERED',
    /**
     * start_character and end_character of the range will be ignored for this
     * type.
     */
    NOT_COVERED: 'NOT_COVERED',
    PARTIALLY_COVERED: 'PARTIALLY_COVERED',
    /**
     * You don't have to use this. If there is no coverage information for a
     * range, then it implicitly means NOT_INSTRUMENTED.
     */
    NOT_INSTRUMENTED: 'NOT_INSTRUMENTED',
  };

  /**
   * @typedef {{
   *   side: string,
   *   type: Gerrit.CoverageType,
   *   code_range: Gerrit.Range,
   * }}
   */
  Gerrit.CoverageRange;

  Polymer({
    is: 'gr-coverage-layer',

    properties: {
      /**
       * Must be sorted by side (all 'left' ranges before 'right' ranges) and
       * then by code_range.start_line for each side.
       *
       * @type {!Array<!Gerrit.CoverageRange>}
       */
      coverageRanges: Array,

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
        default: 0,
      },
      _index: {
        type: Number,
        default: 0,
      },
    },

    /**
     * Layer method to add annotations to a line.
     *
     * @param {!HTMLElement} el The DIV.contentText element to apply the
     *     annotation to.
     * @param {!HTMLElement} lineNumberEl
     * @param {!Object} line The line object. (GrDiffLine)
     */
    annotate(el, lineNumberEl, line) {
      const elementLineNumber = parseInt(
          lineNumberEl.getAttribute('data-value'), 10);
      if (!elementLineNumber || elementLineNumber < 1) return;
      const elementIsLeft = lineNumberEl.classList.contains('left');

      // We expect only one pass of annotate() calls for each gr-coverage-layer
      // instance, so we don't really expect this to happen, but if it does
      // (maybe all layers are refreshed?), then we have to reset and iterate
      // over all ranges again.
      if (elementLineNumber < this._lineNumber) {
        this._index = 0;
      }
      this._lineNumber = elementLineNumber;

      // We simply loop through all the coverage ranges until we find one that
      // matches the line number.
      while (this._index < coverageRanges.length) {
        const coverageRange = coverageRanges[this._index];
        const coverageIsLeft = coverageRange.side === 'left';

        // If the side does not match, or the line number has moved past the
        // current coverage range, then try the next coverage range.
        if (coverageIsLeft !== elementIsLeft || lineNumber >
            coverageRange.code_range.end_line) {
          this._index++;
          continue;
        }

        // If the line number has not reached the next coverage range (and the
        // range before also did not match), then this line has not been
        // instrumented. Nothing to do for this line.
        if (lineNumber < coverageRange.code_range.start_line) {
          return;
        }

        // The line number is within the current coverage range. Style it!
        GrAnnotation.wrapInHighlight(lineNumberEl, coverageRange.type);
        return;
      }
    },
  });
})();
