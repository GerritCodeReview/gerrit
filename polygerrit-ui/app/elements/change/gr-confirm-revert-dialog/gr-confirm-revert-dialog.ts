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
import {ChangeActionDialog, ChangeInfo, CommitId} from '../../../types/common';
import {fire, fireAlert} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {createSearchUrl} from '../../../models/views/search';
import {ParsedChangeInfo} from '../../../types/types';
import {formStyles} from '../../../styles/form-styles';

const ERR_COMMIT_NOT_FOUND = 'Unable to find the commit hash of this change.';
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
export class GrConfirmRevertDialog
  extends LitElement
  implements ChangeActionDialog
{
  /* The revert message updated by the user
      The default value is set by the dialog */
  @state()
  message = '';

  @state()
  private revertType = RevertType.REVERT_SINGLE_CHANGE;

  @state()
  private showRevertSubmission = false;

  // Value supplied by populate(). Non-private for access in tests.
  @state()
  changesCount?: number;

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

  static override get styles() {
    return [
      formStyles,
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
  }

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

  modifyRevertMsg(
    change: ParsedChangeInfo,
    commitMessage: string,
    message: string
  ) {
    return this.getPluginLoader().jsApiService.modifyRevertMsg(
      change as ChangeInfo,
      message,
      commitMessage
    );
  }

  populate(
    change: ParsedChangeInfo,
    commitMessage: string,
    changesCount: number
  ) {
    this.changesCount = changesCount;
    // The option to revert a single change is always available
    this.populateRevertSingleChangeMessage(
      change,
      commitMessage,
      change.current_revision
    );
    this.populateRevertSubmissionMessage(change, commitMessage);
  }

  populateRevertSingleChangeMessage(
    change: ParsedChangeInfo,
    commitMessage: string,
    commitHash?: CommitId
  ) {
    // Figure out what the revert title should be.
    const originalTitle = (commitMessage || '').split('\n')[0];
    let revertTitle = `Revert "${originalTitle}"`;
    const match = originalTitle.match(/^Revert(?:\^([0-9]+))? "(.*)"$/);
    if (match) {
      let revertNum = 2;
      if (match[1]) {
        revertNum = Number(match[1]) + 1;
      }
      revertTitle = `Revert^${revertNum} "${match[2]}"`;
    }

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

  private modifyRevertSubmissionMsg(
    change: ParsedChangeInfo,
    msg: string,
    commitMessage: string
  ) {
    return this.getPluginLoader().jsApiService.modifyRevertSubmissionMsg(
      change as ChangeInfo,
      msg,
      commitMessage
    );
  }

  populateRevertSubmissionMessage(
    change: ParsedChangeInfo,
    commitMessage: string
  ) {
    // Follow the same convention of the revert
    const commitHash = change.current_revision;
    if (!commitHash) {
      fireAlert(this, ERR_COMMIT_NOT_FOUND);
      return;
    }
    if (this.changesCount! <= 1) return;
    const message =
      `Revert submission ${change.submission_id}` +
      '\n\n' +
      'Reason for revert: <INSERT ' +
      'REASONING HERE>\n\n' +
      'Reverted changes: ' +
      createSearchUrl({query: `submissionid:${change.submission_id}`}) +
      '\n';
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
    fire(this, 'confirm-revert', detail);
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'cancel', {});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-revert-dialog': GrConfirmRevertDialog;
  }
  interface HTMLElementEventMap {
    'confirm-revert': CustomEvent<ConfirmRevertEventDetail>;
  }
}
