/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-repo-branch-picker/gr-repo-branch-picker';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {RepoName, BranchName} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, state, query} from 'lit/decorators';
import {assertIsDefined} from '../../../utils/common-util';

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
  @query('#createOverlay') protected createOverlay?: GrOverlay;

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
          @confirm=${(e: Event) => {
            this.pickerConfirm(e);
          }}
          @cancel=${() => {
            this.handleClose();
          }}
          ?disabled=${!(this.repo && this.branch)}
        >
          <div class="header" slot="header">Create change</div>
          <div class="main" slot="main">
            <gr-repo-branch-picker
              .repo=${this.repo}
              .branch=${this.branch}
              @repo-changed=${(e: CustomEvent) => {
                this.handleRepoChanged(e);
              }}
              @branch-changed=${(e: CustomEvent) => {
                this.handleBranchChanged(e);
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

  private handleClose() {
    assertIsDefined(this.createOverlay, 'createOverlay');
    this.createOverlay.close();
  }

  private pickerConfirm(e: Event) {
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
  }

  private handleRepoChanged(e: CustomEvent) {
    this.repo = e.detail.value as RepoName;
  }

  private handleBranchChanged(e: CustomEvent) {
    this.branch = e.detail.value as BranchName;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-destination-dialog': GrCreateDestinationDialog;
  }
}
