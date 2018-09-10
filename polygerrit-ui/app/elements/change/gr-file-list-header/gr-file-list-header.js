/**
@license
Copyright (C) 2017 The Android Open Source Project

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
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../diff/gr-diff-mode-selector/gr-diff-mode-selector.js';
import '../../diff/gr-patch-range-select/gr-patch-range-select.js';
import '../../edit/gr-edit-controls/gr-edit-controls.js';
import '../../shared/gr-editable-label/gr-editable-label.js';
import '../../shared/gr-linked-chip/gr-linked-chip.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/revision-info/revision-info.js';
import '../gr-file-list-constants.js';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;
const MERGED_STATUS = 'MERGED';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .prefsButton {
        float: right;
      }
      .collapseToggleButton {
        text-decoration: none;
      }
      .patchInfoOldPatchSet.patchInfo-header {
        background-color: var(--emphasis-color);
      }
      .patchInfo-header {
        align-items: center;
        background-color: var(--table-header-background-color);
        border-top: 1px solid var(--border-color);
        display: flex;
        padding: 6px var(--default-horizontal-margin);
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
      .latestPatchContainer a {
        text-decoration: none;
      }
      gr-editable-label.descriptionLabel {
        max-width: 100%;
      }
      .mobile {
        display: none;
      }
      .patchInfo-header .container {
        align-items: center;
        display: flex;
      }
      .downloadContainer,
      .uploadContainer,
      .includedInContainer {
        margin-right: 16px;
      }
      .includedInContainer.hide,
      .uploadContainer.hide {
        display: none;
      }
      .rightControls {
        align-self: flex-end;
        margin: auto 0 auto auto;
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        font-weight: normal;
        justify-content: flex-end;
      }
      #collapseBtn,
      .expanded #expandBtn,
      .fileViewActions{
        display: none;
      }
      .expanded #expandBtn {
        display: none;
      }
      gr-linked-chip {
        --linked-chip-text-color: var(--primary-text-color);
      }
      .expanded #collapseBtn,
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
        --gr-button: {
          padding: 2px 4px;
        }
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
        font-family: var(--font-family-bold);
        margin-right: 24px;
      }
      gr-commit-info,
      gr-edit-controls {
        margin-right: -5px;
      }
      .fileViewActionsLabel {
        margin-right: .2rem;
      }
      @media screen and (max-width: 50em) {
        .patchInfo-header .desktop {
          display: none;
        }
      }
    </style>
    <div class\$="patchInfo-header [[_computeEditModeClass(editMode)]] [[_computePatchInfoClass(patchNum, allPatchSets)]]">
      <div class="patchInfo-left">
        <h3 class="label">Files</h3>
        <div class="patchInfoContent">
          <gr-patch-range-select id="rangeSelect" change-comments="[[changeComments]]" change-num="[[changeNum]]" patch-num="[[patchNum]]" base-patch-num="[[basePatchNum]]" available-patches="[[allPatchSets]]" revisions="[[change.revisions]]" revision-info="[[_revisionInfo]]" on-patch-range-change="_handlePatchChange">
          </gr-patch-range-select>
          <span class="separator"></span>
          <gr-commit-info change="[[change]]" server-config="[[serverConfig]]" commit-info="[[commitInfo]]"></gr-commit-info>
          <span class="container latestPatchContainer">
            <span class="separator"></span>
            <a href\$="[[changeUrl]]">Go to latest patch set</a>
          </span>
          <span class="container descriptionContainer hideOnEdit">
            <span class="separator"></span>
            <template is="dom-if" if="[[_patchsetDescription]]">
              <gr-linked-chip id="descriptionChip" text="[[_patchsetDescription]]" removable="[[!_descriptionReadOnly]]" on-remove="_handleDescriptionRemoved"></gr-linked-chip>
            </template>
            <template is="dom-if" if="[[!_patchsetDescription]]">
              <gr-editable-label id="descriptionLabel" uppercase="" class="descriptionLabel" label-text="Add patchset description" value="[[_patchsetDescription]]" placeholder="[[_computeDescriptionPlaceholder(_descriptionReadOnly)]]" read-only="[[_descriptionReadOnly]]" on-changed="_handleDescriptionChanged"></gr-editable-label>
            </template>
          </span>
        </div>
      </div>
      <div class\$="rightControls [[_computeExpandedClass(filesExpanded)]]">
        <span class="showOnEdit flexContainer">
          <gr-edit-controls id="editControls" patch-num="[[patchNum]]" change="[[change]]"></gr-edit-controls>
          <span class="separator"></span>
        </span>
        <span class\$="[[_computeUploadHelpContainerClass(change, account)]]">
          <gr-button link="" class="upload" on-tap="_handleUploadTap">Update Change</gr-button>
        </span>
        <span class="downloadContainer desktop">
          <gr-button link="" class="download" on-tap="_handleDownloadTap">Download</gr-button>
        </span>
        <span class\$="includedInContainer [[_hideIncludedIn(change)]] desktop">
          <gr-button link="" class="includedIn" on-tap="_handleIncludedInTap">Included In</gr-button>
        </span>
        <template is="dom-if" if="[[_fileListActionsVisible(shownFileCount, _maxFilesForBulkActions)]]">
          <gr-button id="expandBtn" link="" on-tap="_expandAllDiffs">Expand All</gr-button>
          <gr-button id="collapseBtn" link="" on-tap="_collapseAllDiffs">Collapse All</gr-button>
        </template>
        <template is="dom-if" if="[[!_fileListActionsVisible(shownFileCount, _maxFilesForBulkActions)]]">
          <div class="warning">
            Bulk actions disabled because there are too many files.
          </div>
        </template>
        <div class="fileViewActions">
          <span class="separator"></span>
          <span class="fileViewActionsLabel">Diff view:</span>
          <gr-diff-mode-selector id="modeSelect" mode="{{diffViewMode}}" save-on-change="[[loggedIn]]"></gr-diff-mode-selector>
          <span id="diffPrefsContainer" class="hideOnEdit" hidden\$="[[_computePrefsButtonHidden(diffPrefs, loggedIn)]]" hidden="">
            <gr-button link="" has-tooltip="" title="Diff preferences" class="prefsButton desktop" on-tap="_handlePrefsTap"><iron-icon icon="gr-icons:settings"></iron-icon></gr-button>
          </span>
        </div>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-file-list-header',

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

  /**
   * @event open-upload-help-dialog
   */

  properties: {
    account: Object,
    allPatchSets: Array,
    /** @type {?} */
    change: Object,
    changeNum: String,
    changeUrl: String,
    changeComments: Object,
    commitInfo: Object,
    editMode: Boolean,
    loggedIn: Boolean,
    serverConfig: Object,
    shownFileCount: Number,
    diffPrefs: Object,
    diffViewMode: {
      type: String,
      notify: true,
    },
    patchNum: String,
    basePatchNum: String,
    filesExpanded: String,
    // Caps the number of files that can be shown and have the 'show diffs' /
    // 'hide diffs' buttons still be functional.
    _maxFilesForBulkActions: {
      type: Number,
      readOnly: true,
      value: 225,
    },
    _patchsetDescription: {
      type: String,
      value: '',
    },
    _descriptionReadOnly: {
      type: Boolean,
      computed: '_computeDescriptionReadOnly(loggedIn, change, account)',
    },
    _revisionInfo: {
      type: Object,
      computed: '_getRevisionInfo(change)',
    },
  },

  behaviors: [
    Gerrit.PatchSetBehavior,
  ],

  observers: [
    '_computePatchSetDescription(change, patchNum)',
  ],

  setDiffViewMode(mode) {
    this.$.modeSelect.setMode(mode);
  },

  _expandAllDiffs() {
    this._expanded = true;
    this.fire('expand-diffs');
  },

  _collapseAllDiffs() {
    this._expanded = false;
    this.fire('collapse-diffs');
  },

  _computeExpandedClass(filesExpanded) {
    const classes = [];
    if (filesExpanded === GrFileListConstants.FilesExpandedState.ALL) {
      classes.push('expanded');
    }
    if (filesExpanded === GrFileListConstants.FilesExpandedState.SOME ||
          filesExpanded === GrFileListConstants.FilesExpandedState.ALL) {
      classes.push('openFile');
    }
    return classes.join(' ');
  },

  _computeDescriptionPlaceholder(readOnly) {
    return (readOnly ? 'No' : 'Add') + ' patchset description';
  },

  _computeDescriptionReadOnly(loggedIn, change, account) {
    return !(loggedIn && (account._account_id === change.owner._account_id));
  },

  _computePatchSetDescription(change, patchNum) {
    const rev = this.getRevisionByPatchNum(change.revisions, patchNum);
    this._patchsetDescription = (rev && rev.description) ?
        rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
  },

  _handleDescriptionRemoved(e) {
    return this._updateDescription('', e);
  },

  /**
   * @param {!Object} revisions The revisions object keyed by revision hashes
   * @param {?Object} patchSet A revision already fetched from {revisions}
   * @return {string|undefined} the SHA hash corresponding to the revision.
   */
  _getPatchsetHash(revisions, patchSet) {
    for (const rev in revisions) {
      if (revisions.hasOwnProperty(rev) &&
          revisions[rev] === patchSet) {
        return rev;
      }
    }
  },

  _handleDescriptionChanged(e) {
    const desc = e.detail.trim();
    this._updateDescription(desc, e);
  },

  /**
   * Update the patchset description with the rest API.
   * @param {string} desc
   * @param {?(Event|Node)} e
   * @return {!Promise}
   */
  _updateDescription(desc, e) {
    const target = Polymer.dom(e).rootTarget;
    if (target) { target.disabled = true; }
    const rev = this.getRevisionByPatchNum(this.change.revisions,
        this.patchNum);
    const sha = this._getPatchsetHash(this.change.revisions, rev);
    return this.$.restAPI.setDescription(this.changeNum, this.patchNum, desc)
        .then(res => {
          if (res.ok) {
            if (target) { target.disabled = false; }
            this.set(['change', 'revisions', sha, 'description'], desc);
            this._patchsetDescription = desc;
          }
        }).catch(err => {
          if (target) { target.disabled = false; }
          return;
        });
  },

  _computePrefsButtonHidden(prefs, loggedIn) {
    return !loggedIn || !prefs;
  },

  _fileListActionsVisible(shownFileCount, maxFilesForBulkActions) {
    return shownFileCount <= maxFilesForBulkActions;
  },

  _handlePatchChange(e) {
    const {basePatchNum, patchNum} = e.detail;
    if (this.patchNumEquals(basePatchNum, this.basePatchNum) &&
        this.patchNumEquals(patchNum, this.patchNum)) { return; }
    Gerrit.Nav.navigateToChange(this.change, patchNum, basePatchNum);
  },

  _handlePrefsTap(e) {
    e.preventDefault();
    this.fire('open-diff-prefs');
  },

  _handleIncludedInTap(e) {
    e.preventDefault();
    this.fire('open-included-in-dialog');
  },

  _handleDownloadTap(e) {
    e.preventDefault();
    this.dispatchEvent(
        new CustomEvent('open-download-dialog', {bubbles: false}));
  },

  _computeEditModeClass(editMode) {
    return editMode ? 'editMode' : '';
  },

  _computePatchInfoClass(patchNum, allPatchSets) {
    const latestNum = this.computeLatestPatchNum(allPatchSets);
    if (this.patchNumEquals(patchNum, latestNum)) {
      return '';
    }
    return 'patchInfoOldPatchSet';
  },

  _getRevisionInfo(change) {
    return new Gerrit.RevisionInfo(change);
  },

  _hideIncludedIn(change) {
    return change && change.status === MERGED_STATUS ? '' : 'hide';
  },

  _handleUploadTap(e) {
    e.preventDefault();
    this.dispatchEvent(
        new CustomEvent('open-upload-help-dialog', {bubbles: false}));
  },

  _computeUploadHelpContainerClass(change, account) {
    const changeIsMerged = change && change.status === MERGED_STATUS;
    const ownerId = change && change.owner && change.owner._account_id ?
        change.owner._account_id : null;
    const userId = account && account._account_id;
    const userIsOwner = ownerId && userId && ownerId === userId;
    const hideContainer = !userIsOwner || changeIsMerged;
    return 'uploadContainer desktop' + (hideContainer ? ' hide' : '');
  }
});
