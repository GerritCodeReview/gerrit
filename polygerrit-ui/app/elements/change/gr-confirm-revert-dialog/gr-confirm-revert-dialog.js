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
(function() {
  'use strict';

  const ERR_COMMIT_NOT_FOUND =
      'Unable to find the commit hash of this change.';
  const CHANGE_SUBJECT_LIMIT = 50;

  // TODO(dhruvsri): clean up repeated definitions after moving to js modules
  const REVERT_TYPES = {
    REVERT_SINGLE_CHANGE: 1,
    REVERT_SUBMISSION: 2,
  };

  /**
   * @appliesMixin Gerrit.FireMixin
   * @extends Polymer.Element
   */
  class GrConfirmRevertDialog extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-confirm-revert-dialog'; }
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
        /* The revert message updated by the user
        The default value is set by the dialog */
        message: String,
        _revertType: {
          type: Number,
          value: REVERT_TYPES.REVERT_SINGLE_CHANGE,
        },
        _showRevertSubmission: {
          type: Boolean,
          value: false,
        },
        /* List of changes with the same topic
        value is empty if only a single change is being reverted */
        changes: {
          type: Array,
          value() { return []; },
        },
        change: Object,
        // commit message is _latestCommitMessage coming from gr-change-view
        commitMessage: String,
        _showErrorMessage: {
          type: Boolean,
          value: false,
        },
        /* store the default revert messages per revert type so that we can
        check if user has edited the revert message or not */
        _originalRevertMessages: {
          type: Array,
          value() { return []; },
        },
      };
    }

    static get observers() {
      return [
        'onInputUpdate(change, commitMessage, changes)',
      ];
    }

    _computeIfSingleRevert(revertType) {
      return revertType === REVERT_TYPES.REVERT_SINGLE_CHANGE;
    }

    _computeIfRevertSubmission(revertType) {
      return revertType === REVERT_TYPES.REVERT_SUBMISSION;
    }

    _modifyRevertMsg(change, commitMessage, message) {
      return this.$.jsAPI.modifyRevertMsg(change,
          message, commitMessage);
    }

    onInputUpdate(change, commitMessage, changes) {
      if (!change || !changes) return;
      // The option to revert a single change is always available
      this._populateRevertSingleChangeMessage(
          change, commitMessage, change.current_revision);
      if (changes.length > 1) {
        this._populateRevertSubmissionMessage(
            change, changes);
      }
    }

    _populateRevertSingleChangeMessage(change, commitMessage, commitHash) {
      // Figure out what the revert title should be.
      const originalTitle = (commitMessage || '').split('\n')[0];
      const revertTitle = `Revert "${originalTitle}"`;
      if (!commitHash) {
        this.fire('show-alert', {message: ERR_COMMIT_NOT_FOUND});
        return;
      }
      const revertCommitText = `This reverts commit ${commitHash}.`;

      this.revertSingleChangeMessage =
          `${revertTitle}\n\n${revertCommitText}\n\n` +
          `Reason for revert: <INSERT REASONING HERE>\n`;
      // This is to give plugins a chance to update message
      this.revertSingleChangeMessage =
          this._modifyRevertMsg(change, commitMessage,
              this.revertSingleChangeMessage);
      this.message = this.revertSingleChangeMessage;
      this._originalRevertMessages[REVERT_TYPES.REVERT_SINGLE_CHANGE] =
        this.message;
    }

    _getTrimmedChangeSubject(subject) {
      if (!subject) return '';
      if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
      return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
    }

    _modifyRevertSubmissionMsg(change) {
      return this.$.jsAPI.modifyRevertSubmissionMsg(change,
          this.revertSubmissionMessage, this.commitMessage);
    }

    _populateRevertSubmissionMessage(change, changes) {
      // Follow the same convention of the revert
      const commitHash = change.current_revision;
      if (!commitHash) {
        this.fire('show-alert', {message: ERR_COMMIT_NOT_FOUND});
        return;
      }
      if (!changes || changes.length <= 1) return;
      const submissionId = change.submission_id;
      const revertTitle = 'Revert submission ' + submissionId;
      this.changes = changes;
      this.revertSubmissionMessage = revertTitle + '\n\n' +
          'Reason for revert: <INSERT REASONING HERE>\n';
      this.revertSubmissionMessage += 'Reverted Changes:\n';
      changes.forEach(change => {
        this.revertSubmissionMessage += change.change_id.substring(0, 10) + ':'
          + this._getTrimmedChangeSubject(change.subject) + '\n';
      });
      this.revertSubmissionMessage = this._modifyRevertSubmissionMsg(change);
      this.message = this.revertSubmissionMessage;
      this._revertType = REVERT_TYPES.REVERT_SUBMISSION;
      this._originalRevertMessages[this._revertType] = this.message;
      this._showRevertSubmission = true;
    }

    _handleRevertSingleChangeClicked() {
      this._showErrorMessage = false;
      if (this._revertType === REVERT_TYPES.REVERT_SINGLE_CHANGE) return;
      this.revertSubmissionMessage = this.message;
      this.message = this.revertSingleChangeMessage;
      this._revertType = REVERT_TYPES.REVERT_SINGLE_CHANGE;
    }

    _handleRevertSubmissionClicked() {
      this._showErrorMessage = false;
      if (this._revertType === REVERT_TYPES.REVERT_SUBMISSION) return;
      this._revertType = REVERT_TYPES.REVERT_SUBMISSION;
      this.revertSingleChangeMessage = this.message;
      this.message = this.revertSubmissionMessage;
    }

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      if (this.message === this._originalRevertMessages[this._revertType]) {
        this._showErrorMessage = true;
        return;
      }
      this.fire('confirm', {revertType: this._revertType,
        message: this.message}, {bubbles: false});
    }

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', {revertType: this._revertType},
          {bubbles: false});
    }
  }

  customElements.define(GrConfirmRevertDialog.is, GrConfirmRevertDialog);
})();
