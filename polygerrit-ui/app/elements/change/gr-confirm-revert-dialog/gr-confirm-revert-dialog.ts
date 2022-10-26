/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {LitElement, html, css, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {ChangeInfo, CommitId} from '../../../types/common';
import {fire, fireAlert} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

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

export interface CancelRevertEventDetail {
  revertType: RevertType;
}

declare global {
  interface HTMLElementEventMap {
    /** Fired when the confirm button is pressed. */
    // prettier-ignore
    'confirm': CustomEvent<ConfirmRevertEventDetail>;
    /** Fired when the cancel button is pressed. */
    // prettier-ignore
    'cancel': CustomEvent<CancelRevertEventDetail>;
  }
}

@customElement('gr-confirm-revert-dialog')
export class GrConfirmRevertDialog extends LitElement {
  /* The revert message updated by the user
      The default value is set by the dialog */
  @state()
  message = '';

  @state()
  private revertType = RevertType.REVERT_SINGLE_CHANGE;

  @state()
  private showRevertSubmission = false;

  @state()
  private changesCount?: number;

  @state()
  showErrorMessage = false;

  /* store the default revert messages per revert type so that we can
  check if user has edited the revert message or not
  Set when populate() is called */
  @state()
  private originalRevertMessages: string[] = [];

  // Store the actual messages that the user has edited
  @state()
  private revertMessages: string[] = [];

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  static override styles = [
    sharedStyles,
    css`
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
    `,
  ];

  override render() {
    return html`
      <gr-dialog
        .confirmLabel=${'Create Revert Change'}
        @confirm=${(e: Event) => this.handleConfirmTap(e)}
        @cancel=${(e: Event) => this.handleCancelTap(e)}
      >
        <div class="header" slot="header">Revert Merged Change</div>
        <div class="main" slot="main">
          <div class="error" ?hidden=${!this.showErrorMessage}>
            <span> A reason is required </span>
          </div>
          ${this.showRevertSubmission
            ? html`
                <div class="revertSubmissionLayout">
                  <input
                    name="revertOptions"
                    type="radio"
                    id="revertSingleChange"
                    @change=${() => this.handleRevertSingleChangeClicked()}
                    ?checked=${this.computeIfSingleRevert()}
                  />
                  <label
                    for="revertSingleChange"
                    class="label revertSingleChange"
                  >
                    Revert single change
                  </label>
                </div>
                <div class="revertSubmissionLayout">
                  <input
                    name="revertOptions"
                    type="radio"
                    id="revertSubmission"
                    @change=${() => this.handleRevertSubmissionClicked()}
                    .checked=${this.computeIfRevertSubmission()}
                  />
                  <label for="revertSubmission" class="label revertSubmission">
                    Revert entire submission (${this.changesCount} Changes)
                  </label>
                </div>
              `
            : nothing}
          <gr-endpoint-decorator name="confirm-revert-change">
            <label for="messageInput"> Revert Commit Message </label>
            <iron-autogrow-textarea
              id="messageInput"
              class="message"
              .autocomplete=${'on'}
              .maxRows=${15}
              .bindValue=${this.message}
              @bind-value-changed=${this.handleBindValueChanged}
            ></iron-autogrow-textarea>
          </gr-endpoint-decorator>
        </div>
      </gr-dialog>
    `;
  }

  private computeIfSingleRevert() {
    return this.revertType === RevertType.REVERT_SINGLE_CHANGE;
  }

  private computeIfRevertSubmission() {
    return this.revertType === RevertType.REVERT_SUBMISSION;
  }

  modifyRevertMsg(change: ChangeInfo, commitMessage: string, message: string) {
    return this.getPluginLoader().jsApiService.modifyRevertMsg(
      change,
      message,
      commitMessage
    );
  }

  populate(change: ChangeInfo, commitMessage: string, changes: ChangeInfo[]) {
    this.changesCount = changes.length;
    // The option to revert a single change is always available
    this.populateRevertSingleChangeMessage(
      change,
      commitMessage,
      change.current_revision
    );
    this.populateRevertSubmissionMessage(change, changes, commitMessage);
  }

  populateRevertSingleChangeMessage(
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
    this.message = this.modifyRevertMsg(change, commitMessage, message);
    this.revertType = RevertType.REVERT_SINGLE_CHANGE;
    this.showRevertSubmission = false;
    this.revertMessages[this.revertType] = this.message;
    this.originalRevertMessages[this.revertType] = this.message;
  }

  private getTrimmedChangeSubject(subject: string) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  private modifyRevertSubmissionMsg(
    change: ChangeInfo,
    msg: string,
    commitMessage: string
  ) {
    return this.getPluginLoader().jsApiService.modifyRevertSubmissionMsg(
      change,
      msg,
      commitMessage
    );
  }

  populateRevertSubmissionMessage(
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
        `${this.getTrimmedChangeSubject(change.subject)}\n`;
    });
    this.message = this.modifyRevertSubmissionMsg(
      change,
      message,
      commitMessage
    );
    this.revertType = RevertType.REVERT_SUBMISSION;
    this.revertMessages[this.revertType] = this.message;
    this.originalRevertMessages[this.revertType] = this.message;
    this.showRevertSubmission = true;
  }

  private handleBindValueChanged(e: BindValueChangeEvent) {
    this.message = e.detail.value ?? '';
  }

  private handleRevertSingleChangeClicked() {
    this.showErrorMessage = false;
    if (this.message)
      this.revertMessages[RevertType.REVERT_SUBMISSION] = this.message;
    this.message = this.revertMessages[RevertType.REVERT_SINGLE_CHANGE];
    this.revertType = RevertType.REVERT_SINGLE_CHANGE;
  }

  private handleRevertSubmissionClicked() {
    this.showErrorMessage = false;
    this.revertType = RevertType.REVERT_SUBMISSION;
    if (this.message)
      this.revertMessages[RevertType.REVERT_SINGLE_CHANGE] = this.message;
    this.message = this.revertMessages[RevertType.REVERT_SUBMISSION];
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (
      this.message === this.originalRevertMessages[this.revertType] ||
      this.message.includes(INSERT_REASON_STRING)
    ) {
      this.showErrorMessage = true;
      return;
    }
    const detail: ConfirmRevertEventDetail = {
      revertType: this.revertType,
      message: this.message,
    };
    fire(this, 'confirm', detail);
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    const detail: ConfirmRevertEventDetail = {
      revertType: this.revertType,
    };
    fire(this, 'cancel', detail);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-revert-dialog': GrConfirmRevertDialog;
  }
}
