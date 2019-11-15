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

  /**
    * @appliesMixin Gerrit.BaseUrlMixin
    * @appliesMixin Gerrit.PathListMixin
    * @appliesMixin Gerrit.URLEncodingMixin
    */
  class GrCommentList extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.PathListBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-comment-list'; }

    static get properties() {
      return {
        changeNum: Number,
        comments: Object,
        patchNum: Number,
        projectName: String,
        /** @type {?} */
        projectConfig: Object,
      };
    }

    _computeFilesFromComments(comments) {
      const arr = Object.keys(comments || {});
      return arr.sort(this.specialFilePathCompare);
    }

    _isOnParent(comment) {
      return comment.side === 'PARENT';
    }

    _computeDiffLineURL(file, changeNum, patchNum, comment) {
      const basePatchNum = comment.hasOwnProperty('parent') ?
        -comment.parent : null;
      return Gerrit.Nav.getUrlForDiffById(this.changeNum, this.projectName,
          file, patchNum, basePatchNum, comment.line,
          this._isOnParent(comment));
    }

    _computeCommentsForFile(comments, file) {
      // Changes are not picked up by the dom-repeat due to the array instance
      // identity not changing even when it has elements added/removed from it.
      return (comments[file] || []).slice();
    }

    _computePatchDisplayName(comment) {
      if (this._isOnParent(comment)) {
        return 'Base, ';
      }
      if (comment.patch_set != this.patchNum) {
        return `PS${comment.patch_set}, `;
      }
      return '';
    }
  }

  customElements.define(GrCommentList.is, GrCommentList);
})();
