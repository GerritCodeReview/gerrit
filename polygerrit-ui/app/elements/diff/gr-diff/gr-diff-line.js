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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrDiffLine) { return; }

  /**
   * @param {GrDiffLine.Type} type
   * @param {number|string=} opt_beforeLine
   * @param {number|string=} opt_afterLine
   */
  function GrDiffLine(type, opt_beforeLine, opt_afterLine) {
    this.type = type;

    /** @type {number|string} */
    this.beforeNumber = opt_beforeLine || 0;

    /** @type {number|string} */
    this.afterNumber = opt_afterLine || 0;

    /** @type {boolean} */
    this.hasIntralineInfo = false;

    /** @type {!Array<GrDiffLine.Highlights>} */
    this.highlights = [];

    /** @type {?Array<Object>} ?Array<!GrDiffGroup> */
    this.contextGroups = null;

    this.text = '';
  }

  GrDiffLine.Type = {
    ADD: 'add',
    BOTH: 'both',
    BLANK: 'blank',
    CONTEXT_CONTROL: 'contextControl',
    REMOVE: 'remove',
  };

  /**
   * A line highlight object consists of three fields:
   * - contentIndex: The index of the chunk `content` field (the line
   *   being referred to).
   * - startIndex: Index of the character where the highlight should begin.
   * - endIndex: (optional) Index of the character where the highlight should
   *   end. If omitted, the highlight is meant to be a continuation onto the
   *   next line.
   *
   * @typedef {{
   *  contentIndex: number,
   *  startIndex: number,
   *  endIndex: number
   * }}
   */
  GrDiffLine.Highlights;

  GrDiffLine.FILE = 'FILE';

  GrDiffLine.BLANK_LINE = new GrDiffLine(GrDiffLine.Type.BLANK);

  window.GrDiffLine = GrDiffLine;
})(window);
