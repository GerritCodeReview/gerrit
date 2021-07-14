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
import '../../../styles/shared-styles';
import '../../diff/gr-diff-mode-selector/gr-diff-mode-selector';
import '../../diff/gr-patch-range-select/gr-patch-range-select';
import '../../edit/gr-edit-controls/gr-edit-controls';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-commit-info/gr-commit-info';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-file-list-header_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {computeLatestPatchNum, PatchSet} from '../../../utils/patch-set-util';
import {property, customElement} from '@polymer/decorators';
import {
  AccountInfo,
  ChangeInfo,
  EditPatchSetNum,
  ParentPatchSetNum,
  PatchSetNum,
  CommitInfo,
  ServerInfo,
  RevisionInfo,
  NumericChangeId,
  BasePatchSetNum,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {GrDiffModeSelector} from '../../diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {ChangeStatus, DiffViewMode} from '../../../constants/constants';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fireEvent} from '../../../utils/event-util';

const MERGED_STATUS = 'MERGED';

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-header': GrFileListHeader;
  }
}

export interface GrFileListHeader {
  $: {
    modeSelect: GrDiffModeSelector;
    expandBtn: GrButton;
    collapseBtn: GrButton;
  };
}

@customElement('gr-file-list-header')
export class GrFileListHeader extends KeyboardShortcutMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * @event expand-diffs
   */

  /**
   * @event collapse-diffs
   */

  /**
   * @event open-diff-prefs
   */

  /**
   * @event open-included-in-dialog
   */

  /**
   * @event open-download-dialog
   */

  @property({type: Object})
  account: AccountInfo | undefined;

  @property({type: Array})
  allPatchSets?: PatchSet[];

  @property({type: Object})
  change: ChangeInfo | undefined;

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: String})
  changeUrl?: string;

  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Object})
  commitInfo?: CommitInfo;

  @property({type: Boolean})
  editMode?: boolean;

  @property({type: Boolean})
  loggedIn: boolean | undefined;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: Number})
  shownFileCount?: number;

  @property({type: Object})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Boolean})
  diffPrefsDisabled?: boolean;

  @property({type: String, notify: true})
  diffViewMode?: DiffViewMode;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  basePatchNum?: BasePatchSetNum;

  @property({type: String})
  filesExpanded?: FilesExpandedState;

  // Caps the number of files that can be shown and have the 'show diffs' /
  // 'hide diffs' buttons still be functional.
  @property({type: Number})
  readonly _maxFilesForBulkActions = 225;

  @property({type: Object})
  revisionInfo?: RevisionInfo;

  setDiffViewMode(mode: DiffViewMode) {
    this.$.modeSelect.setMode(mode);
  }

  _expandAllDiffs() {
    fireEvent(this, 'expand-diffs');
  }

  _collapseAllDiffs() {
    fireEvent(this, 'collapse-diffs');
  }

  _computeExpandedClass(filesExpanded: FilesExpandedState) {
    const classes = [];
    if (filesExpanded === FilesExpandedState.ALL) {
      classes.push('openFile');
      classes.push('allExpanded');
    } else if (filesExpanded === FilesExpandedState.SOME) {
      classes.push('openFile');
      classes.push('someExpanded');
    }
    return classes.join(' ');
  }

  _computePrefsButtonHidden(
    prefs: DiffPreferencesInfo,
    diffPrefsDisabled: boolean
  ) {
    return diffPrefsDisabled || !prefs;
  }

  _fileListActionsVisible(
    shownFileCount: number,
    maxFilesForBulkActions: number
  ) {
    return shownFileCount <= maxFilesForBulkActions;
  }

  _showAddPatchsetDescription(
    patchsetDescription: string,
    change?: ChangeInfo
  ) {
    return !patchsetDescription && change?.status === ChangeStatus.NEW;
  }

  _handlePatchChange(e: CustomEvent) {
    const {basePatchNum, patchNum} = e.detail;
    if (
      (basePatchNum === this.basePatchNum && patchNum === this.patchNum) ||
      !this.change
    ) {
      return;
    }
    if (patchNum === EditPatchSetNum && basePatchNum === ParentPatchSetNum) {
      GerritNav.navigateToChange(this.change, undefined, undefined, true);
      return;
    }
    GerritNav.navigateToChange(this.change, patchNum, basePatchNum);
  }

  _handlePrefsTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'open-diff-prefs');
  }

  _handleIncludedInTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'open-included-in-dialog');
  }

  _handleDownloadTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('open-download-dialog', {bubbles: false})
    );
  }

  _computeEditModeClass(editMode?: boolean) {
    return editMode ? 'editMode' : '';
  }

  _computePatchInfoClass(patchNum?: PatchSetNum, allPatchSets?: PatchSet[]) {
    const latestNum = computeLatestPatchNum(allPatchSets);
    if (patchNum === latestNum) {
      return '';
    }
    return 'patchInfoOldPatchSet';
  }

  _hideIncludedIn(change?: ChangeInfo) {
    return change?.status === MERGED_STATUS ? '' : 'hide';
  }
}
