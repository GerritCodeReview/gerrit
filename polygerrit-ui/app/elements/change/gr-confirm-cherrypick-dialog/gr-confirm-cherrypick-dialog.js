/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../../scripts/bundled-polymer.js';

import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '@polymer/iron-input/iron-input.js';
import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-cherrypick-dialog_html.js';

const SUGGESTIONS_LIMIT = 15;
const CHANGE_SUBJECT_LIMIT = 50;
const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};

/**
 * @appliesMixin Gerrit.FireMixin
 * @extends Polymer.Element
 */
class GrConfirmCherrypickDialog extends mixinBehaviors( [
  Gerrit.FireBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-cherrypick-dialog'; }
  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  static get properties() {
    return {
      branch: {
        type: String,
        observer: '_updateBranch',
      },
      baseCommit: String,
      changeStatus: String,
      commitMessage: String,
      commitNum: String,
      message: String,
      project: String,
      changes: Array,
      _query: {
        type: Function,
        value() {
          return this._getProjectBranchesSuggestions.bind(this);
        },
      },
      _showCherryPickTopic: {
        type: Boolean,
        value: false,
      },
      _changesCount: Number,
      _cherryPickType: {
        type: Number,
        value: CHERRY_PICK_TYPES.SINGLE_CHANGE,
      },
      _duplicateProjectChanges: {
        type: Boolean,
        value: false,
      },
      statusCherryPick: {
        type: Object,
      },
      _statuses: Object,
      _invalidBranch: {
        type: Boolean,
        value: false,
      },
    };
  }

  static get observers() {
    return [
      '_computeMessage(changeStatus, commitNum, commitMessage)',
    ];
  }

  updateChanges(changes) {
    this.changes = changes;
    this._statuses = {};
    const projects = {};
    this._duplicateProjectChanges = false;
    changes.forEach(change => {
      if (projects[change.project]) {
        this._duplicateProjectChanges = true;
      }
      projects[change.project] = true;
    });
    this._changesCount = changes.length;
    this._showCherryPickTopic = changes.length > 1;
  }

  _updateBranch(branch) {
    const invalidChars = [',', ' '];
    this._invalidBranch = branch && invalidChars.some(c => branch.includes(c));
  }

  _computeTopicErrorMessage(duplicateProjectChanges) {
    if (duplicateProjectChanges) {
      return 'Two changes cannot be of the same project';
    }
  }

  updateStatus(change, status) {
    this._statuses = Object.assign({}, this._statuses, {[change.id]: status});
  }

  _computeStatus(change, statuses) {
    if (!statuses || !statuses[change.id]) return 'NOT STARTED';
    return statuses[change.id].status;
  }

  _computeError(change, statuses) {
    if (!statuses || !statuses[change.id]) return '';
    if (statuses[change.id].status === 'FAILED') {
      return statuses[change.id].msg;
    }
  }

  _getChangeId(change) {
    return change.change_id.substring(0, 10);
  }

  _getTrimmedChangeSubject(subject) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  _computeCancelLabel(statuses) {
    const isRunningChange = Object.values(statuses).
        some(v => v.status === 'RUNNING');
    return isRunningChange ? 'Close' : 'Cancel';
  }

  _computeDisableCherryPick(cherryPickType, duplicateProjectChanges,
      statuses) {
    const duplicateProject = (cherryPickType === CHERRY_PICK_TYPES.TOPIC) &&
      duplicateProjectChanges;
    if (duplicateProject) return true;
    if (!statuses) return false;
    const isRunningChange = Object.values(statuses).
        some(v => v.status === 'RUNNING');
    return isRunningChange;
  }

  _computeIfSinglecherryPick(cherryPickType) {
    return cherryPickType === CHERRY_PICK_TYPES.SINGLE_CHANGE;
  }

  _computeIfCherryPickTopic(cherryPickType) {
    return cherryPickType === CHERRY_PICK_TYPES.TOPIC;
  }

  _handlecherryPickSingleChangeClicked(e) {
    this._cherryPickType = CHERRY_PICK_TYPES.SINGLE_CHANGE;
  }

  _handlecherryPickTopicClicked(e) {
    this._cherryPickType = CHERRY_PICK_TYPES.TOPIC;
  }

  _computeMessage(changeStatus, commitNum, commitMessage) {
    // Polymer 2: check for undefined
    if ([
      changeStatus,
      commitNum,
      commitMessage,
    ].some(arg => arg === undefined)) {
      return;
    }

    let newMessage = commitMessage;

    if (changeStatus === 'MERGED') {
      newMessage += '(cherry picked from commit ' + commitNum + ')';
    }
    this.message = newMessage;
  }

  _generateRandomCherryPickTopic(change) {
    const randomString = Math.random().toString(36)
        .substr(2, 10);
    const message = `cherrypick-${change.topic}-${randomString}`;
    return message;
  }

  _handleCherryPickFailed(change, response) {
    response.text().then(errText => {
      this.updateStatus(change,
          {status: 'FAILED', msg: errText});
    });
  }

  _handleCherryPickTopic() {
    const topic = this._generateRandomCherryPickTopic(
        this.changes[0]);
    this.changes.forEach(change => {
      this.updateStatus(change,
          {status: 'RUNNING'});
      const payload = {
        destination: this.branch,
        base: null,
        topic,
        allow_conflicts: true,
      };
      const handleError = response => {
        this._handleCherryPickFailed(change, response);
      };
      const patchNum = change.revisions[change.current_revision]._number;
      this.$.restAPI.executeChangeAction(change._number, 'POST', '/cherrypick',
          patchNum, payload, handleError).then(response => {
        this.updateStatus(change, {status: 'SUCCESSFUL'});
        const failedOrPending = Object.values(this._statuses).find(
            v => v.status !== 'SUCCESSFUL');
        if (!failedOrPending) {
          Gerrit.Nav.navigateToSearchQuery(`topic: "${change.topic}"`);
        }
      });
    });
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    if (this._cherryPickType === CHERRY_PICK_TYPES.TOPIC) {
      this._handleCherryPickTopic();
      return;
    }
    this.fire('confirm', {type: this._cherryPickType, branch: this.branch},
        {bubbles: false});
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.fire('cancel', null, {bubbles: false});
  }

  resetFocus() {
    this.$.branchInput.focus();
  }

  _getProjectBranchesSuggestions(input) {
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.$.restAPI.getRepoBranches(
        input, this.project, SUGGESTIONS_LIMIT).then(response => {
      const branches = [];
      let branch;
      for (const key in response) {
        if (!response.hasOwnProperty(key)) { continue; }
        if (response[key].ref.startsWith('refs/heads/')) {
          branch = response[key].ref.substring('refs/heads/'.length);
        } else {
          branch = response[key].ref;
        }
        branches.push({
          name: branch,
        });
      }
      return branches;
    });
  }
}

customElements.define(GrConfirmCherrypickDialog.is,
    GrConfirmCherrypickDialog);
