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
(function() {
  'use strict';

  // Maximum length for patch set descriptions.
  const PATCH_DESC_MAX_LENGTH = 500;
  const MERGED_STATUS = 'MERGED';

  /**
    * @appliesMixin Gerrit.FireMixin
    * @appliesMixin Gerrit.PatchSetMixin
    */
  class GrFileListHeader extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.PatchSetBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-file-list-header'; }
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

    static get properties() {
      return {
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
        diffPrefsDisabled: Boolean,
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
        showTitle: {
          type: Boolean,
          value: true,
        },
        _descriptionReadOnly: {
          type: Boolean,
          computed: '_computeDescriptionReadOnly(loggedIn, change, account)',
        },
        revisionInfo: Object,
      };
    }

    static get observers() {
      return [
        '_computePatchSetDescription(change, patchNum)',
      ];
    }

    setDiffViewMode(mode) {
      this.$.modeSelect.setMode(mode);
    }

    _expandAllDiffs() {
      this._expanded = true;
      this.fire('expand-diffs');
    }

    _collapseAllDiffs() {
      this._expanded = false;
      this.fire('collapse-diffs');
    }

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
    }

    _computeDescriptionPlaceholder(readOnly) {
      return (readOnly ? 'No' : 'Add') + ' patchset description';
    }

    _computeDescriptionReadOnly(loggedIn, change, account) {
      // Polymer 2: check for undefined
      if ([loggedIn, change, account].some(arg => arg === undefined)) {
        return undefined;
      }

      return !(loggedIn && (account._account_id === change.owner._account_id));
    }

    _computePatchSetDescription(change, patchNum) {
      // Polymer 2: check for undefined
      if ([change, patchNum].some(arg => arg === undefined)) {
        return;
      }

      const rev = this.getRevisionByPatchNum(change.revisions, patchNum);
      this._patchsetDescription = (rev && rev.description) ?
        rev.description.substring(0, PATCH_DESC_MAX_LENGTH) : '';
    }

    _handleDescriptionRemoved(e) {
      return this._updateDescription('', e);
    }

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
    }

    _handleDescriptionChanged(e) {
      const desc = e.detail.trim();
      this._updateDescription(desc, e);
    }

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
    }

    _computePrefsButtonHidden(prefs, diffPrefsDisabled) {
      return diffPrefsDisabled || !prefs;
    }

    _fileListActionsVisible(shownFileCount, maxFilesForBulkActions) {
      return shownFileCount <= maxFilesForBulkActions;
    }

    _handlePatchChange(e) {
      const {basePatchNum, patchNum} = e.detail;
      if (this.patchNumEquals(basePatchNum, this.basePatchNum) &&
          this.patchNumEquals(patchNum, this.patchNum)) { return; }
      Gerrit.Nav.navigateToChange(this.change, patchNum, basePatchNum);
    }

    _handlePrefsTap(e) {
      e.preventDefault();
      this.fire('open-diff-prefs');
    }

    _handleIncludedInTap(e) {
      e.preventDefault();
      this.fire('open-included-in-dialog');
    }

    _handleDownloadTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.dispatchEvent(
          new CustomEvent('open-download-dialog', {bubbles: false}));
    }

    _computeEditModeClass(editMode) {
      return editMode ? 'editMode' : '';
    }

    _computePatchInfoClass(patchNum, allPatchSets) {
      const latestNum = this.computeLatestPatchNum(allPatchSets);
      if (this.patchNumEquals(patchNum, latestNum)) {
        return '';
      }
      return 'patchInfoOldPatchSet';
    }

    _hideIncludedIn(change) {
      return change && change.status === MERGED_STATUS ? '' : 'hide';
    }

    _handleUploadTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.dispatchEvent(
          new CustomEvent('open-upload-help-dialog', {bubbles: false}));
    }

    _computeUploadHelpContainerClass(change, account) {
      const changeIsMerged = change && change.status === MERGED_STATUS;
      const ownerId = change && change.owner && change.owner._account_id ?
        change.owner._account_id : null;
      const userId = account && account._account_id;
      const userIsOwner = ownerId && userId && ownerId === userId;
      const hideContainer = !userIsOwner || changeIsMerged;
      return 'uploadContainer desktop' + (hideContainer ? ' hide' : '');
    }
  }

  customElements.define(GrFileListHeader.is, GrFileListHeader);
})();
