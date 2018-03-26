/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  /**
   * Used to create a context for GrAnnotationActionsInterface.
   * @param {HTMLElement} el The DIV.contentText element to apply the
   *     annotation to using annotateRange.
   * @param {GrDiffLine} line The line object.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   */
  function GrAnnotationActionsContext(el, line, path, changeNum, patchNum) {
    this._el = el;

    this.line = line;
    this.path = path;
    this.changeNum = parseInt(changeNum);
    this.patchNum = parseInt(patchNum);
  }

  /**
   * Method to add annotations to a line.
   * @param {Number} start The line number where the update starts.
   * @param {Number} end The line number where the update ends.
   * @param {String} cssClass The name of a CSS class created using Gerrit.css.
   * @param {String} side The side of the update. ('left' or 'right')
   */
  GrAnnotationActionsContext.prototype.annotateRange = function(
      start, end, cssClass, side) {
    if (this._el.getAttribute('data-side') == side) {
      GrAnnotation.annotateElement(this._el, start, end, cssClass);
    }
  };

  window.GrAnnotationActionsContext = GrAnnotationActionsContext;
})(window);
