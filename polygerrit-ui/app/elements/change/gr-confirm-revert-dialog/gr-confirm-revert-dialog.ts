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
import '../../shared/gr-dialog/gr-dialog';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {ChangeInfo, CommitId} from '../../../types/common';
import {fireAlert} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {html} from '@polymer/polymer/lib/utils/html-tag';

const ERR_COMMIT_NOT_FOUND = 'Unable to find the commit hash of this change.';
const CHANGE_SUBJECT_LIMIT = 50;
const INSERT_REASON_STRING = '<INSERT REASONING HERE>';

// TODO(dhruvsri): clean up repeated definitions after moving to js modules
export enum RevertType {
  REVERT_SINGLE_CHANGE = 1,
  REVERT_SUBMISSION = 2,
}

export interface ConfirmRevertEventDetail {
  revertType: RevertType;
  message?: string;
}

@customElement('gr-confirm-revert-dialog')
export class GrConfirmRevertDialog extends PolymerElement {
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

  /* The revert message updated by the user
      The default value is set by the dialog */
  @property({type: String})
  _message = '';

  @property({type: Number})
  _revertType = RevertType.REVERT_SINGLE_CHANGE;

  @property({type: Boolean})
  _showRevertSubmission = false;

  @property({type: Number})
  _changesCount?: number;

  @property({type: Boolean})
  _showErrorMessage = false;

  /* store the default revert messages per revert type so that we can
  check if user has edited the revert message or not
  Set when populate() is called */
  @property({type: Array})
  _originalRevertMessages: string[] = [];

  // Store the actual messages that the user has edited
  @property({type: Array})
  _revertMessages: string[] = [];

  static get template() {
    return html`
      <style include="shared-styles">
        :host {
          display: block;
        }
        :host([disabled]) {
          opacity: 0.5;
          pointer-events: none;
        }
        label {
          cursor: pointer;
          display: block;
          width: 100%;
        }
        .revertSubmissionLayout {
          display: flex;
          align-items: center;
        }
        .label {
          margin-left: var(--spacing-m);
        }
        iron-autogrow-textarea {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 73ch; /* Add a char to account for the border. */
        }
        .error {
          color: var(--error-text-color);
          margin-bottom: var(--spacing-m);
        }
        label[for='messageInput'] {
          margin-top: var(--spacing-m);
        }
      </style>
      <gr-dialog
        confirm-label="Revert"
        on-confirm="_handleConfirmTap"
        on-cancel="_handleCancelTap"
      >
        <div class="header" slot="header">Revert Merged Change</div>
        <div class="main" slot="main">
          <div class="error" hidden$="[[!_showErrorMessage]]">
            <span> A reason is required </span>
          </div>
          <template is="dom-if" if="[[_showRevertSubmission]]">
            <div class="revertSubmissionLayout">
              <input
                name="revertOptions"
                type="radio"
                id="revertSingleChange"
                on-change="_handleRevertSingleChangeClicked"
                checked="[[_computeIfSingleRevert(_revertType)]]"
              />
              <label for="revertSingleChange" class="label revertSingleChange">
                Revert single change
              </label>
            </div>
            <div class="revertSubmissionLayout">
              <input
                name="revertOptions"
                type="radio"
                id="revertSubmission"
                on-change="_handleRevertSubmissionClicked"
                checked="[[_computeIfRevertSubmission(_revertType)]]"
              />
              <label for="revertSubmission" class="label revertSubmission">
                Revert entire submission ([[_changesCount]] Changes)
              </label>
            </div>
          </template>
          <gr-endpoint-decorator name="confirm-revert-change">
            <label for="messageInput"> Revert Commit Message </label>
            <iron-autogrow-textarea
              id="messageInput"
              class="message"
              autocomplete="on"
              max-rows="15"
              bind-value="{{_message}}"
            ></iron-autogrow-textarea>
          </gr-endpoint-decorator>
        </div>
      </gr-dialog>
    `;
  }

  private readonly jsAPI = getAppContext().jsApiService;

  _computeIfSingleRevert(revertType: number) {
    return revertType === RevertType.REVERT_SINGLE_CHANGE;
  }

  _computeIfRevertSubmission(revertType: number) {
    return revertType === RevertType.REVERT_SUBMISSION;
  }

