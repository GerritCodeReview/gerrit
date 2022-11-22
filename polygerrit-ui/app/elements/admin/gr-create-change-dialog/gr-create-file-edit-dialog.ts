/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  RepoName,
  BranchName,
  ChangeInfo,
  PatchSetNumber,
} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, html, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {createEditUrl} from '../../../models/views/edit';
import {modalStyles} from '../../../styles/gr-modal-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-file-edit-dialog': GrCreateFileEditDialog;
  }
}

@customElement('gr-create-file-edit-dialog')
export class GrCreateFileEditDialog extends LitElement {
  @query('dialog')
  dialog?: HTMLDialogElement;

  @property({type: String})
  repo?: RepoName;

  @property({type: String})
  branch?: BranchName;

  @property({type: String})
  path?: string;

  @state()
  errorMessage?: string;

  @state()
  change?: ChangeInfo;

  @state()
  loading = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  static override get styles() {
    return [modalStyles];
  }

  override render() {
    if (!this.repo || !this.branch || !this.path) return nothing;
    return html`
      <dialog>
        <gr-dialog
          disabled
          ?loading=${this.loading}
          .loadingLabel=${'Creating change ...'}
          @cancel=${() => this.close()}
          .confirmLabel=${this.loading ? 'Please wait ...' : 'Failed'}
          .cancelLabel=${'Cancel'}
        >
          <div slot="header">
            <span class="main-heading">Create Change from URL</span>
          </div>
          <div slot="main">${this.renderCreating()}${this.renderError()}</div>
        </gr-dialog>
      </dialog>
    `;
  }

  private close() {
    this.repo = undefined;
    this.branch = undefined;
    this.path = undefined;
    this.dialog?.close();
  }

  override updated() {
    if (this.dialog?.open === false) this.dialog.showModal();
  }

  private renderCreating() {
    if (this.errorMessage) return nothing;
    return html`
      <div>
        <span>
          Creating a change in repository <b>${this.repo}</b> on branch
          <b>${this.branch}</b>.
        </span>
      </div>
      <div>
        <span>
          Will then redirect to editing the file
          <b>${this.path}</b>
          in the newly created change.
        </span>
      </div>
    `;
  }

  private renderError() {
    if (!this.errorMessage) return nothing;
    return html`<div>Error: ${this.errorMessage}</div>`;
  }

  override willUpdate() {
    this.createChange();
  }

  private createChange() {
    if (!this.repo || !this.branch || !this.path) return;
    if (this.loading || this.errorMessage) return;
    this.loading = true;
    return this.restApiService
      .createChange(this.repo, this.branch, `Edit ${this.path}`)
      .then(change => {
        if (change) {
          this.change = change;
        } else {
          this.errorMessage = 'Creating the change failed.';
        }
      })
      .then(() => this.redirectToFileEdit())
      .catch(() => {
        this.errorMessage = 'Creating the change failed.';
      })
      .finally(() => {
        this.loading = false;
      });
  }

  redirectToFileEdit() {
    if (!this.change || !this.path) return;
    if (this.dialog?.open === false) return;

    const url = createEditUrl({
      changeNum: this.change._number,
      repo: this.change.project,
      path: this.path,
      patchNum: 1 as PatchSetNumber,
    });
    this.getNavigation().setUrl(url);
    this.close();
  }
}
