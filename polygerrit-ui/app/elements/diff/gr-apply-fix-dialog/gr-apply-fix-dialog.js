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
    properties: {
      prefs: Array,
      change: Object,
      changeNum: String,
      _patchNum: Number,
      _robotId: String,
      _currentFix: Object,
      _currentPreviews: {type: Array, value: () => []},
      _fixSuggestions: Array, // all available fixes from single comment
    },
    behaviors: [
      Gerrit.FireBehavior,
    ],
    open(e) {
      this._patchNum = e.detail.patchNum;
      const fixSuggestions = e.detail.comment.fix_suggestions;
      this._fixSuggestions = fixSuggestions;
      this._robotId = e.detail.comment.robot_id;
      // select and fetch preview of first fix
      if (fixSuggestions != null && fixSuggestions[0]) {
        const promises = [];
        promises.push(
            this._showSelectedFixSuggestion(fixSuggestions[0]),
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
    _showSelectedFixSuggestion(fixSuggestion) {
      this._currentFix = fixSuggestion;
      return this._fetchFixPreview(fixSuggestion.fix_id);
    },
    _fetchFixPreview(fixId) {
      return this.$.restAPI
          .getRobotCommentFixPreview(this.changeNum, this._patchNum, fixId)
          .then(res => {
            if (res != null) {
              const previews = Object.keys(res).map(key =>
                ({filepath: key, preview: res[key]}));
              this._currentPreviews = previews;
            }
          }).catch(err => {
            this._close();
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
      this._close();
    },
    _close() {
      this._currentFix = {};
      this._currentPreviews = [];

      this.dispatchEvent(new CustomEvent('close-fix-preview', {
        bubbles: true,
        composed: true,
      }));
      this.$.applyFixOverlay.close();
    },
    _handleApplyFix(e) {
      if (e) {
        e.stopPropagation();
      }
      if (this._currentFix == null || this._currentFix.fix_id == null) {
        return;
      }
      return this.$.restAPI.applyFixSuggestion(this.changeNum, this._patchNum,
          this._currentFix.fix_id).then(res => {
            Gerrit.Nav.navigateToChange(this.change, 'edit', this._patchNum);
            // reset and close dialog
            this._close();
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
