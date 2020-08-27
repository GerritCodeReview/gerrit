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
import '../../shared/gr-editable-label/gr-editable-label';
import '../../shared/gr-linked-chip/gr-linked-chip';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../gr-commit-info/gr-commit-info';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-file-list-header_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {GrFileListConstants} from '../gr-file-list-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  computeLatestPatchNum,
  getRevisionByPatchNum,
  patchNumEquals,
} from '../../../utils/patch-set-util';
import { property, computed } from '@polymer/decorators';
import { AccountInfo, ChangeInfo, PatchSetNum } from '../../../types/common';
import { RevisionInfo } from '../../shared/revision-info/revision-info';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;
const MERGED_STATUS = 'MERGED';

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-header': GrFileListHeader;
  }
}
export class GrFileListHeader extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
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

  /**
   * @event open-upload-help-dialog
   */

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Array})
  allPatchSets: 

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  changeNum: 

  @property({type: String})
  changeUrl: 

  @property({type: Object})
  changeComments: 

  @property({type: Object})
  commitInfo: 

  @property({type: Boolean})
  editMode: 

  @property({type: Boolean})
  loggedIn: 

  @property({type: Object})
  serverConfig: 

  @property({type: Number})
  shownFileCount: 

  @property({type: Object})
  diffPrefs: 

  @property({type: Boolean})
  diffPrefsDisabled: 

  @property({type: String, notify: true})
  diffViewMode?: string;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  basePatchNum?: PatchSetNum;

  @property({type: String})
  filesExpanded?: string;


  // Caps the number of files that can be shown and have the 'show diffs' /
  // 'hide diffs' buttons still be functional.
  @property({type: Number, readOnly: true})
  _maxFilesForBulkActions = 225;

  @property({type: String})
  _patchsetDescription = '';

  @property({type: Object})
  revisionInfo: RevisionInfo;

  @computed('loggedIn', 'change', 'account')
  get _descriptionReadOnly() {
    // Polymer 2: check for undefined
    if ([this.loggedIn, this.change, this.account].includes(undefined)) {
      return undefined;
    }

    return !(this.loggedIn && this.account._account_id === this.change.owner._account_id);
  }

  static get observers() {
    return ['_computePatchSetDescription(change, patchNum)'];
  }

  setDiffViewMode(mode) {
    this.$.modeSelect.setMode(mode);
  }

  _expandAllDiffs() {
    this._expanded = true;
    this.dispatchEvent(
      new CustomEvent('expand-diffs', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _collapseAllDiffs() {
    this._expanded = false;
    this.dispatchEvent(
      new CustomEvent('collapse-diffs', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _computeExpandedClass(filesExpanded) {
    const classes = [];
    if (filesExpanded === GrFileListConstants.FilesExpandedState.ALL) {
      classes.push('expanded');
    }
    if (
      filesExpanded === GrFileListConstants.FilesExpandedState.SOME ||
      filesExpanded === GrFileListConstants.FilesExpandedState.ALL
    ) {
      classes.push('openFile');
    }
    return classes.join(' ');
  }

  _computeDescriptionPlaceholder(readOnly) {
    return (readOnly ? 'No' : 'Add') + ' patchset description';
  }

  _computePatchSetDescription(change, patchNum) {
    // Polymer 2: check for undefined
    if ([change, patchNum].includes(undefined)) {
      return;
    }

    const rev = getRevisionByPatchNum(change.revisions, patchNum);
    this._patchsetDescription =
      rev && rev.description
        ? rev.description.substring(0, PATCH_DESC_MAX_LENGTH)
        : '';
  }

  _handleDescriptionRemoved(e) {
    return this._updateDescription('', e);
  }

  /**
   * @param revisions The revisions object keyed by revision hashes
   * @param patchSet A revision already fetched from {revisions}
   * @return the SHA hash corresponding to the revision.
   */
  _getPatchsetHash(revisions, patchSet) {
    for (const rev in revisions) {
      if (revisions.hasOwnProperty(rev) && revisions[rev] === patchSet) {
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
   *
   * @param desc
   * @param e
   * @return
   */
  _updateDescription(desc, e) {
    const target = dom(e).rootTarget;
    if (target) {
      target.disabled = true;
    }
    const rev = getRevisionByPatchNum(this.change.revisions, this.patchNum);
    const sha = this._getPatchsetHash(this.change.revisions, rev);
    return this.$.restAPI
      .setDescription(this.changeNum, this.patchNum, desc)
      .then(res => {
        if (res.ok) {
          if (target) {
            target.disabled = false;
          }
          this.set(['change', 'revisions', sha, 'description'], desc);
          this._patchsetDescription = desc;
        }
      })
      .catch(err => {
        if (target) {
          target.disabled = false;
        }
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
    if (
      patchNumEquals(basePatchNum, this.basePatchNum) &&
      patchNumEquals(patchNum, this.patchNum)
    ) {
      return;
    }
    GerritNav.navigateToChange(this.change, patchNum, basePatchNum);
  }

  _handlePrefsTap(e) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('open-diff-prefs', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _handleIncludedInTap(e) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('open-included-in-dialog', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _handleDownloadTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('open-download-dialog', {bubbles: false})
    );
  }

  _computeEditModeClass(editMode) {
    return editMode ? 'editMode' : '';
  }

  _computePatchInfoClass(patchNum, allPatchSets) {
    const latestNum = computeLatestPatchNum(allPatchSets);
    if (patchNumEquals(patchNum, latestNum)) {
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
      new CustomEvent('open-upload-help-dialog', {bubbles: false})
    );
  }

  _computeUploadHelpContainerClass(change, account) {
    const changeIsMerged = change && change.status === MERGED_STATUS;
    const ownerId =
      change && change.owner && change.owner._account_id
        ? change.owner._account_id
        : null;
    const userId = account && account._account_id;
    const userIsOwner = ownerId && userId && ownerId === userId;
    const hideContainer = !userIsOwner || changeIsMerged;
    return 'uploadContainer desktop' + (hideContainer ? ' hide' : '');
  }
}

customElements.define(GrFileListHeader.is, GrFileListHeader);
