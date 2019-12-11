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
   *
   * @param {HTMLElement} contentEl The DIV.contentText element of the line
   *     content to apply the annotation to using annotateRange.
   * @param {HTMLElement} lineNumberEl The TD element of the line number to
   *     apply the annotation to using annotateLineNumber.
   * @param {GrDiffLine} line The line object.
   * @param {string} path The file path (eg: /COMMIT_MSG').
   * @param {string} changeNum The Gerrit change number.
   * @param {string} patchNum The Gerrit patch number.
   */
  function GrAnnotationActionsContext(
      contentEl, lineNumberEl, line, path, changeNum, patchNum) {
    this._contentEl = contentEl;
    this._lineNumberEl = lineNumberEl;

    this.line = line;
    this.path = path;
    this.changeNum = parseInt(changeNum);
    this.patchNum = parseInt(patchNum);
  }

  /**
   * Method to add annotations to a content line.
   *
   * @param {number} offset The char offset where the update starts.
   * @param {number} length The number of chars that the update covers.
   * @param {GrStyleObject} styleObject The style object for the range.
   * @param {string} side The side of the update. ('left' or 'right')
   */
  GrAnnotationActionsContext.prototype.annotateRange = function(
      offset, length, styleObject, side) {
    if (this._contentEl && this._contentEl.getAttribute('data-side') == side) {
      GrAnnotation.annotateElement(this._contentEl, offset, length,
          styleObject.getClassName(this._contentEl));
    }
  };

  /**
   * Method to add a CSS class to the line number TD element.
   *
   * @param {GrStyleObject} styleObject The style object for the range.
   * @param {string} side The side of the update. ('left' or 'right')
   */
  GrAnnotationActionsContext.prototype.annotateLineNumber = function(
      styleObject, side) {
    if (this._lineNumberEl && this._lineNumberEl.classList.contains(side)) {
      styleObject.apply(this._lineNumberEl);
    }
  };

  window.GrAnnotationActionsContext = GrAnnotationActionsContext;
})(window);
