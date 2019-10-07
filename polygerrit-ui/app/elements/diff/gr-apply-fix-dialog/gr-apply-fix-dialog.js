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
  Polymer({
    is: 'gr-apply-fix-dialog',
    _legacyUndefinedCheck: true,
    properties: {
      prefs: Array,
      change: Object,
      changeNum: String,
      patchNum: Number,
      robotId: String,
      currentFix: Object,
      currentPreviews: {type: Array, value: () => []},
      fixSuggestions: Array, // all available fixes from single comment
    },
    behaviors: [
      Gerrit.FireBehavior,
    ],
    open(e) {
      this.patchNum = e.detail.patchNum;
      const fixSuggestions = e.detail.comment.fix_suggestions;
      this.fixSuggestions = fixSuggestions;
      this.robotId = e.detail.comment.robot_id;
      // select and fetch preview of first fix
      if (fixSuggestions != null && fixSuggestions[0] &&
        this.changeNum != null && this.patchNum != null) {
        const promises = [];
        promises.push(
            this.showSelectedFixSuggestion(this.changeNum, this.patchNum,
                fixSuggestions[0]),
            this.$.applyFixOverlay.open()
        );
        return Promise.all(promises)
            .then(() => {
              // ensures gr-overlay repositions overlay in center
              this.$.applyFixOverlay.fire('iron-resize');
            });
      }
      return Promise.resolve();
    },
    showSelectedFixSuggestion(changeNum, patchNum, fixSuggestion) {
      this.currentFix = fixSuggestion;
      return this.fetchFixPreview(changeNum, patchNum, fixSuggestion.fix_id);
    },
    fetchFixPreview(changeNum, patchNum, fixId) {
      return this.$.restAPI
          .getRobotCommentFixPreview(changeNum, patchNum, fixId)
          .then(res => {
            if (res != null) {
              const previews = Object.keys(res).map(key =>
                ({filepath: key, preview: res[key]}));
              this.currentPreviews = previews;
            }
          }).catch(err => {
            this.onCancel(undefined);
            this.dispatchEvent(new CustomEvent('show-error', {
              bubbles: true,
              composed: true,
              detail: {message: `Error generating fix preview: ${err}`},
            }));
          });
    },
    overridePartialPrefs(prefs) {
      // generate a smaller gr-diff than fullscreen for dialog
      return Object.assign({}, prefs, {line_length: 50});
    },
    onCancel(e) {
      if (e) {
        e.stopPropagation();
      }
      // reset preview
      this.currentFix = {};
      this.currentPreviews = [];
      this.$.applyFixOverlay.close();
    },
    handleApplyFix(e) {
      if (e) {
        e.stopPropagation();
      }
      if (this.changeNum == null || this.patchNum == null ||
        this.currentFix == null || this.currentFix.fix_id == null) {
        return;
      }
      return this.$.restAPI.applyFixSuggestion(this.changeNum, this.patchNum,
          this.currentFix.fix_id).then(res => {
            Gerrit.Nav.navigateToChange(this.change, 'edit', this.patchNum);
            // reset and close dialog
            this.onCancel(undefined);
          }).catch(err => {
            console.error(err);
            this.dispatchEvent(new CustomEvent('show-error', {
              bubbles: true,
              composed: true,
              detail: {message: `Error applying fix suggestion: ${err}`},
            }));
          });
    },
    getFixDescription(currentFix) {
      if (currentFix != null && currentFix.description) {
        return currentFix.description;
      }
      return '';
    },
  });
})();
