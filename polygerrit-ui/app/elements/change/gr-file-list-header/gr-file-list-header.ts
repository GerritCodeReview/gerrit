/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../diff/gr-diff-mode-selector/gr-diff-mode-selector';
import '../../diff/gr-patch-range-select/gr-patch-range-select';
import '../../edit/gr-edit-controls/gr-edit-controls';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../gr-commit-info/gr-commit-info';
import {FilesExpandedState} from '../gr-file-list-constants';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {property, customElement, query, state} from 'lit/decorators.js';
import {
  AccountInfo,
  ChangeInfo,
  PatchSetNum,
  CommitInfo,
  ServerInfo,
  BasePatchSetNum,
  PatchSetNumber,
} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {GrDiffModeSelector} from '../../diff/gr-diff-mode-selector/gr-diff-mode-selector';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fire, fireNoBubbleNoCompose} from '../../../utils/event-util';
import {css, html, LitElement, nothing} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {when} from 'lit/directives/when.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  Shortcut,
  ShortcutSection,
  shortcutsServiceToken,
} from '../../../services/shortcuts/shortcuts-service';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {createChangeUrl} from '../../../models/views/change';
import {userModelToken} from '../../../models/user/user-model';
import {changeModelToken} from '../../../models/change/change-model';
import {PatchRangeChangeEvent} from '../../diff/gr-patch-range-select/gr-patch-range-select';
import {classMap} from 'lit/directives/class-map.js';

@customElement('gr-file-list-header')
export class GrFileListHeader extends LitElement {
  @property({type: Object})
  account: AccountInfo | undefined;

  @property({type: Object})
  change: ChangeInfo | undefined;

  @property({type: String})
  changeUrl?: string;

  @property({type: Object})
  commitInfo?: CommitInfo;

  @property({type: Boolean})
  editMode?: boolean;

  @property({type: Boolean})
  loggedIn: boolean | undefined;

  @property({type: Number})
  shownFileCount = 0;

  @property({type: String})
  filesExpanded?: FilesExpandedState;

  @state() latestPatchNum?: PatchSetNumber;

  @state() patchNum?: PatchSetNum;

  @state() basePatchNum?: BasePatchSetNum;

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state()
  serverConfig?: ServerInfo;

  @query('#modeSelect')
  modeSelect?: GrDiffModeSelector;

  @query('#expandBtn')
  expandBtn?: GrButton;

