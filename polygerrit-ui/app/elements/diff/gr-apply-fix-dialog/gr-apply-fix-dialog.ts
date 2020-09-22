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
import '@polymer/iron-icon/iron-icon';
import '../../../styles/shared-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-diff/gr-diff';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-apply-fix-dialog_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {ParsedChangeInfo} from '../../shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {FixSuggestionInfo, PatchSetNum, RobotId} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

export interface GrApplyFixDialog {
  $: {
    applyFixOverlay: GrOverlay;
  }
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  prefs?: unknown;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  changeNum?: string;

  @property({type: Number})
  _patchNum?: PatchSetNum;

  @property({type: String})
  _robotId?: RobotId;

  @property({type: Object})
  _currentFix?: FixSuggestionInfo;

  @property({type: Array})
  _currentPreviews: unknown = (() => [])();

  @property({type: Array})
  _fixSuggestions?: FixSuggestionInfo[];

  @property({type: Boolean})
  _isApplyFixLoading = false;

  @property({type: Number})
  _selectedFixIdx = 0;

  @property({
    type: Boolean,
    computed:
      '_computeDisableApplyFixButton(_isApplyFixLoading, change, ' +
      '_patchNum)',
  })
  _disableApplyFixButton?: boolean;

  private refitOverlay?: () => void;
  /**
   * Given robot comment CustomEvent objevt, fetch diffs associated
   * with first robot comment suggested fix and open dialog.
   *
   * @param e CustomEvent to be passed from gr-comment with
   * robot comment detail.
   * @return Promise that resolves either when all
   * preview diffs are fetched or no fix suggestions in custom event detail.
   */
  open(e) {
    this._patchNum = e.detail.patchNum;
    this._fixSuggestions = e.detail.comment.fix_suggestions;
    this._robotId = e.detail.comment.robot_id;
    if (!this._fixSuggestions || this._fixSuggestions.length == 0) {
      return Promise.resolve();
    }
    this._selectedFixIdx = 0;
    const promises = [];
    promises.push(
      this._showSelectedFixSuggestion(this._fixSuggestions[0]),
      this.$.applyFixOverlay.open()
    );
    return Promise.all(promises).then(() => {
      // ensures gr-overlay repositions overlay in center
      this.$.applyFixOverlay.dispatchEvent(
        new CustomEvent('iron-resize', {
          composed: true,
          bubbles: true,
        })
      );
    });
  }

  attached() {
    super.attached();
    this.refitOverlay = () => {
      // re-center the dialog as content changed
      this.$.applyFixOverlay.dispatchEvent(
        new CustomEvent('iron-resize', {
          composed: true,
          bubbles: true,
        })
      );
    };
    this.addEventListener('diff-context-expanded', this.refitOverlay);
  }

  detached() {
    super.detached();
    this.removeEventListener('diff-context-expanded', this.refitOverlay);
  }

  _showSelectedFixSuggestion(fixSuggestion: FixSuggestionInfo) {
    this._currentFix = fixSuggestion;
    return this._fetchFixPreview(fixSuggestion.fix_id);
  }

  _fetchFixPreview(fixId) {
    return this.$.restAPI
      .getRobotCommentFixPreview(this.changeNum, this._patchNum, fixId)
      .then(res => {
        if (res != null) {
          this._currentPreviews = Object.keys(res).map(key => {
            return {filepath: key, preview: res[key]};
          });
        }
      })
      .catch(err => {
        this._close();
        throw err;
      });
  }

  hasSingleFix(_fixSuggestions?: FixSuggestionInfo[]) {
    return (_fixSuggestions || []).length === 1;
  }

  overridePartialPrefs(prefs) {
    // generate a smaller gr-diff than fullscreen for dialog
    return {...prefs, line_length: 50};
  }

  onCancel(e) {
    if (e) {
      e.stopPropagation();
    }
    this._close();
  }

  addOneTo(_selectedFixIdx: number) {
    return _selectedFixIdx + 1;
  }

  _onPrevFixClick(e) {
    if (e) e.stopPropagation();
    if (this._selectedFixIdx >= 1 && this._fixSuggestions) {
      this._selectedFixIdx -= 1;
      return this._showSelectedFixSuggestion(
        this._fixSuggestions[this._selectedFixIdx]
      );
    }
  }

  _onNextFixClick(e) {
    if (e) e.stopPropagation();
    if (
      this._fixSuggestions &&
      this._selectedFixIdx < this._fixSuggestions.length
    ) {
      this._selectedFixIdx += 1;
      return this._showSelectedFixSuggestion(
        this._fixSuggestions[this._selectedFixIdx]
      );
    }
  }

  _noPrevFix(_selectedFixIdx: number) {
    return _selectedFixIdx === 0;
  }

  _noNextFix(_selectedFixIdx: number, fixSuggestions?: FixSuggestionInfo[]) {
    if (!fixSuggestions) return true;
    return _selectedFixIdx === fixSuggestions.length - 1;
  }

  _close() {
    this._currentFix = {};
    this._currentPreviews = [];
    this._isApplyFixLoading = false;

    this.dispatchEvent(
      new CustomEvent('close-fix-preview', {
        bubbles: true,
        composed: true,
      })
    );
    this.$.applyFixOverlay.close();
  }

  _getApplyFixButtonLabel(isLoading: boolean) {
    return isLoading ? 'Saving...' : 'Apply Fix';
  }

  _computeTooltip(change?: ParsedChangeInfo, patchNum?: PatchSetNum) {
    if (!change || patchNum === undefined) return '';
    // If change is defined, change.revisions and change.current_revisions
    // must be defined
    const latestPatchNum = change.revisions![change.current_revision!]._number;
    return latestPatchNum !== patchNum
      ? 'Fix can only be applied to the latest patchset'
      : '';
  }

  _computeDisableApplyFixButton(
    isApplyFixLoading?: boolean,
    change?: ParsedChangeInfo,
    patchNum?: PatchSetNum
  ) {
    if (!change || isApplyFixLoading === undefined || patchNum === undefined) {
      return true;
    }
    // If change is defined, change.revisions and change.current_revisions
    // must be defined
    const currentPatchNum = change.revisions![change.current_revision!]._number;
    if (patchNum !== currentPatchNum) {
      return true;
    }
    return isApplyFixLoading;
  }

  _handleApplyFix(e) {
    if (e) {
      e.stopPropagation();
    }
    if (!this._currentFix || !this._currentFix.fix_id) {
      return;
    }
    this._isApplyFixLoading = true;
    return this.$.restAPI
      .applyFixSuggestion(
        this.changeNum,
        this._patchNum,
        this._currentFix.fix_id
      )
      .then(res => {
        if (res && res.ok) {
          GerritNav.navigateToChange(this.change, 'edit', this._patchNum);
          this._close();
        }
        this._isApplyFixLoading = false;
      });
  }

  getFixDescription(currentFix) {
    return currentFix && currentFix.description
      ? currentFix.description
      : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-apply-fix-dialog': GrApplyFixDialog;
  }
}
