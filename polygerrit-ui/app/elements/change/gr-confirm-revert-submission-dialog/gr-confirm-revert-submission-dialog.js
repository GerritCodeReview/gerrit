/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import '../../shared/gr-dialog/gr-dialog.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-confirm-revert-submission-dialog_html.js';

const ERR_COMMIT_NOT_FOUND =
    'Unable to find the commit hash of this change.';
const CHANGE_SUBJECT_LIMIT = 50;

/**
 * @extends PolymerElement
 */
class GrConfirmRevertSubmissionDialog extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-confirm-revert-submission-dialog'; }
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
      commitMessage: String,
    };
  }

  _getTrimmedChangeSubject(subject) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  _modifyRevertSubmissionMsg(change) {
    return this.$.jsAPI.modifyRevertSubmissionMsg(change,
        this.message, this.commitMessage);
  }

  _populateRevertSubmissionMessage(message, change, changes) {
    if (change === undefined) {
      return;
    }
    // Follow the same convention of the revert
    const commitHash = change.current_revision;
    if (!commitHash) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        detail: {message: ERR_COMMIT_NOT_FOUND},
        composed: true, bubbles: true,
      }));
      return;
    }
    const submissionId = change.submission_id;
    const revertTitle = 'Revert submission ' + submissionId;
    this.changes = changes;
    this.message = revertTitle + '\n\n' +
        'Reason for revert: <INSERT REASONING HERE>\n';
    changes = changes || [];
    if (changes.length) {
      this.message += 'Reverted Changes:\n';
      changes.forEach(change => {
        this.message += change.change_id.substring(0, 10) + ': ' +
          this._getTrimmedChangeSubject(change.subject) + '\n';
      });
    }
    this.message = this._modifyRevertSubmissionMsg(change);
  }

  _handleConfirmTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {
      composed: true, bubbles: true,
    }));
  }

  _handleCancelTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {
      composed: true, bubbles: true,
    }));
  }
}

customElements.define(GrConfirmRevertSubmissionDialog.is,
    GrConfirmRevertSubmissionDialog);
