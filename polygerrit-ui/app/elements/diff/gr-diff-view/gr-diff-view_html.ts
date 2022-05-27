/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-a11y-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    :host {
      display: block;
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
    }
    .stickyHeader {
      background-color: var(--view-background-color);
      position: sticky;
      top: 0;
      /* TODO(dhruvsri): This is required only because of 'position:relative' in
         <gr-diff-highlight> (which could maybe be removed??). */
      z-index: 1;
      box-shadow: var(--elevation-level-1);
      /* This is just for giving the box-shadow some space. */
      margin-bottom: 2px;
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
      vertical-align: top;
      position: relative;
      top: 8px;
    }
    .jumpToFileContainer {
      display: inline-block;
      word-break: break-all;
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
    .editMode .hideOnEdit {
      display: none;
    }
    .blameLoader,
    .fileNum {
      display: none;
    }
    .blameLoader.show,
    .fileNum.show,
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
        word-break: break-all;
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
    :host(.hideComments) {
      --gr-comment-thread-display: none;
    }
  </style>
  <div class$="stickyHeader [[_computeContainerClass(_editMode)]]">
    <h1 class="assistive-tech-only">
      Diff of [[_computeTruncatedPath(_path)]]
    </h1>
    <header>
      <div>
        <a
          href$="[[_computeChangePath(_change, _patchRange.*, _change.revisions)]]"
          >[[_changeNum]]</a
        ><!--
       --><span class="changeNumberColon">:</span>
        <span class="headerSubject">[[_change.subject]]</span>
        <input
          id="reviewed"
          class="reviewed hideOnEdit"
          type="checkbox"
          on-change="_handleReviewedChange"
          hidden$="[[!_loggedIn]]"
          hidden=""
          title="Toggle reviewed status of file"
          aria-label="file reviewed"
        /><!--
       -->
        <div class="jumpToFileContainer">
          <gr-dropdown-list
            id="dropdown"
            value="[[_path]]"
            on-value-change="_handleFileChange"
            items="[[_formattedFiles]]"
            initial-count="75"
            show-copy-for-trigger-text
          >
          </gr-dropdown-list>
        </div>
      </div>
      <div class="navLinks desktop">
        <span
          class$="fileNum [[_computeFileNumClass(_fileNum, _formattedFiles)]]"
        >
          File [[_fileNum]] of [[_formattedFiles.length]]
          <span class="separator"></span>
        </span>
        <a
          class="navLink"
          title="[[createTitle(Shortcut.PREV_FILE,
                    ShortcutSection.NAVIGATION)]]"
          href$="[[_computeNavLinkURL(_change, _path, _fileList, -1)]]"
        >
          Prev</a
        >
        <span class="separator"></span>
        <a
          class="navLink"
          title="[[createTitle(Shortcut.UP_TO_CHANGE,
                ShortcutSection.NAVIGATION)]]"
          href$="[[_computeChangePath(_change, _patchRange.*, _change.revisions)]]"
        >
          Up</a
        >
        <span class="separator"></span>
        <a
          class="navLink"
          title="[[createTitle(Shortcut.NEXT_FILE,
                ShortcutSection.NAVIGATION)]]"
          href$="[[_computeNavLinkURL(_change, _path, _fileList, 1)]]"
        >
          Next</a
        >
      </div>
    </header>
    <div class="subHeader">
      <div class="patchRangeLeft">
        <gr-patch-range-select
          id="rangeSelect"
          change-num="[[_changeNum]]"
          patch-num="[[_patchRange.patchNum]]"
          base-patch-num="[[_patchRange.basePatchNum]]"
          files-weblinks="[[_filesWeblinks]]"
          available-patches="[[_allPatchSets]]"
          revisions="[[_change.revisions]]"
          revision-info="[[_revisionInfo]]"
          on-patch-range-change="_handlePatchChange"
        >
        </gr-patch-range-select>
        <span class="download desktop">
          <span class="separator"></span>
          <gr-dropdown
            link=""
            down-arrow=""
            items="[[_computeDownloadDropdownLinks(_change.project, _changeNum, _patchRange, _path, _diff)]]"
            horizontal-align="left"
          >
            <span class="downloadTitle"> Download </span>
          </gr-dropdown>
        </span>
      </div>
      <div class="rightControls">
        <span
          class$="blameLoader [[_computeBlameLoaderClass(_isImageDiff, _path)]]"
        >
          <gr-button
            link=""
            id="toggleBlame"
            title="[[createTitle(Shortcut.TOGGLE_BLAME, ShortcutSection.DIFFS)]]"
            disabled="[[_isBlameLoading]]"
            on-click="_toggleBlame"
            >[[_computeBlameToggleLabel(_isBlameLoaded,
            _isBlameLoading)]]</gr-button
          >
        </span>
        <template
          is="dom-if"
          if="[[_computeCanEdit(_loggedIn, _editWeblinks, _change.*)]]"
        >
          <span class="separator"></span>
          <span class="editButton">
            <gr-button
              link=""
              title="Edit current file"
              on-click="_goToEditFile"
              >edit</gr-button
            >
          </span>
        </template>
        <template is="dom-if" if="[[_computeShowEditLinks(_editWeblinks)]]">
          <span class="separator"></span>
          <template is="dom-repeat" items="[[_editWeblinks]]" as="weblink">
            <a target="_blank" href$="[[weblink.url]]">[[weblink.name]]</a>
          </template>
        </template>
        <span class="separator"></span>
        <div class$="diffModeSelector [[_computeModeSelectHideClass(_diff)]]">
          <span>Diff view:</span>
          <gr-diff-mode-selector
            id="modeSelect"
            save-on-change="[[_loggedIn]]"
            show-tooltip-below=""
          ></gr-diff-mode-selector>
        </div>
        <span
          id="diffPrefsContainer"
          hidden$="[[_computePrefsButtonHidden(_prefs, _loggedIn)]]"
          hidden=""
        >
          <span class="preferences desktop">
            <gr-tooltip-content
              has-tooltip=""
              position-below=""
              title="Diff preferences"
            >
              <gr-button link="" class="prefsButton" on-click="_handlePrefsTap"
                ><iron-icon icon="gr-icons:settings"></iron-icon
              ></gr-button>
            </gr-tooltip-content>
          </span>
        </span>
        <gr-endpoint-decorator name="annotation-toggler">
          <span hidden="" id="annotation-span">
            <label for="annotation-checkbox" id="annotation-label"></label>
            <iron-input type="checkbox" disabled="">
              <input
                is="iron-input"
                type="checkbox"
                id="annotation-checkbox"
                disabled=""
              />
            </iron-input>
          </span>
        </gr-endpoint-decorator>
      </div>
    </div>
    <div class="fileNav mobile">
      <a
        class="mobileNavLink"
        href$="[[_computeNavLinkURL(_change, _path, _fileList, -1)]]"
      >
        &lt;</a
      >
      <div class="fullFileName mobile">[[_computeDisplayPath(_path)]]</div>
      <a
        class="mobileNavLink"
        href$="[[_computeNavLinkURL(_change, _path, _fileList, 1)]]"
      >
        &gt;</a
      >
    </div>
  </div>
  <div class="loading" hidden$="[[!_loading]]">Loading...</div>
  <h2 class="assistive-tech-only">Diff view</h2>
  <gr-diff-host
    id="diffHost"
    hidden=""
    hidden$="[[_loading]]"
    is-image-diff="{{_isImageDiff}}"
    edit-weblinks="{{_editWeblinks}}"
    files-weblinks="{{_filesWeblinks}}"
    diff="{{_diff}}"
    change-num="[[_changeNum]]"
    change="[[_change]]"
    commit-range="[[_commitRange]]"
    patch-range="[[_patchRange]]"
    file="[[_file]]"
    path="[[_path]]"
    prefs="[[_prefs]]"
    project-name="[[_change.project]]"
    is-blame-loaded="{{_isBlameLoaded}}"
    on-comment-anchor-tap="_onLineSelected"
    on-line-selected="_onLineSelected"
  >
  </gr-diff-host>
  <gr-apply-fix-dialog
    id="applyFixDialog"
    prefs="[[_prefs]]"
    change="[[_change]]"
    change-num="[[_changeNum]]"
  >
  </gr-apply-fix-dialog>
  <gr-diff-preferences-dialog
    id="diffPreferencesDialog"
    on-reload-diff-preference="_handleReloadingDiffPreference"
  >
  </gr-diff-preferences-dialog>
  <gr-overlay id="downloadOverlay">
    <gr-download-dialog
      id="downloadDialog"
      change="[[_change]]"
      patch-num="[[_patchRange.patchNum]]"
      config="[[_serverConfig.download]]"
      on-close="_handleDownloadDialogClose"
    ></gr-download-dialog>
  </gr-overlay>
`;
