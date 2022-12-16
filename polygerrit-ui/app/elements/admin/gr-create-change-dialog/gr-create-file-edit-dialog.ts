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
import {assertIsDefined} from '../../../utils/common-util';
import {when} from 'lit/directives/when.js';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-file-edit-dialog': GrCreateFileEditDialog;
  }
}

@customElement('gr-create-file-edit-dialog')
export class GrCreateFileEditDialog extends LitElement {
  @query('dialog')
  modal?: HTMLDialogElement;

  @query('gr-dialog')
  grDialog?: GrDialog;

  @property({type: String})
  repo?: RepoName;

  @property({type: String})
  branch?: BranchName;

  @property({type: String})
  path?: string;

  /**
   * If this is set, then we show this message replacing all other content.
   */
  @state()
  errorMessage?: string;

  /**
   * Triggers showing the dialog and kicks off creating a change.
   */
  @state()
  active = false;

  /**
   * Indicates whether the REST API call for creating a change is in progress.
   */
  @state()
  loading = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  static override get styles() {
    return [modalStyles];
  }

  override render() {
    if (!this.active) return nothing;
    return html`
      <dialog tabindex="-1">
        <gr-dialog
          disabled
          ?loading=${this.loading}
          .loadingLabel=${'Creating change ...'}
          @cancel=${() => this.deactivate()}
          .confirmLabel=${this.loading ? 'Please wait ...' : 'Failed'}
          .cancelLabel=${'Cancel'}
        >
          <div slot="header">
            <span class="main-heading">Create Change from URL</span>
          </div>
          <div slot="main">
            ${when(
              this.errorMessage,
              () => this.renderError(),
              () => this.renderCreating()
            )}
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  async activate() {
    this.active = true;
    this.createChange();
    await this.updateComplete;
    if (this.active && this.modal?.open === false) this.modal.showModal();
  }

  deactivate() {
    this.active = false;
    this.modal?.close();
  }

  private renderCreating() {
    return html`
      <div>
        <span>
          Creating a change in repository <b>${this.repo}</b> on branch
          <b>${this.branch}</b>.
        </span>
      </div>
      <div>
        <span>
          The page will then redirect to the file editor for
          <b>${this.path}</b>
          in the newly created change.
        </span>
      </div>
    `;
  }

  private renderError() {
    return html`<div>Error: ${this.errorMessage}</div>`;
  }

  private createChange() {
    if (!this.repo || !this.branch || !this.path) {
      this.errorMessage = 'repo, branch and path must be set';
      return;
    }
    if (this.loading || this.errorMessage) return;
    this.loading = true;
    this.restApiService
      .createChange(this.repo, this.branch, `Edit ${this.path}`)
      .then(change => {
        if (!this.active) return;
        if (change) {
          this.loading = false;
          this.redirectToFileEdit(change);
          this.deactivate();
        } else {
          this.errorMessage = 'Creating the change failed.';
        }
      })
      .catch(() => {
        this.errorMessage = 'Creating the change failed.';
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private redirectToFileEdit(change: ChangeInfo) {
    assertIsDefined(this.path, 'path');
    const url = createEditUrl({
      changeNum: change._number,
      repo: change.project,
      patchNum: 1 as PatchSetNumber,
      editView: {path: this.path},
    });
    this.getNavigation().setUrl(url);
  }
}
