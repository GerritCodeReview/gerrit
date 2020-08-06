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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '@polymer/iron-input/iron-input.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-cherrypick-dialog_html.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {appContext} from '../../../services/app-context.js';

const SUGGESTIONS_LIMIT = 15;
const CHANGE_SUBJECT_LIMIT = 50;
const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};

/**
 * @extends PolymerElement
 */
class GrConfirmCherrypickDialog extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
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
      // Status of each change that is being cherry picked together
      _statuses: Object,
      _invalidBranch: {
        type: Boolean,
        value: false,
      },
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
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
    this._statuses = {...this._statuses, [change.id]: status};
  }

  _computeStatus(change, statuses) {
    if (!change || !statuses || !statuses[change.id]) return 'NOT STARTED';
    return statuses[change.id].status;
  }

  _computeStatusClass(change, statuses) {
    if (!change || !statuses || !statuses[change.id]) return '';
    return statuses[change.id].status === 'FAILED' ? 'error': '';
  }

  _computeError(change, statuses) {
    if (!change || !statuses || !statuses[change.id]) return '';
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
    ].includes(undefined)) {
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
        allow_empty: true,
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
          /* This needs some more work, as the new topic may not always be
          created, instead we may end up creating a new patchset */
          GerritNav.navigateToSearchQuery(`topic: "${topic}"`);
        }
      });
    });
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    if (this._cherryPickType === CHERRY_PICK_TYPES.TOPIC) {
      this.reporting.reportInteraction('cherry-pick-topic-clicked');
      this._handleCherryPickTopic();
      return;
    }
    // Cherry pick single change
    this.dispatchEvent(new CustomEvent('confirm', {
      composed: true, bubbles: false,
    }));
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {
      composed: true, bubbles: false,
    }));
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
