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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        background-color: var(--view-background-color);
      }
      .hidden {
        display: none;
      }
      gr-patch-range-select {
        display: block;
      }
      gr-diff {
        border: none;
        --diff-container-styles: {
          border-bottom: 1px solid var(--border-color);
        }
      }
      gr-fixed-panel {
        background-color: var(--view-background-color);
        border-bottom: 1px solid var(--border-color);
        z-index: 1;
      }
      header,
      .subHeader {
        align-items: center;
        display: flex;
        justify-content: space-between;
      }
      header {
        padding: var(--spacing-s) var(--spacing-xl);
        border-bottom: 1px solid var(--border-color);
      }
      .changeNumberColon {
        color: transparent;
      }
      .headerSubject {
        margin-right: var(--spacing-m);
        font-weight: var(--font-weight-bold);
      }
      .patchRangeLeft {
        align-items: center;
        display: flex;
      }
      .navLink:not([href]) {
        color: var(--deemphasized-text-color);
      }
      .navLinks {
        align-items: center;
        display: flex;
        white-space: nowrap;
      }
      .navLink {
        padding: 0 var(--spacing-xs);
      }
      .reviewed {
        display: inline-block;
        margin: 0 var(--spacing-xs);
        vertical-align: .15em;
      }
      .jumpToFileContainer {
        display: inline-block;
      }
      .mobile {
        display: none;
      }
      gr-button {
        padding: var(--spacing-s) 0;
        text-decoration: none;
      }
      .loading {
        color: var(--deemphasized-text-color);
        font-family: var(--header-font-family);
        font-size: var(--font-size-h1);
        font-weight: var(--font-weight-h1);
        line-height: var(--line-height-h1);
        height: 100%;
        padding: var(--spacing-l);
        text-align: center;
      }
      .subHeader {
        background-color: var(--background-color-secondary);
        flex-wrap: wrap;
        padding: 0 var(--spacing-l);
      }
      .prefsButton {
        text-align: right;
      }
      .noOverflow {
        display: block;
        overflow: auto;
      }
      .editMode .hideOnEdit {
        display: none;
      }
      .blameLoader,
      .fileNum {
        display: none;
      }
      .blameLoader.show,
      .fileNum.show ,
      .download,
      .preferences,
      .rightControls {
        align-items: center;
        display: flex;
      }
      .diffModeSelector,
      .editButton {
        align-items: center;
        display: flex;
      }
      .diffModeSelector span,
      .editButton span {
        margin-right: var(--spacing-xs);
      }
      .diffModeSelector.hide,
      .separator.hide {
        display: none;
      }
      gr-dropdown-list {
        --trigger-style: {
          text-transform: none;
        }
      }
      .editButtona a {
        text-decoration: none;
      }
      @media screen and (max-width: 50em) {
        header {
          padding: var(--spacing-s) var(--spacing-l);
        }
        .dash {
          display: none;
        }
        .desktop {
          display: none;
        }
        .fileNav {
          align-items: flex-start;
          display: flex;
          margin: 0 var(--spacing-xs);
        }
        .fullFileName {
          display: block;
          font-style: italic;
          min-width: 50%;
          padding: 0 var(--spacing-xxs);
          text-align: center;
          width: 100%;
          word-wrap: break-word;
        }
        .reviewed {
          vertical-align: -1px;
        }
        .mobileNavLink {
          color: var(--primary-text-color);
          font-family: var(--header-font-family);
          font-size: var(--font-size-h2);
          font-weight: var(--font-weight-h2);
          line-height: var(--line-height-h2);
          text-decoration: none;
        }
        .mobileNavLink:not([href]) {
          color: var(--deemphasized-text-color);
        }
        .jumpToFileContainer {
          display: block;
          width: 100%;
        }
        gr-dropdown-list {
          width: 100%;
          --gr-select-style: {
            display: block;
            width: 100%;
          }
          --native-select-style: {
            width: 100%;
          }
        }
      }
    </style>
    <gr-fixed-panel class\$="[[_computeContainerClass(_editMode)]]" floating-disabled="[[_panelFloatingDisabled]]" keep-on-scroll="" ready-for-measure="[[!_loading]]" on-floating-height-changed="_onChangeHeaderPanelHeightChanged">
      <header>
        <div>
          <a href\$="[[_computeChangePath(_change, _patchRange.*, _change.revisions)]]">[[_changeNum]]</a><!--
       --><span class="changeNumberColon">:</span>
          <span class="headerSubject">[[_change.subject]]</span>
          <input id="reviewed" class="reviewed hideOnEdit" type="checkbox" on-change="_handleReviewedChange" hidden\$="[[!_loggedIn]]" hidden=""><!--
       --><div class="jumpToFileContainer">
            <gr-dropdown-list id="dropdown" value="[[_path]]" on-value-change="_handleFileChange" items="[[_formattedFiles]]" initial-count="75">
           </gr-dropdown-list>
          </div>
        </div>
        <div class="navLinks desktop">
          <span class\$="fileNum [[_computeFileNumClass(_fileNum, _formattedFiles)]]">
            File [[_fileNum]] of [[_formattedFiles.length]]
            <span class="separator"></span>
          </span>
          <a class="navLink" title="[[createTitle(Shortcut.PREV_FILE,
                    ShortcutSection.NAVIGATION)]]" href\$="[[_computeNavLinkURL(_change, _path, _fileList, -1, 1)]]">
            Prev</a>
          <span class="separator"></span>
          <a class="navLink" title="[[createTitle(Shortcut.UP_TO_CHANGE,
                ShortcutSection.NAVIGATION)]]" href\$="[[_computeChangePath(_change, _patchRange.*, _change.revisions)]]">
            Up</a>
          <span class="separator"></span>
          <a class="navLink" title="[[createTitle(Shortcut.NEXT_FILE,
                ShortcutSection.NAVIGATION)]]" href\$="[[_computeNavLinkURL(_change, _path, _fileList, 1, 1)]]">
            Next</a>
        </div>
      </header>
      <div class="subHeader">
        <div class="patchRangeLeft">
          <gr-patch-range-select id="rangeSelect" change-num="[[_changeNum]]" change-comments="[[_changeComments]]" patch-num="[[_patchRange.patchNum]]" base-patch-num="[[_patchRange.basePatchNum]]" files-weblinks="[[_filesWeblinks]]" available-patches="[[_allPatchSets]]" revisions="[[_change.revisions]]" revision-info="[[_revisionInfo]]" on-patch-range-change="_handlePatchChange">
          </gr-patch-range-select>
          <span class="download desktop">
            <span class="separator"></span>
            <gr-dropdown link="" down-arrow="" items="[[_computeDownloadDropdownLinks(_change.project, _changeNum, _patchRange, _path, _diff)]]" horizontal-align="left">
              <span class="downloadTitle">
                Download
              </span>
            </gr-dropdown>
          </span>
        </div>
        <div class="rightControls">
          <span class\$="blameLoader [[_computeBlameLoaderClass(_isImageDiff, _path)]]">
            <gr-button link="" id="toggleBlame" title="[[createTitle(Shortcut.TOGGLE_BLAME, ShortcutSection.DIFFS)]]" disabled="[[_isBlameLoading]]" on-click="_toggleBlame">[[_computeBlameToggleLabel(_isBlameLoaded, _isBlameLoading)]]</gr-button>
          </span>
          <template is="dom-if" if="[[_computeIsLoggedIn(_loggedIn)]]">
            <span class="separator"></span>
            <span class="editButton">
              <gr-button link="" title="Edit current file" on-click="_goToEditFile">edit</gr-button>
            </span>
          </template>
          <span class="separator"></span>
          <div class\$="diffModeSelector [[_computeModeSelectHideClass(_isImageDiff)]]">
            <span>Diff view:</span>
            <gr-diff-mode-selector id="modeSelect" save-on-change="[[!_diffPrefsDisabled]]" mode="{{changeViewState.diffMode}}"></gr-diff-mode-selector>
          </div>
          <span id="diffPrefsContainer" hidden\$="[[_computePrefsButtonHidden(_prefs, _diffPrefsDisabled)]]" hidden="">
            <span class="preferences desktop">
              <gr-button link="" class="prefsButton" has-tooltip="" title="Diff preferences" on-click="_handlePrefsTap"><iron-icon icon="gr-icons:settings"></iron-icon></gr-button>
            </span>
          </span>
          <gr-endpoint-decorator name="annotation-toggler">
            <span hidden="" id="annotation-span">
              <label for="annotation-checkbox" id="annotation-label"></label>
              <iron-input type="checkbox" disabled="">
                <input is="iron-input" type="checkbox" id="annotation-checkbox" disabled="">
              </iron-input>
            </span>
          </gr-endpoint-decorator>
        </div>
      </div>
      <div class="fileNav mobile">
        <a class="mobileNavLink" href\$="[[_computeNavLinkURL(_change, _path, _fileList, -1, 1)]]">
          &lt;</a>
        <div class="fullFileName mobile">[[computeDisplayPath(_path)]]
        </div>
        <a class="mobileNavLink" href\$="[[_computeNavLinkURL(_change, _path, _fileList, 1, 1)]]">
          &gt;</a>
      </div>
    </gr-fixed-panel>
    <div class="loading" hidden\$="[[!_loading]]">Loading...</div>
    <gr-diff-host id="diffHost" hidden="" hidden\$="[[_loading]]" class\$="[[_computeDiffClass(_panelFloatingDisabled)]]" is-image-diff="{{_isImageDiff}}" files-weblinks="{{_filesWeblinks}}" diff="{{_diff}}" change-num="[[_changeNum]]" commit-range="[[_commitRange]]" patch-range="[[_patchRange]]" path="[[_path]]" prefs="[[_prefs]]" project-name="[[_change.project]]" view-mode="[[_diffMode]]" is-blame-loaded="{{_isBlameLoaded}}" on-comment-anchor-tap="_onLineSelected" on-line-selected="_onLineSelected">
    </gr-diff-host>
    <gr-apply-fix-dialog id="applyFixDialog" prefs="[[_prefs]]" change="[[_change]]" change-num="[[_changeNum]]">
    </gr-apply-fix-dialog>
    <gr-diff-preferences-dialog id="diffPreferencesDialog" diff-prefs="{{_prefs}}" on-reload-diff-preference="_handleReloadingDiffPreference">
    </gr-diff-preferences-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-storage id="storage"></gr-storage>
    <gr-diff-cursor id="cursor" scroll-top-margin="[[_scrollTopMargin]]"></gr-diff-cursor>
    <gr-comment-api id="commentAPI"></gr-comment-api>
`;
