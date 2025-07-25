/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  RepoName,
  SubmitRequirementInfo,
  SubmitRequirementInput,
} from '../../../types/common';
import {firePageError} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {modalStyles} from '../../../styles/gr-modal-styles';
import '../../shared/gr-list-view/gr-list-view';
import {
  createRepoUrl,
  RepoDetailView,
  RepoViewState,
} from '../../../models/views/repo';
import '@polymer/iron-input/iron-input';

@customElement('gr-repo-submit-requirements')
export class GrRepoSubmitRequirements extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  params?: RepoViewState;

  @query('#createDialog')
  private readonly createDialog?: HTMLDialogElement;

  @query('#deleteDialog')
  private readonly deleteDialog?: HTMLDialogElement;

  @state()
  loading = true;

  @state()
  submitRequirements?: SubmitRequirementInfo[];

  @state()
  showCreateDialog = false;

  @state() isProjectOwner = false;

  @state()
  newRequirement: SubmitRequirementInput = this.getEmptyRequirement();

  @state() offset = 0;

  @state() filter = '';

  @state() itemsPerPage = 25;

  @state() showDeleteDialog = false;

  @state() requirementToDelete?: SubmitRequirementInfo;

  @state()
  requirementToEdit?: SubmitRequirementInfo;

  @state()
  isEditing = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      tableStyles,
      grFormStyles,
      modalStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
        .actions {
          display: flex;
          justify-content: flex-end;
          margin-bottom: var(--spacing-m);
          padding: var(--spacing-l);
        }
        .createButton {
          margin-left: var(--spacing-m);
        }
        .deleteBtn {
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        div.title-flex,
        div.value-flex {
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
        input {
          width: 20em;
          box-sizing: border-box;
        }
        div.gr-form-styles section {
          margin: var(--spacing-m) 0;
        }
        div.gr-form-styles span.title {
          width: 13em;
        }
        section .title gr-icon {
          vertical-align: top;
        }
        textarea {
          width: 20em;
          min-height: 100px;
          resize: vertical;
          box-sizing: border-box;
        }
        gr-dialog {
          width: 36em;
        }
      `,
    ];
  }

  constructor() {
    super();
    if (this.repo) {
      this.checkProjectOwner();
    }
  }

  private async checkProjectOwner() {
    if (!this.repo) return;
    try {
      const access = await this.restApiService.getRepoAccessRights(this.repo);
      this.isProjectOwner = !!access?.is_owner;
    } catch (e) {
      console.error('Failed to check project owner status:', e);
      this.isProjectOwner = false;
    }
  }

  override render() {
    return html`
      <gr-list-view
        .createNew=${this.isProjectOwner}
        .filter=${this.filter}
        .itemsPerPage=${this.itemsPerPage}
        .items=${this.submitRequirements}
        .loading=${this.loading}
        .offset=${this.offset}
        .path=${createRepoUrl({
          repo: this.repo,
          detail: RepoDetailView.SUBMIT_REQUIREMENTS,
        })}
        @create-clicked=${() => this.handleCreateClick()}
      >
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="topHeader">Name</th>
              <th class="topHeader">Description</th>
              <th class="topHeader">Applicability Expression</th>
              <th class="topHeader">Submittability Expression</th>
              <th class="topHeader">Override Expression</th>
              <th
                class="topHeader"
                title="Whether override is allowed in child projects"
              >
                Allow Override
              </th>
              ${when(
                this.isProjectOwner,
                () => html`<th class="topHeader"></th>`
              )}
            </tr>
          </tbody>
          <tbody id="submit-requirements">
            ${when(
              this.loading,
              () => html`<tr id="loadingContainer">
                <td>Loading...</td>
              </tr>`,
              () =>
                html` ${(this.submitRequirements ?? []).map(
                  item => html`
                    <tr class="table">
                      <td class="name">${item.name}</td>
                      <td class="desc">${item.description}</td>
                      <td class="applicability">
                        ${item.applicability_expression}
                      </td>
                      <td class="submittability">
                        ${item.submittability_expression}
                      </td>
                      <td class="override">${item.override_expression}</td>
                      <td class="allowOverride">
                        ${this.renderCheckmark(
                          item.allow_override_in_child_projects
                        )}
                      </td>
                      ${when(
                        this.isProjectOwner,
                        () => html`
                          <td class="actions">
                            <gr-button
                              class="editBtn"
                              link
                              @click=${() => this.handleEditClick(item)}
                            >
                              Edit
                            </gr-button>
                            <gr-button
                              class="deleteBtn"
                              link
                              @click=${() => this.handleDeleteClick(item)}
                            >
                              Delete
                            </gr-button>
                          </td>
                        `
                      )}
                    </tr>
                  `
                )}`
            )}
          </tbody>
        </table>
      </gr-list-view>

      ${this.renderCreateDialog()} ${this.renderDeleteDialog()}
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.getSubmitRequirements();
      this.checkProjectOwner();
    }
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this._paramsChanged();
    }
  }

  async _paramsChanged() {
    const params = this.params;
    this.loading = true;
    this.filter = params?.filter ?? '';
    this.offset = Number(params?.offset ?? 0);

    await this.getSubmitRequirements(this.filter, this.offset);
  }

  private getSubmitRequirements(filter?: string, offset?: number) {
    const repo = this.repo;
    this.loading = true;
    if (!repo) {
      return Promise.resolve();
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getRepoSubmitRequirements(repo, errFn)
      .then((res?: SubmitRequirementInfo[]) => {
        if (!res) {
          return;
        }

        this.submitRequirements = res
          .filter(item =>
            filter === undefined
              ? true
              : item.name.toLowerCase().includes(filter.toLowerCase())
          )
          .slice(offset ?? 0, (offset ?? 0) + this.itemsPerPage);
        this.loading = false;
      });
  }

  private renderCheckmark(check?: boolean) {
    return check ? '✓' : '';
  }

  private handleCreateClick() {
    this.isEditing = false;
    this.newRequirement = this.getEmptyRequirement();
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.showModal();
  }

  private handleEditClick(requirement: SubmitRequirementInfo) {
    this.isEditing = true;
    this.requirementToEdit = requirement;
    this.newRequirement = {
      name: requirement.name,
      description: requirement.description || '',
      applicability_expression: requirement.applicability_expression || '',
      submittability_expression: requirement.submittability_expression || '',
      override_expression: requirement.override_expression || '',
      allow_override_in_child_projects:
        requirement.allow_override_in_child_projects || false,
    };
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.showModal();
  }

  private handleCreateCancel() {
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.close();
    this.newRequirement = this.getEmptyRequirement();
    this.requirementToEdit = undefined;
    this.isEditing = false;
  }

  private handleCreateConfirm() {
    if (!this.repo) return;
    if (
      !this.newRequirement.name ||
      !this.newRequirement.submittability_expression
    ) {
      return;
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const promise =
      this.isEditing && this.requirementToEdit
        ? this.restApiService.updateSubmitRequirement(
            this.repo,
            this.requirementToEdit.name,
            this.newRequirement,
            errFn
          )
        : this.restApiService.createSubmitRequirement(
            this.repo,
            this.newRequirement,
            errFn
          );

    promise.then(() => {
      this.createDialog?.close();
      this.newRequirement = this.getEmptyRequirement();
      this.requirementToEdit = undefined;
      this.isEditing = false;
      this.getSubmitRequirements(this.filter, this.offset);
    });
  }

  private getEmptyRequirement(): SubmitRequirementInput {
    return {
      name: '',
      description: '',
      applicability_expression: '',
      submittability_expression: '',
      override_expression: '',
      allow_override_in_child_projects: false,
    };
  }

  private renderCreateDialog() {
    if (!this.isProjectOwner) return nothing;

    return html`
      <dialog id="createDialog" tabindex="-1">
        <gr-dialog
          confirm-label=${this.isEditing ? 'Save' : 'Create'}
          cancel-label="Cancel"
          ?disabled=${!this.newRequirement.name ||
          !this.newRequirement.submittability_expression}
          @confirm=${this.handleCreateConfirm}
          @cancel=${this.handleCreateCancel}
        >
          <div class="header" slot="header">
            ${this.isEditing ? 'Edit' : 'Create'} Submit Requirement
          </div>
          <div class="main" slot="main">
            <div class="gr-form-styles">
              <div id="form">
                <section>
                  <div class="title-flex">
                    <span class="title">Name</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newRequirement.name}
                        @bind-value-changed=${(e: Event) => {
                          this.newRequirement = {
                            ...this.newRequirement,
                            name: (e as CustomEvent).detail.value,
                          };
                        }}
                      >
                        <input
                          id="name"
                          type="text"
                          required
                          ?disabled=${this.isEditing}
                        />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Description</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <textarea
                        id="description"
                        .value=${this.newRequirement.description ?? ''}
                        placeholder="Optional"
                      ></textarea>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Applicability Expression</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newRequirement
                          .applicability_expression}
                        @bind-value-changed=${(e: Event) => {
                          this.newRequirement = {
                            ...this.newRequirement,
                            applicability_expression: (e as CustomEvent).detail
                              .value,
                          };
                        }}
                      >
                        <input
                          id="applicability"
                          type="text"
                          placeholder="Optional"
                        />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Submittability Expression</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newRequirement
                          .submittability_expression}
                        @bind-value-changed=${(e: Event) => {
                          this.newRequirement = {
                            ...this.newRequirement,
                            submittability_expression: (e as CustomEvent).detail
                              .value,
                          };
                        }}
                      >
                        <input id="submittability" type="text" required />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Override Expression</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newRequirement.override_expression}
                        @bind-value-changed=${(e: Event) => {
                          this.newRequirement = {
                            ...this.newRequirement,
                            override_expression: (e as CustomEvent).detail
                              .value,
                          };
                        }}
                      >
                        <input
                          id="override"
                          type="text"
                          placeholder="Optional"
                        />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Allow Override in Child Projects</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <gr-select
                        id="allowOverride"
                        .bindValue=${this.newRequirement
                          .allow_override_in_child_projects}
                        @bind-value-changed=${(e: Event) => {
                          this.newRequirement = {
                            ...this.newRequirement,
                            allow_override_in_child_projects:
                              (e as CustomEvent).detail.value === 'true',
                          };
                        }}
                      >
                        <select>
                          <option value="true">True</option>
                          <option value="false">False</option>
                        </select>
                      </gr-select>
                    </span>
                  </div>
                </section>
              </div>
            </div>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderDeleteDialog() {
    if (!this.isProjectOwner) return nothing;

    return html`
      <dialog id="deleteDialog" tabindex="-1">
        <gr-dialog
          confirm-label="Delete"
          cancel-label="Cancel"
          @confirm=${this.handleDeleteConfirm}
          @cancel=${this.handleDeleteCancel}
        >
          <div class="header" slot="header">Delete Submit Requirement</div>
          <div class="main" slot="main">
            Are you sure you want to delete the submit requirement
            "${this.requirementToDelete?.name}"?
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private handleDeleteClick(requirement: SubmitRequirementInfo) {
    this.requirementToDelete = requirement;
    assertIsDefined(this.deleteDialog, 'deleteDialog');
    this.deleteDialog.showModal();
  }

  private handleDeleteCancel() {
    assertIsDefined(this.deleteDialog, 'deleteDialog');
    this.deleteDialog.close();
    this.requirementToDelete = undefined;
  }

  private handleDeleteConfirm() {
    if (!this.repo || !this.requirementToDelete) return;

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    this.restApiService
      .deleteSubmitRequirement(this.repo, this.requirementToDelete.name, errFn)
      .then(() => {
        this.deleteDialog?.close();
        this.requirementToDelete = undefined;
        this.getSubmitRequirements(this.filter, this.offset);
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-submit-requirements': GrRepoSubmitRequirements;
  }
}
