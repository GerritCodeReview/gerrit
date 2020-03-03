/**
@license
Copyright (C) 2019 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import "../../../scripts/bundled-polymer.js";

import '@polymer/iron-icon/iron-icon.js';
import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-diff/gr-diff.js';
import { Polymer } from '@polymer/polymer/lib/legacy/polymer-fn.js';
import { html } from '@polymer/polymer/lib/utils/html-tag.js';
Polymer({
  _template: html`
    <style include="shared-styles">
      gr-diff {
        --content-width: 90vw;
      }
      .diffContainer {
        padding: var(--spacing-l) 0;
        border-bottom: 1px solid var(--border-color);
      }
      .file-name {
        display: block;
        padding: var(--spacing-s) var(--spacing-l);
        background-color: var(--background-color-secondary);
        border-bottom: 1px solid var(--border-color);
      }
      .fixActions {
        display: flex;
        justify-content: flex-end;
      }
      gr-button {
        margin-left: var(--spacing-m);
      }
      .fix-picker {
        display: flex;
        align-items: center;
        margin-right: var(--spacing-l);
      }
    </style>
    <gr-overlay id="applyFixOverlay" with-backdrop="">
      <gr-dialog id="applyFixDialog" on-confirm="_handleApplyFix" confirm-label="[[_getApplyFixButtonLabel(_isApplyFixLoading)]]" disabled="[[_isApplyFixLoading]]" on-cancel="onCancel">
        <div slot="header">[[_robotId]] - [[getFixDescription(_currentFix)]]</div>
        <div slot="main">
          <template is="dom-repeat" items="[[_currentPreviews]]">
            <div class="file-name">
              <span>[[item.filepath]]</span>
            </div>
            <div class="diffContainer">
              <gr-diff prefs="[[overridePartialPrefs(prefs)]]" change-num="[[changeNum]]" path="[[item.filepath]]" diff="[[item.preview]]"></gr-diff>
            </div>
          </template>
        </div>
        <div slot="footer" class="fix-picker" hidden\$="[[hasSingleFix(_fixSuggestions)]]">
          <span>Suggested fix [[addOneTo(_selectedFixIdx)]] of [[_fixSuggestions.length]]</span>
          <gr-button id="prevFix" on-click="_onPrevFixClick" disabled\$="[[_noPrevFix(_selectedFixIdx)]]">
            <iron-icon icon="gr-icons:chevron-left"></iron-icon>
          </gr-button>
          <gr-button id="nextFix" on-click="_onNextFixClick" disabled\$="[[_noNextFix(_selectedFixIdx, _fixSuggestions)]]">
            <iron-icon icon="gr-icons:chevron-right"></iron-icon>
          </gr-button>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-apply-fix-dialog',

  properties: {
    // Diff rendering preference API response.
    prefs: Array,
    // ChangeInfo API response object.
    change: Object,
    changeNum: String,
    _patchNum: Number,
    // robot ID associated with a robot comment.
    _robotId: String,
    // Selected FixSuggestionInfo entity from robot comment API response.
    _currentFix: Object,
    // Flattened /preview API response DiffInfo map object.
    _currentPreviews: {type: Array, value: () => []},
    // FixSuggestionInfo entities from robot comment API response.
    _fixSuggestions: Array,
    _isApplyFixLoading: {
      type: Boolean,
      value: false,
    },
    // Index of currently showing suggested fix.
    _selectedFixIdx: Number,
  },

  behaviors: [
    Gerrit.FireBehavior,
  ],

  /**
   * Given robot comment CustomEvent objevt, fetch diffs associated
   * with first robot comment suggested fix and open dialog.
   *
   * @param {*} e CustomEvent to be passed from gr-comment with
   * robot comment detail.
   * @return {Promise<undefined>} Promise that resolves either when all
   * preview diffs are fetched or no fix suggestions in custom event detail.
   */
  open(e) {
    this._patchNum = e.detail.patchNum;
    this._fixSuggestions = e.detail.comment.fix_suggestions;
    this._robotId = e.detail.comment.robot_id;
    if (this._fixSuggestions == null || this._fixSuggestions.length == 0) {
      return Promise.resolve();
    }
    this._selectedFixIdx = 0;
    const promises = [];
    promises.push(
        this._showSelectedFixSuggestion(this._fixSuggestions[0]),
        this.$.applyFixOverlay.open()
    );
    return Promise.all(promises)
        .then(() => {
          // ensures gr-overlay repositions overlay in center
          this.$.applyFixOverlay.fire('iron-resize');
        });
  },

  attached() {
    this.refitOverlay = () => {
      // re-center the dialog as content changed
      this.$.applyFixOverlay.fire('iron-resize');
    };
    this.addEventListener('diff-context-expanded', this.refitOverlay);
  },

  detached() {
    this.removeEventListener('diff-context-expanded', this.refitOverlay);
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
            const previews = Object.keys(res).map(key => {
              return {filepath: key, preview: res[key]};
            });
            this._currentPreviews = previews;
          }
        })
        .catch(err => {
          this._close();
          throw err;
        });
  },

  hasSingleFix(_fixSuggestions) {
    return (_fixSuggestions || {}).length === 1;
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

  addOneTo(_selectedFixIdx) {
    return _selectedFixIdx + 1;
  },

  _onPrevFixClick(e) {
    if (e) e.stopPropagation();
    if (this._selectedFixIdx >= 1 && this._fixSuggestions != null) {
      this._selectedFixIdx -= 1;
      return this._showSelectedFixSuggestion(
          this._fixSuggestions[this._selectedFixIdx]);
    }
  },

  _onNextFixClick(e) {
    if (e) e.stopPropagation();
    if (this._fixSuggestions &&
      this._selectedFixIdx < this._fixSuggestions.length) {
      this._selectedFixIdx += 1;
      return this._showSelectedFixSuggestion(
          this._fixSuggestions[this._selectedFixIdx]);
    }
  },

  _noPrevFix(_selectedFixIdx) {
    return _selectedFixIdx === 0;
  },

  _noNextFix(_selectedFixIdx, fixSuggestions) {
    if (fixSuggestions == null) return true;
    return _selectedFixIdx === fixSuggestions.length - 1;
  },

  _close() {
    this._currentFix = {};
    this._currentPreviews = [];
    this._isApplyFixLoading = false;

    this.dispatchEvent(new CustomEvent('close-fix-preview', {
      bubbles: true,
      composed: true,
    }));
    this.$.applyFixOverlay.close();
  },

  _getApplyFixButtonLabel(isLoading) {
    return isLoading ? 'Saving...' : 'Apply Fix';
  },

  _handleApplyFix(e) {
    if (e) {
      e.stopPropagation();
    }
    if (this._currentFix == null || this._currentFix.fix_id == null) {
      return;
    }
    this._isApplyFixLoading = true;
    return this.$.restAPI
        .applyFixSuggestion(
            this.changeNum, this._patchNum, this._currentFix.fix_id
        )
        .then(res => {
          if (res && res.ok) {
            Gerrit.Nav.navigateToChange(this.change, 'edit', this._patchNum);
            this._close();
          }
          this._isApplyFixLoading = false;
        });
  },

  getFixDescription(currentFix) {
    return currentFix != null && currentFix.description ?
      currentFix.description : '';
  }
});