  @query('#collapseBtn')
  collapseBtn?: GrButton;

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  // Caps the number of files that can be shown and have the 'show diffs' /
  // 'hide diffs' buttons still be functional.
  private readonly maxFilesForBulkActions = 225;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().diffPreferences$,
      diffPreferences => {
        if (!diffPreferences) return;
        this.diffPrefs = diffPreferences;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.patchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().basePatchNum$,
      x => (this.basePatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        #diffPrefsContainer {
          display: flex;
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
        .allExpanded #expandBtn {
          display: none;
        }
        .someExpanded #expandBtn {
          margin-right: 8px;
        }
        .someExpanded #collapseBtn,
        .allExpanded #collapseBtn {
          align-items: center;
          display: flex;
        }
        .rightControls gr-button,
        gr-patch-range-select {
          margin: 0 -4px;
        }
        .fileViewActions gr-button {
          margin: 0;
        }
        .fileViewActions {
          --gr-button-padding: 2px 4px;
        }
        .fileViewActions,
        .flexContainer {
          align-items: center;
          display: flex;
        }
        .label {
          font-weight: var(--font-weight-medium);
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
  }

  override render() {
    if (!this.change || !this.diffPrefs) {
      return;
    }
    const expandedClass = this.computeExpandedClass(this.filesExpanded);
    return html`
      <div
        class=${classMap({
          'patchInfo-header': true,
          patchInfoOldPatchSet: this.patchNum !== this.latestPatchNum,
        })}
      >
        <div class="patchInfo-left">
          <div class="patchInfoContent">
            <gr-patch-range-select
              id="rangeSelect"
              @patch-range-change=${this.handlePatchChange}
            >
            </gr-patch-range-select>
            <span class="separator"></span>
            <gr-commit-info .commitInfo=${this.commitInfo}></gr-commit-info>
            ${this.renderLatestPatchContainer()}
          </div>
        </div>
        <div class="rightControls ${expandedClass}">
          ${when(
            this.editMode,
            () => html`
              <span class="flexContainer">
                <gr-edit-controls
                  id="editControls"
                  .patchNum=${this.patchNum}
                  .change=${this.change}
                ></gr-edit-controls>
                <span class="separator"></span>
              </span>
            `
          )}
          ${when(
            this.loggedIn && this.diffPrefs,
            () => html`
              <div class="fileViewActions">
                <span class="fileViewActionsLabel">Diff view:</span>
                <gr-diff-mode-selector
                  id="modeSelect"
                  .saveOnChange=${true}
                ></gr-diff-mode-selector>
                ${this.renderDiffPrefsContainer()}
                <span class="separator"></span>
              </div>
            `
          )}
          <span class="downloadContainer desktop">
            <gr-tooltip-content
              has-tooltip
              title=${this.createTitle(
                Shortcut.OPEN_DOWNLOAD_DIALOG,
                ShortcutSection.ACTIONS
              )}
            >
              <gr-button link class="download" @click=${this.handleDownloadTap}
                >Download</gr-button
              >
            </gr-tooltip-content>
          </span>
          ${when(
            this.fileListActionsVisible(
              this.shownFileCount,
              this.maxFilesForBulkActions
            ),
            () => html` <gr-tooltip-content
                has-tooltip
                title=${this.createTitle(
                  Shortcut.TOGGLE_ALL_INLINE_DIFFS,
                  ShortcutSection.FILE_LIST
                )}
              >
                <gr-button id="expandBtn" link @click=${this.expandAllDiffs}
                  >Expand All</gr-button
                >
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip
                title=${this.createTitle(
                  Shortcut.TOGGLE_ALL_INLINE_DIFFS,
                  ShortcutSection.FILE_LIST
                )}
              >
                <gr-button id="collapseBtn" link @click=${this.collapseAllDiffs}
                  >Collapse All</gr-button
                >
              </gr-tooltip-content>`,
            () => html`
              <div class="warning">
                Bulk actions disabled because there are too many files.
              </div>
            `
          )}
        </div>
      </div>
    `;
  }

  private renderLatestPatchContainer() {
    if (this.editMode || this.patchNum === this.latestPatchNum) return nothing;
    return html`
      <span class="container latestPatchContainer">
        <span class="separator"></span>
        <a href=${ifDefined(this.changeUrl)}>Go to latest patch set</a>
      </span>
    `;
  }

  private renderDiffPrefsContainer() {
    if (this.editMode) return nothing;
    return html`
      <span id="diffPrefsContainer">
        <gr-tooltip-content has-tooltip title="Diff preferences">
          <gr-button
            link
            class="prefsButton desktop"
            @click=${this.handleDiffPrefsTap}
            ><gr-icon icon="settings" filled></gr-icon
          ></gr-button>
        </gr-tooltip-content>
      </span>
    `;
  }

  private expandAllDiffs() {
    fire(this, 'expand-diffs', {});
  }

  private collapseAllDiffs() {
    fire(this, 'collapse-diffs', {});
  }

  private computeExpandedClass(filesExpanded?: FilesExpandedState) {
    const classes = [];
    if (filesExpanded === FilesExpandedState.ALL) {
      classes.push('allExpanded');
    } else if (filesExpanded === FilesExpandedState.SOME) {
      classes.push('someExpanded');
    }
    return classes.join(' ');
  }

  private fileListActionsVisible(
    shownFileCount: number,
    maxFilesForBulkActions: number
  ) {
    return shownFileCount <= maxFilesForBulkActions;
  }

  handlePatchChange(e: PatchRangeChangeEvent) {
    const {basePatchNum, patchNum} = e.detail;
    if (
      (basePatchNum === this.basePatchNum && patchNum === this.patchNum) ||
      !this.change
    ) {
      return;
    }
    this.getNavigation().setUrl(
      createChangeUrl({change: this.change, patchNum, basePatchNum})
    );
  }

  private handleDiffPrefsTap(e: Event) {
    e.preventDefault();
    fire(this, 'open-diff-prefs', {});
  }

  private handleDownloadTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'open-download-dialog', {});
  }

  private createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.getShortcutsService().createTitle(shortcutName, section);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-header': GrFileListHeader;
  }
  interface HTMLElementEventMap {
    'collapse-diffs': CustomEvent<{}>;
    'expand-diffs': CustomEvent<{}>;
    'open-diff-prefs': CustomEvent<{}>;
    'open-download-dialog': CustomEvent<{}>;
  }
}
