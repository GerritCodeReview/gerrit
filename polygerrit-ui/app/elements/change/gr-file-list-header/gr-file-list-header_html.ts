/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
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
  </style>
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