  _modifyRevertMsg(change: ChangeInfo, commitMessage: string, message: string) {
    return this.jsAPI.modifyRevertMsg(change, message, commitMessage);
  }

  populate(change: ChangeInfo, commitMessage: string, changes: ChangeInfo[]) {
    this._changesCount = changes.length;
    // The option to revert a single change is always available
    this._populateRevertSingleChangeMessage(
      change,
      commitMessage,
      change.current_revision
    );
    this._populateRevertSubmissionMessage(change, changes, commitMessage);
  }

  _populateRevertSingleChangeMessage(
    change: ChangeInfo,
    commitMessage: string,
    commitHash?: CommitId
  ) {
    // Figure out what the revert title should be.
    const originalTitle = (commitMessage || '').split('\n')[0];
    const revertTitle = `Revert "${originalTitle}"`;
    if (!commitHash) {
      fireAlert(this, ERR_COMMIT_NOT_FOUND);
      return;
    }
    const revertCommitText = `This reverts commit ${commitHash}.`;

    const message =
      `${revertTitle}\n\n${revertCommitText}\n\n` +
      `Reason for revert: ${INSERT_REASON_STRING}\n`;
    // This is to give plugins a chance to update message
    this._message = this._modifyRevertMsg(change, commitMessage, message);
    this._revertType = RevertType.REVERT_SINGLE_CHANGE;
    this._showRevertSubmission = false;
    this._revertMessages[this._revertType] = this._message;
    this._originalRevertMessages[this._revertType] = this._message;
  }

  _getTrimmedChangeSubject(subject: string) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  _modifyRevertSubmissionMsg(
    change: ChangeInfo,
    msg: string,
    commitMessage: string
  ) {
    return this.jsAPI.modifyRevertSubmissionMsg(change, msg, commitMessage);
  }

  _populateRevertSubmissionMessage(
    change: ChangeInfo,
    changes: ChangeInfo[],
    commitMessage: string
  ) {
    // Follow the same convention of the revert
    const commitHash = change.current_revision;
    if (!commitHash) {
      fireAlert(this, ERR_COMMIT_NOT_FOUND);
      return;
    }
    if (!changes || changes.length <= 1) return;
    const revertTitle = `Revert submission ${change.submission_id}`;
    let message =
      revertTitle +
      '\n\n' +
      'Reason for revert: <INSERT ' +
      'REASONING HERE>\n';
    message += 'Reverted Changes:\n';
    changes.forEach(change => {
      message +=
        `${change.change_id.substring(0, 10)}:` +
        `${this._getTrimmedChangeSubject(change.subject)}\n`;
    });
    this._message = this._modifyRevertSubmissionMsg(
      change,
      message,
      commitMessage
    );
    this._revertType = RevertType.REVERT_SUBMISSION;
    this._revertMessages[this._revertType] = this._message;
    this._originalRevertMessages[this._revertType] = this._message;
    this._showRevertSubmission = true;
  }

  _handleRevertSingleChangeClicked() {
    this._showErrorMessage = false;
    if (this._message)
      this._revertMessages[RevertType.REVERT_SUBMISSION] = this._message;
    this._message = this._revertMessages[RevertType.REVERT_SINGLE_CHANGE];
    this._revertType = RevertType.REVERT_SINGLE_CHANGE;
  }

  _handleRevertSubmissionClicked() {
    this._showErrorMessage = false;
    this._revertType = RevertType.REVERT_SUBMISSION;
    if (this._message)
      this._revertMessages[RevertType.REVERT_SINGLE_CHANGE] = this._message;
    this._message = this._revertMessages[RevertType.REVERT_SUBMISSION];
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (
      this._message === this._originalRevertMessages[this._revertType] ||
      this._message.includes(INSERT_REASON_STRING)
    ) {
      this._showErrorMessage = true;
      return;
    }
    const detail: ConfirmRevertEventDetail = {
      revertType: this._revertType,
      message: this._message,
    };
    this.dispatchEvent(
      new CustomEvent('confirm', {
        detail,
        composed: true,
        bubbles: false,
      })
    );
  }

  _handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        detail: {revertType: this._revertType},
        composed: true,
        bubbles: false,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-revert-dialog': GrConfirmRevertDialog;
  }
}
