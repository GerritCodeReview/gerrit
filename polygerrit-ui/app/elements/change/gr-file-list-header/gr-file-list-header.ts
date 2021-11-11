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
import {FilesExpandedState} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {computeLatestPatchNum, PatchSet} from '../../../utils/patch-set-util';
import {property, customElement} from '@polymer/decorators';
import {
  AccountInfo,
  ChangeInfo,
  PatchSetNum,
  CommitInfo,
  ServerInfo,
  RevisionInfo,
  NumericChangeId,
  BasePatchSetNum,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffModeSelector} from '../../diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fireEvent} from '../../../utils/event-util';
import {
  Shortcut,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {appContext} from '../../../services/app-context';

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
export class GrFileListHeader extends PolymerElement {
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

  private readonly shortcuts = appContext.shortcutsService;

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

  _computePrefsButtonHidden(prefs: DiffPreferencesInfo, loggedIn: boolean) {
    return !loggedIn || !prefs;
  }

  _fileListActionsVisible(
    shownFileCount: number,
    maxFilesForBulkActions: number
  ) {
    return shownFileCount <= maxFilesForBulkActions;
  }

  _handlePatchChange(e: CustomEvent) {
    const {basePatchNum, patchNum} = e.detail;
    if (
      (basePatchNum === this.basePatchNum && patchNum === this.patchNum) ||
      !this.change
    ) {
      return;
    }
    GerritNav.navigateToChange(this.change, {patchNum, basePatchNum});
  }

  _handlePrefsTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'open-diff-prefs');
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

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.shortcuts.createTitle(shortcutName, section);
  }
}
