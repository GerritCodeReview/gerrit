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
   *   codeRange: Gerrit.Range,
   * }}
   */
  Gerrit.CoverageRange;

  Polymer({
    is: 'gr-coverage-layer',

    properties: {
      /**
       * Must be sorted by range.
       *
       * @type {!Array<!Gerrit.CoverageRange>}
       */
      coverageRanges: Array,
    },

    observers: [
      '_handleCoverageRangesChange(coverageRanges.*)',
    ],

    /**
     * Layer method to add annotations to a line.
     * @param {!HTMLElement} el The DIV.contentText element to apply the
     *     annotation to.
     * @param {!HTMLElement} lineNumberEl
     * @param {!Object} line The line object. (GrDiffLine)
     */
    annotate(el, lineNumberEl, line) {
      // TODO(brohlfs): Implement properly! This is not how it works. :-)
      const relevantRanges = _getRangesForLine(line,
          el.getAttribute('data-side'));
      for (const coverageRange of relevantRanges) {
        GrAnnotation.annotateElement(lineNumberEl, 0, 1000, coverageRange.type);
      }
    },

    _handleCoverageRangesChange(record) {
      // TODO(brohlfs): Implement!
    },

    _getRangesForLine(line, side) {
      // TODO(brohlfs): Implement!
      return [];
    },
  });
})();
