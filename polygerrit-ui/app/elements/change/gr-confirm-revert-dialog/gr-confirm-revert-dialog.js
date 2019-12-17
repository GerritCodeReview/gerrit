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

  /**
    * @appliesMixin Gerrit.FireMixin
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
        message: String,
        _revertSingleChange: {
          type: Boolean,
          value: true,
        },
        _showRevertSubmission: {
          type: Boolean,
          value: false,
        },
      };
    }

    populateRevertSingleChangeMessage(message, commitHash) {
      // Figure out what the revert title should be.
      const originalTitle = message.split('\n')[0];
      const revertTitle = `Revert "${originalTitle}"`;
      if (!commitHash) {
        this.fire('show-alert', {message: ERR_COMMIT_NOT_FOUND});
        return;
      }
      const revertCommitText = `This reverts commit ${commitHash}.`;

      this.revertSingleChangeMessage =
          `${revertTitle}\n\n${revertCommitText}\n\n` +
          `Reason for revert: <INSERT REASONING HERE>\n`;
      this.message = this.revertSingleChangeMessage;
    }

    getTrimmedChangeSubject(subject) {
      if (!subject) return '';
      if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
      return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
    }

    _modifyRevertSubmissionMsg(change) {
      return this.$.jsAPI.modifyRevertSubmissionMsg(change,
          this.revertSubmissionMessage, this.commitMessage);
    }

    populateRevertSubmissionMessage(change, changes) {
      // Follow the same convention of the revert
      const commitHash = change.current_revision;
      if (!commitHash) {
        this.fire('show-alert', {message: ERR_COMMIT_NOT_FOUND});
        return;
      }
      const submissionId = change.submission_id;
      const revertTitle = 'Revert submission ' + submissionId;
      this.changes = changes;
      this.revertSubmissionMessage = revertTitle + '\n\n' +
          'Reason for revert: <INSERT REASONING HERE>\n';
      this.revertSubmissionMessage += 'Reverted Changes:\n';
      changes = changes || [];
      changes.forEach(change => {
        this.revertSubmissionMessage += change.change_id.substring(0, 10) + ':'
          + this.getTrimmedChangeSubject(change.subject) + '\n';
      });
      this.revertSubmissionMessage = this._modifyRevertSubmissionMsg(change);
      this.message = this.revertSubmissionMessage;
      this._revertSingleChange = false;
      this._showRevertSubmission = true;
    }

    _handleRevertSingleChangeClicked() {
      if (this._revertSingleChange) return;
      this.revertSubmissionMessage = this.message;
      this.message = this.revertSingleChangeMessage;
      this._revertSingleChange = true;
    }

    _handleRevertSubmissionClicked() {
      if (!this._revertSingleChange) return;
      this._revertSingleChange = false;
      this.revertSingleChangeMessage = this.message;
      this.message = this.revertSubmissionMessage;
    }

    _handleConfirmTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('confirm', {revertSingleChange: this._revertSingleChange},
          {bubbles: false});
    }

    _handleCancelTap(e) {
      e.preventDefault();
      e.stopPropagation();
      this.fire('cancel', {revertSingleChange: this._revertSingleChange},
          {bubbles: false});
    }
  }

  customElements.define(GrConfirmRevertDialog.is, GrConfirmRevertDialog);
})();
