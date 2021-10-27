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
import '../gr-diff/gr-diff';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-apply-fix-dialog_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {
  NumericChangeId,
  EditPatchSetNum,
  FixId,
  FixSuggestionInfo,
  PatchSetNum,
  RobotId,
  BasePatchSetNum,
} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {isRobot} from '../../../utils/comment-util';
import {OpenFixPreviewEvent} from '../../../types/events';
import {appContext} from '../../../services/app-context';
import {fireCloseFixPreview, fireEvent} from '../../../utils/event-util';
import {DiffLayer, ParsedChangeInfo} from '../../../types/types';
import {GrButton} from '../../shared/gr-button/gr-button';
import {TokenHighlightLayer} from '../gr-diff-builder/token-highlight-layer';

export interface GrApplyFixDialog {
  $: {
    applyFixOverlay: GrOverlay;
    nextFix: GrButton;
  };
}

interface FilePreview {
  filepath: string;
  preview: DiffInfo;
}

@customElement('gr-apply-fix-dialog')
export class GrApplyFixDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  prefs?: DiffPreferencesInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: Number})
  _patchNum?: PatchSetNum;

  @property({type: String})
  _robotId?: RobotId;

  @property({type: Object})
  _currentFix?: FixSuggestionInfo;

  @property({type: Array})
  _currentPreviews: FilePreview[] = [];

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
  _disableApplyFixButton = false;

  @property({type: Array})
  layers: DiffLayer[] = [];

  private refitOverlay?: () => void;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this.restApiService.getPreferences().then(prefs => {
      if (!prefs?.disable_token_highlighting) {
        this.layers = [new TokenHighlightLayer(this)];
      }
    });
  }

  /**
   * Given robot comment CustomEvent object, fetch diffs associated
   * with first robot comment suggested fix and open dialog.
   *
   * @param e to be passed from gr-comment with robot comment detail.
   * @return Promise that resolves either when all
   * preview diffs are fetched or no fix suggestions in custom event detail.
   */
  open(e: OpenFixPreviewEvent) {
    const detail = e.detail;
    const comment = detail.comment;
    if (!detail.patchNum || !comment || !isRobot(comment)) {
      return Promise.resolve();
    }
    this._patchNum = detail.patchNum;
    this._fixSuggestions = comment.fix_suggestions;
    this._robotId = comment.robot_id;
    if (!this._fixSuggestions || !this._fixSuggestions.length) {
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
      fireEvent(this.$.applyFixOverlay, 'iron-resize');
    });
  }

  override connectedCallback() {
    super.connectedCallback();
    this.refitOverlay = () => {
      // re-center the dialog as content changed
      fireEvent(this.$.applyFixOverlay, 'iron-resize');
    };
    this.addEventListener('diff-context-expanded', this.refitOverlay);
  }

  override disconnectedCallback() {
    if (this.refitOverlay) {
      this.removeEventListener('diff-context-expanded', this.refitOverlay);
    }
    super.disconnectedCallback();
  }

  _showSelectedFixSuggestion(fixSuggestion: FixSuggestionInfo) {
    this._currentFix = fixSuggestion;
    return this._fetchFixPreview(fixSuggestion.fix_id);
  }

  _fetchFixPreview(fixId: FixId) {
    if (!this.changeNum || !this._patchNum) {
      return Promise.reject(
        new Error('Both _patchNum and changeNum must be set')
      );
    }
    return this.restApiService
      .getRobotCommentFixPreview(this.changeNum, this._patchNum, fixId)
      .then(res => {
        if (res) {
          this._currentPreviews = Object.keys(res).map(key => {
            return {filepath: key, preview: res[key]};
          });
        }
      })
      .catch(err => {
        this._close(false);
        throw err;
      });
  }

  hasSingleFix(_fixSuggestions?: FixSuggestionInfo[]) {
    return (_fixSuggestions || []).length === 1;
  }

  overridePartialPrefs(prefs?: DiffPreferencesInfo) {
    if (!prefs) return undefined;
    // generate a smaller gr-diff than fullscreen for dialog
    return {...prefs, line_length: 50};
  }

  onCancel(e: Event) {
    if (e) {
      e.stopPropagation();
    }
    this._close(false);
  }

  addOneTo(_selectedFixIdx: number) {
    return _selectedFixIdx + 1;
  }

  _onPrevFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (this._selectedFixIdx >= 1 && this._fixSuggestions) {
      this._selectedFixIdx -= 1;
      this._showSelectedFixSuggestion(
        this._fixSuggestions[this._selectedFixIdx]
      );
    }
  }

  _onNextFixClick(e: Event) {
    if (e) e.stopPropagation();
    if (
      this._fixSuggestions &&
      this._selectedFixIdx < this._fixSuggestions.length
    ) {
      this._selectedFixIdx += 1;
      this._showSelectedFixSuggestion(
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

  _close(fixApplied: boolean) {
    this._currentFix = undefined;
    this._currentPreviews = [];
    this._isApplyFixLoading = false;

    fireCloseFixPreview(this, fixApplied);
    this.$.applyFixOverlay.close();
  }

  _getApplyFixButtonLabel(isLoading: boolean) {
    return isLoading ? 'Saving...' : 'Apply Fix';
  }

  _computeTooltip(change?: ParsedChangeInfo, patchNum?: PatchSetNum) {
    if (!change || !patchNum) return '';
    const latestPatchNum = change.revisions[change.current_revision]._number;
    return latestPatchNum !== patchNum
      ? 'Fix can only be applied to the latest patchset'
      : '';
  }

  _computeDisableApplyFixButton(
    isApplyFixLoading: boolean,
    change?: ParsedChangeInfo,
    patchNum?: PatchSetNum
  ) {
    if (!change || isApplyFixLoading === undefined || patchNum === undefined) {
      return true;
    }
    const currentPatchNum = change.revisions[change.current_revision]._number;
    if (patchNum !== currentPatchNum) {
      return true;
    }
    return isApplyFixLoading;
  }

  _handleApplyFix(e: Event) {
    if (e) {
      e.stopPropagation();
    }

    const changeNum = this.changeNum;
    const patchNum = this._patchNum;
    const change = this.change;
    if (!changeNum || !patchNum || !change || !this._currentFix) {
      return Promise.reject(new Error('Not all required properties are set.'));
    }
    this._isApplyFixLoading = true;
    return this.restApiService
      .applyFixSuggestion(changeNum, patchNum, this._currentFix.fix_id)
      .then(res => {
        if (res && res.ok) {
          GerritNav.navigateToChange(
            change,
            EditPatchSetNum,
            patchNum as BasePatchSetNum
          );
          this._close(true);
        }
        this._isApplyFixLoading = false;
      });
  }

  getFixDescription(currentFix?: FixSuggestionInfo) {
    return currentFix && currentFix.description ? currentFix.description : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-apply-fix-dialog': GrApplyFixDialog;
  }
}
