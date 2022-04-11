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
import '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import '../../diff/gr-patch-range-select/gr-patch-range-select';
import '../../edit/gr-edit-controls/gr-edit-controls';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-commit-info/gr-commit-info';
import {PolymerElement} from '@polymer/polymer/polymer-element';
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
import {GrDiffModeSelector} from '../../../embed/diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fireEvent} from '../../../utils/event-util';
import {
  Shortcut,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {getAppContext} from '../../../services/app-context';
import {css, html} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';

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

  private readonly shortcuts = getAppContext().shortcutsService;

  static override styles = [
    sharedStyles,
    css`
      .prefsButton {
        float: right;
      }
      .patchInfoOldPatchSet.patchInfo-header {
        background-color: var(--emphasis-color);
      }
      .patchInfo-header {
        align-items: center;
        display: flex;
        padding: var(--spacing-s) var(--spacing-l);
      }
      .patchInfo-left {
        align-items: baseline;
        display: flex;
      }
      .patchInfoContent {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
      }
      .patchInfo-header .container.latestPatchContainer {
        display: none;
      }
      .patchInfoOldPatchSet .container.latestPatchContainer {
        display: initial;
      }
      .editMode.patchInfoOldPatchSet .container.latestPatchContainer {
        display: none;
      }
      .latestPatchContainer a {
        text-decoration: none;
      }
      .mobile {
        display: none;
      }
      .patchInfo-header .container {
        align-items: center;
        display: flex;
      }
      .downloadContainer,
      .uploadContainer {
        margin-right: 16px;
      }
      .uploadContainer.hide {
        display: none;
      }
      .rightControls {
        align-self: flex-end;
        margin: auto 0 auto auto;
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        font-weight: var(--font-weight-normal);
        justify-content: flex-end;
      }
      #collapseBtn,
      .allExpanded #expandBtn,
      .fileViewActions {
        display: none;
      }
      .someExpanded #expandBtn {
        margin-right: 8px;
      }
      .someExpanded #collapseBtn,
      .allExpanded #collapseBtn,
      .openFile .fileViewActions {
        align-items: center;
        display: flex;
      }
      .rightControls gr-button,
      gr-patch-range-select {
        margin: 0 -4px;
      }
      .fileViewActions gr-button {
        margin: 0;
        --gr-button-padding: 2px 4px;
      }
      .editMode .hideOnEdit {
        display: none;
      }
      .showOnEdit {
        display: none;
      }
      .editMode .showOnEdit {
        display: initial;
      }
      .editMode .showOnEdit.flexContainer {
        align-items: center;
        display: flex;
      }
      .label {
        font-weight: var(--font-weight-bold);
        margin-right: 24px;
      }
      gr-commit-info,
      gr-edit-controls {
        margin-right: -5px;
      }
      .fileViewActionsLabel {
        margin-right: var(--spacing-xs);
      }
      @media screen and (max-width: 50em) {
        .patchInfo-header .desktop {
          display: none;
        }
      }
    `,
  ];

  override render() {
    return html`
      <div
        class$="patchInfo-header [[_computeEditModeClass(editMode)]] [[_computePatchInfoClass(patchNum, allPatchSets)]]"
      >
        <div class="patchInfo-left">
          <div class="patchInfoContent">
            <gr-patch-range-select
              id="rangeSelect"
              change-num="[[changeNum]]"
              patch-num="[[patchNum]]"
              base-patch-num="[[basePatchNum]]"
              available-patches="[[allPatchSets]]"
              revisions="[[change.revisions]]"
              revision-info="[[revisionInfo]]"
              on-patch-range-change="_handlePatchChange"
            >
            </gr-patch-range-select>
            <span class="separator"></span>
            <gr-commit-info
              change="[[change]]"
              server-config="[[serverConfig]]"
              commit-info="[[commitInfo]]"
            ></gr-commit-info>
            <span class="container latestPatchContainer">
              <span class="separator"></span>
              <a href$="[[changeUrl]]">Go to latest patch set</a>
            </span>
          </div>
        </div>
        <div class$="rightControls [[_computeExpandedClass(filesExpanded)]]">
          <template is="dom-if" if="[[editMode]]">
            <span class="showOnEdit flexContainer">
              <gr-edit-controls
                id="editControls"
                patch-num="[[patchNum]]"
                change="[[change]]"
              ></gr-edit-controls>
              <span class="separator"></span>
            </span>
          </template>
          <div class="fileViewActions">
            <span class="fileViewActionsLabel">Diff view:</span>
            <gr-diff-mode-selector
              id="modeSelect"
              save-on-change="[[loggedIn]]"
            ></gr-diff-mode-selector>
            <span
              id="diffPrefsContainer"
              class="hideOnEdit"
              hidden$="[[_computePrefsButtonHidden(diffPrefs, loggedIn)]]"
              hidden=""
            >
              <gr-tooltip-content has-tooltip title="Diff preferences">
                <gr-button
                  link=""
                  class="prefsButton desktop"
                  on-click="_handlePrefsTap"
                  ><iron-icon icon="gr-icons:settings"></iron-icon
                ></gr-button>
              </gr-tooltip-content>
            </span>
            <span class="separator"></span>
          </div>
          <span class="downloadContainer desktop">
            <gr-tooltip-content
              has-tooltip
              title="[[createTitle(Shortcut.OPEN_DOWNLOAD_DIALOG,
                   ShortcutSection.ACTIONS)]]"
            >
              <gr-button link="" class="download" on-click="_handleDownloadTap"
                >Download</gr-button
              >
            </gr-tooltip-content>
          </span>
          <template
            is="dom-if"
            if="[[_fileListActionsVisible(shownFileCount, _maxFilesForBulkActions)]]"
          >
            <gr-tooltip-content
              has-tooltip
              title="[[createTitle(Shortcut.TOGGLE_ALL_INLINE_DIFFS,
                  ShortcutSection.FILE_LIST)]]"
            >
              <gr-button id="expandBtn" link="" on-click="_expandAllDiffs"
                >Expand All</gr-button
              >
            </gr-tooltip-content>
            <gr-tooltip-content
              has-tooltip
              title="[[createTitle(Shortcut.TOGGLE_ALL_INLINE_DIFFS,
                  ShortcutSection.FILE_LIST)]]"
            >
              <gr-button id="collapseBtn" link="" on-click="_collapseAllDiffs"
                >Collapse All</gr-button
              >
            </gr-tooltip-content>
          </template>
          <template
            is="dom-if"
            if="[[!_fileListActionsVisible(shownFileCount, _maxFilesForBulkActions)]]"
          >
            <div class="warning">
              Bulk actions disabled because there are too many files.
            </div>
          </template>
        </div>
      </div>
    `;
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
