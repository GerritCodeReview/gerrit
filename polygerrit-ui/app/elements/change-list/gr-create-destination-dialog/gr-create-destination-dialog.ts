/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-repo-branch-picker/gr-repo-branch-picker';
import {BranchName, RepoName} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {BindValueChangeEvent} from '../../../types/events';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';

export interface CreateDestinationConfirmDetail {
  repo?: RepoName;
  branch?: BranchName;
}

@customElement('gr-create-destination-dialog')
export class GrCreateDestinationDialog extends LitElement {
  /**
   * Fired when a destination has been picked. Event details contain the repo
   * name and the branch name.
   *
   * @event confirm
   */

  @query('#createModal') private createModal?: HTMLDialogElement;

  @state() private repo?: RepoName;

  @state() private branch?: BranchName;

  static override get styles() {
    return [sharedStyles, modalStyles];
  }

  override render() {
    return html`
      <dialog id="createModal" tabindex="-1">
        <gr-dialog
          confirm-label="View commands"
          @confirm=${this.pickerConfirm}
          @cancel=${() => {
            assertIsDefined(this.createModal, 'createModal');
            this.createModal.close();
          }}
          ?disabled=${!(this.repo && this.branch)}
        >
          <div class="header" slot="header">Create change</div>
          <div class="main" slot="main">
            <gr-repo-branch-picker
              .repo=${this.repo}
              .branch=${this.branch}
              @repo-changed=${(e: BindValueChangeEvent) => {
                this.repo = e.detail.value as RepoName;
              }}
              @branch-changed=${(e: BindValueChangeEvent) => {
                this.branch = e.detail.value as BranchName;
              }}
            ></gr-repo-branch-picker>
            <p>
              If you haven't done so, you will need to clone the repository.
            </p>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  open() {
    assertIsDefined(this.createModal, 'createModal');
    this.repo = '' as RepoName;
    this.branch = '' as BranchName;
    this.createModal.showModal();
  }

  private pickerConfirm = (e: Event) => {
    assertIsDefined(this.createModal, 'createModal');
    this.createModal.close();
    const detail: CreateDestinationConfirmDetail = {
      repo: this.repo,
      branch: this.branch,
    };
    // e is a 'confirm' event from gr-dialog. We want to fire a more detailed
    // 'confirm' event here, so let's stop propagation of the bare event.
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'confirm-destination', detail);
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-destination-dialog': GrCreateDestinationDialog;
  }
  interface HTMLElementEventMap {
    'confirm-destination': CustomEvent<CreateDestinationConfirmDetail>;
  }
}
