/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-repo-branch-picker/gr-repo-branch-picker';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {RepoName, BranchName} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, state, query} from 'lit/decorators';
import {assertIsDefined} from '../../../utils/common-util';
import {BindValueChangeEvent} from '../../../types/events';

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

  @query('#createOverlay') private createOverlay?: GrOverlay;

  @state() private repo?: RepoName;

  @state() private branch?: BranchName;

  static override get styles() {
    return [sharedStyles];
  }

  override render() {
    return html`
      <gr-overlay id="createOverlay" with-backdrop>
        <gr-dialog
          confirm-label="View commands"
          @confirm=${this.pickerConfirm}
          @cancel=${() => {
            assertIsDefined(this.createOverlay, 'createOverlay');
            this.createOverlay.close();
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
      </gr-overlay>
    `;
  }

  open() {
    assertIsDefined(this.createOverlay, 'createOverlay');
    this.repo = '' as RepoName;
    this.branch = '' as BranchName;
    this.createOverlay.open();
  }

  private pickerConfirm = (e: Event) => {
    assertIsDefined(this.createOverlay, 'createOverlay');
    this.createOverlay.close();
    const detail: CreateDestinationConfirmDetail = {
      repo: this.repo,
      branch: this.branch,
    };
    // e is a 'confirm' event from gr-dialog. We want to fire a more detailed
    // 'confirm' event here, so let's stop propagation of the bare event.
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {detail, bubbles: false}));
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-destination-dialog': GrCreateDestinationDialog;
  }
}
