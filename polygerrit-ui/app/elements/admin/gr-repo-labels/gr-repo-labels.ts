/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName} from '../../../types/common';
import {fireError, firePageError} from '../../../utils/event-util';
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
import {
  BatchLabelInput,
  DeleteLabelInput,
  LabelDefinitionInfo,
  LabelDefinitionInfoFunction,
  LabelDefinitionInput,
  LabelValueToDescriptionMap,
} from '../../../api/rest-api';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {createChangeUrl} from '../../../models/views/change';
import {resolve} from '../../../models/dependency';
import {GrButton} from '../../shared/gr-button/gr-button';

@customElement('gr-repo-labels')
export class GrRepoLabels extends LitElement {
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
  labels?: LabelDefinitionInfo[];

  @state()
  showCreateDialog = false;

  @state() isProjectOwner = false;

  @state()
  disableSaveWithoutReview = true;

  @state()
  showSaveForReviewButton = false;

  @state()
  newLabel: LabelDefinitionInput = this.getEmptyLabel();

  @state() offset = 0;

  @state() filter = '';

  @state() itemsPerPage = 25;

  @state() showDeleteDialog = false;

  @state()
  labelToDelete?: LabelDefinitionInfo;

  @state()
  labelToEdit?: LabelDefinitionInfo;

  @state()
  isEditing = false;

  private readonly getNavigation = resolve(this, navigationToken);

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
          min-height: 4em;
          resize: vertical;
          box-sizing: border-box;
        }
        gr-dialog .main {
          max-height: 70vh;
          overflow-y: auto;
        }
        gr-dialog .footer {
          width: 100%;
          display: flex;
          justify-content: flex-end;
        }
        gr-dialog {
          width: 36em;
        }
        .warning {
          color: var(--warning-foreground);
          margin-top: var(--spacing-s);
        }
        .warning gr-icon {
          color: var(--warning-icon-color);
          vertical-align: bottom;
          margin-right: var(--spacing-s);
        }
        td gr-icon {
          vertical-align: bottom;
          margin-left: var(--spacing-s);
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
      this.disableSaveWithoutReview =
        !!access?.require_change_for_config_update;
      this.showSaveForReviewButton = true;
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
        .items=${this.labels}
        .loading=${this.loading}
        .offset=${this.offset}
        .path=${createRepoUrl({
          repo: this.repo,
          detail: RepoDetailView.LABELS,
        })}
        @create-clicked=${() => this.handleCreateClick()}
      >
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="topHeader">Name</th>
              <th class="topHeader">Description</th>
              <th class="topHeader">Function</th>
              <th class="topHeader">Default Value</th>
              <th class="topHeader">Copy Condition</th>
              <th class="topHeader">Allow Post Submit</th>
              <th class="topHeader">Can Override</th>
              <th class="topHeader">Ignore Self Approval</th>
              <th class="topHeader">Branches</th>
              <th class="topHeader">Values</th>
              ${when(
                this.isProjectOwner,
                () => html`<th class="topHeader"></th>`
              )}
            </tr>
          </tbody>
          <tbody id="labels">
            ${when(
              this.loading,
              () => html`<tr id="loadingContainer">
                <td>Loading...</td>
              </tr>`,
              () =>
                html` ${(this.labels ?? []).map(
                  item => html`
                    <tr class="table">
                      <td class="name">${item.name}</td>
                      <td class="description">${item.description}</td>
                      <td class="function">
                        ${item.function}
                        ${when(
                          this.isFunctionDeprecated(item.function),
                          () => html`
                            <gr-icon
                              icon="warning"
                              title="This function is deprecated."
                            ></gr-icon>
                          `
                        )}
                      </td>
                      <td class="defaultValue">${item.default_value}</td>
                      <td class="copyCondition">${item.copy_condition}</td>
                      <td class="allowPostSubmit">
                        ${this.renderCheckmark(item.allow_post_submit)}
                      </td>
                      <td class="canOverride">
                        ${this.renderCheckmark(item.can_override)}
                      </td>
                      <td class="ignoreSelfApproval">
                        ${this.renderCheckmark(item.ignore_self_approval)}
                        ${when(
                          item.ignore_self_approval,
                          () => html`
                            <gr-icon
                              icon="warning"
                              title="ignoreSelfApproval is deprecated."
                            ></gr-icon>
                          `
                        )}
                      </td>
                      <td class="branches">
                        ${(item.branches ?? []).join(', ')}
                      </td>
                      <td class="values">${JSON.stringify(item.values)}</td>
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
      this.getLabels();
      this.checkProjectOwner();
    }
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  async paramsChanged() {
    const params = this.params;
    this.loading = true;
    this.filter = params?.filter ?? '';
    this.offset = Number(params?.offset ?? 0);

    await this.getLabels(this.filter, this.offset);
  }

  private getLabels(filter?: string, offset?: number) {
    const repo = this.repo;
    this.loading = true;
    if (!repo) {
      return Promise.resolve();
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getRepoLabels(repo, errFn)
      .then((res?: LabelDefinitionInfo[]) => {
        if (!res) {
          return;
        }

        this.labels = res
          .filter(item =>
            filter === undefined
              ? true
              : item.name.toLowerCase().includes(filter.toLowerCase())
          )
          .slice(offset ?? 0, (offset ?? 0) + this.itemsPerPage);
        this.loading = false;
      });
  }

  private isFunctionDeprecated(fun?: LabelDefinitionInfoFunction) {
    if (!fun) return false;
    return (
      fun !== LabelDefinitionInfoFunction.NoBlock &&
      fun !== LabelDefinitionInfoFunction.Noop &&
      fun !== LabelDefinitionInfoFunction.PatchSetLock
    );
  }

  private renderCheckmark(check?: boolean) {
    return check ? '✓' : '';
  }

  private handleCreateClick() {
    this.isEditing = false;
    this.newLabel = this.getEmptyLabel();
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.showModal();
  }

  private handleEditClick(label: LabelDefinitionInfo) {
    this.isEditing = true;
    this.labelToEdit = label;
    this.newLabel = {
      name: label.name,
      description: label.description,
      function: label.function,
      default_value: label.default_value,
      copy_condition: label.copy_condition,
      allow_post_submit: label.allow_post_submit,
      can_override: label.can_override,
      ignore_self_approval: label.ignore_self_approval,
      values: label.values,
      branches: label.branches,
    };
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.showModal();
  }

  private handleCreateCancel() {
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.close();
    this.newLabel = this.getEmptyLabel();
    this.labelToEdit = undefined;
    this.isEditing = false;
  }

  private handleCreateConfirm() {
    if (!this.repo) return;
    if (!this.newLabel.name) {
      return;
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const promise =
      this.isEditing && this.labelToEdit
        ? this.restApiService.updateRepoLabel(
            this.repo,
            this.labelToEdit.name,
            this.newLabel,
            errFn
          )
        : this.restApiService.createRepoLabel(
            this.repo,
            this.newLabel.name,
            this.newLabel,
            errFn
          );

    promise.then(() => {
      this.createDialog?.close();
      this.newLabel = this.getEmptyLabel();
      this.labelToEdit = undefined;
      this.isEditing = false;
      this.getLabels(this.filter, this.offset);
    });
  }

  private getEmptyLabel(): LabelDefinitionInput {
    return {
      name: '',
      description: '',
      function: LabelDefinitionInfoFunction.NoBlock,
      default_value: 0,
      copy_condition: 'is:MIN',
      unset_copy_condition: false,
      allow_post_submit: false,
      can_override: true,
      ignore_self_approval: false,
      values: {
        ' 0': 'No score',
        '-1': 'I would prefer this is not submitted as is',
        '-2': 'This shall not be submitted',
        '+1': 'Looks good to me, but someone else must approve',
        '+2': 'Looks good to me, approved',
      },
      branches: [],
    };
  }

  private async handleSaveForReview(e: Event) {
    if (!this.repo) return;
    if (!this.newLabel.name) {
      return;
    }
    const button = e.target as GrButton;
    button.loading = true;

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const input: BatchLabelInput = {};
    if (this.isEditing && this.labelToEdit) {
      const updatedLabel: Partial<LabelDefinitionInput> = {};
      const original = this.labelToEdit;
      const updated = this.newLabel;

      if (updated.description !== original.description) {
        updatedLabel.description = updated.description;
      }
      if (updated.function !== original.function) {
        updatedLabel.function = updated.function;
      }
      if (JSON.stringify(updated.values) !== JSON.stringify(original.values)) {
        updatedLabel.values = updated.values;
      }
      if (updated.default_value !== original.default_value) {
        updatedLabel.default_value = updated.default_value;
      }
      if (
        JSON.stringify(updated.branches) !== JSON.stringify(original.branches)
      ) {
        updatedLabel.branches = updated.branches;
      }
      if (updated.can_override !== original.can_override) {
        updatedLabel.can_override = updated.can_override;
      }
      if (updated.copy_condition !== original.copy_condition) {
        updatedLabel.copy_condition = updated.copy_condition;
      }
      if (updated.allow_post_submit !== original.allow_post_submit) {
        updatedLabel.allow_post_submit = updated.allow_post_submit;
      }
      if (updated.ignore_self_approval !== original.ignore_self_approval) {
        updatedLabel.ignore_self_approval = updated.ignore_self_approval;
      }
      if (updated.unset_copy_condition) {
        updatedLabel.unset_copy_condition = updated.unset_copy_condition;
      }
      input.update = {[this.newLabel.name]: updatedLabel};
    } else {
      input.create = [this.newLabel];
    }

    const promise = this.restApiService.saveRepoLabelsForReview(
      this.repo,
      input,
      errFn
    );

    try {
      const change = await promise;
      if (change) {
        this.getNavigation().setUrl(createChangeUrl({change}));
      }
    } finally {
      button.loading = false;
      this.createDialog?.close();
      this.newLabel = this.getEmptyLabel();
      this.labelToEdit = undefined;
      this.isEditing = false;
      this.getLabels(this.filter, this.offset);
    }
  }

  private renderCreateDialog() {
    if (!this.isProjectOwner) return nothing;

    return html`
      <dialog id="createDialog" tabindex="-1">
        <gr-dialog .cancelLabel=${''} .confirmLabel=${''}>
          <div class="header" slot="header">
            ${this.isEditing ? 'Edit' : 'Create'} Label
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
                        .bindValue=${this.newLabel.name}
                        @bind-value-changed=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
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
                      <iron-input
                        .bindValue=${this.newLabel.description}
                        @bind-value-changed=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            description: (e as CustomEvent).detail.value,
                          };
                        }}
                      >
                        <input id="description" type="text" />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Function</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <gr-dropdown-list
                        .items=${Object.values(LabelDefinitionInfoFunction).map(
                          fun => {
                            return {
                              text: fun,
                              value: fun,
                            };
                          }
                        )}
                        .value=${this.newLabel.function ?? ''}
                        @value-change=${(e: CustomEvent<{value: string}>) => {
                          const value = e.detail.value;
                          this.newLabel = {
                            ...this.newLabel,
                            function: value
                              ? (value as LabelDefinitionInfoFunction)
                              : undefined,
                          };
                        }}
                      ></gr-dropdown-list>
                      ${when(
                        this.isFunctionDeprecated(this.newLabel.function),
                        () => html`
                          <div class="warning">
                            <gr-icon icon="warning"></gr-icon>
                            This function is deprecated.
                          </div>
                        `
                      )}
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Default Value</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newLabel.default_value?.toString()}
                        @bind-value-changed=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            default_value: Number(
                              (e as CustomEvent).detail.value
                            ),
                          };
                        }}
                      >
                        <input id="defaultValue" type="number" />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Copy Condition</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <iron-input
                        .bindValue=${this.newLabel.copy_condition}
                        @bind-value-changed=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            copy_condition: (e as CustomEvent).detail.value,
                          };
                        }}
                      >
                        <input id="copyCondition" type="text" />
                      </iron-input>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Unset Copy Condition</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <input
                        id="unsetCopyCondition"
                        type="checkbox"
                        ?checked=${this.newLabel.unset_copy_condition ?? false}
                        @change=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            unset_copy_condition: (e.target as HTMLInputElement)
                              .checked,
                          };
                        }}
                      />
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Can Override</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <input
                        id="canOverride"
                        type="checkbox"
                        ?checked=${this.newLabel.can_override ?? false}
                        @change=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            can_override: (e.target as HTMLInputElement)
                              .checked,
                          };
                        }}
                      />
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Allow Post Submit</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <input
                        id="allowPostSubmit"
                        type="checkbox"
                        ?checked=${this.newLabel.allow_post_submit ?? false}
                        @change=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            allow_post_submit: (e.target as HTMLInputElement)
                              .checked,
                          };
                        }}
                      />
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Ignore Self Approval</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <input
                        id="ignoreSelfApproval"
                        type="checkbox"
                        ?checked=${this.newLabel.ignore_self_approval ?? false}
                        @change=${(e: Event) => {
                          this.newLabel = {
                            ...this.newLabel,
                            ignore_self_approval: (e.target as HTMLInputElement)
                              .checked,
                          };
                        }}
                      />
                      ${when(
                        this.newLabel.ignore_self_approval,
                        () => html`
                          <div class="warning">
                            <gr-icon icon="warning"></gr-icon>
                            ignoreSelfApproval is deprecated.
                          </div>
                        `
                      )}
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Branches</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <textarea
                        id="branches"
                        rows="2"
                        .value=${(this.newLabel.branches ?? []).join('\n')}
                        @change=${(e: Event) => {
                          const target = e.target as HTMLTextAreaElement;
                          this.newLabel = {
                            ...this.newLabel,
                            branches: target.value
                              .split('\n')
                              .map(b => b.trim())
                              .filter(Boolean),
                          };
                        }}
                      ></textarea>
                    </span>
                  </div>
                </section>
                <section>
                  <div class="title-flex">
                    <span class="title">Values</span>
                  </div>
                  <div class="value-flex">
                    <span class="value">
                      <textarea
                        id="values"
                        .value=${JSON.stringify(this.newLabel.values, null, 2)}
                        @change=${(e: Event) => {
                          const target = e.target as HTMLTextAreaElement;
                          try {
                            this.newLabel = {
                              ...this.newLabel,
                              values: JSON.parse(
                                target.value
                              ) as LabelValueToDescriptionMap,
                            };
                          } catch (err) {
                            fireError(this, 'Invalid JSON');
                          }
                        }}
                      ></textarea>
                    </span>
                  </div>
                </section>
              </div>
            </div>
          </div>
          <div class="footer" slot="footer">
            <gr-button @click=${this.handleCreateCancel} link>Cancel</gr-button>
            <gr-button
              class="action save-button"
              link
              primary
              ?disabled=${!this.newLabel.name || this.disableSaveWithoutReview}
              @click=${this.handleCreateConfirm}
            >
              ${this.isEditing ? 'Save' : 'Create'}
            </gr-button>
            <gr-button
              class="action save-for-review"
              primary
              link
              ?hidden=${!this.showSaveForReviewButton}
              ?disabled=${!this.newLabel.name}
              @click=${this.handleSaveForReview}
            >
              Save for review
            </gr-button>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renderDeleteDialog() {
    if (!this.isProjectOwner) return nothing;

    return html`
      <dialog id="deleteDialog" tabindex="-1">
        <gr-dialog .cancelLabel=${''} .confirmLabel=${''}>
          <div class="header" slot="header">Delete Label</div>
          <div class="main" slot="main">
            Are you sure you want to delete the label
            "${this.labelToDelete?.name}"?
          </div>
          <div class="footer" slot="footer">
            <gr-button link @click=${this.handleDeleteCancel}>Cancel</gr-button>
            <gr-button
              class="action"
              link
              ?disabled=${this.disableSaveWithoutReview}
              @click=${this.handleDeleteConfirm}
            >
              Delete
            </gr-button>
            <gr-button
              class="action"
              primary
              link
              ?hidden=${!this.showSaveForReviewButton}
              @click=${this.handleDeleteForReviewConfirm}
            >
              Delete for review
            </gr-button>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private handleDeleteClick(label: LabelDefinitionInfo) {
    this.labelToDelete = label;
    assertIsDefined(this.deleteDialog, 'deleteDialog');
    this.deleteDialog.showModal();
  }

  private handleDeleteCancel() {
    assertIsDefined(this.deleteDialog, 'deleteDialog');
    this.deleteDialog.close();
    this.labelToDelete = undefined;
  }

  private async handleDeleteForReviewConfirm() {
    if (!this.repo || !this.labelToDelete) return;

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const input: BatchLabelInput = {
      delete: [this.labelToDelete.name],
    };

    const promise = this.restApiService.saveRepoLabelsForReview(
      this.repo,
      input,
      errFn
    );

    try {
      const change = await promise;
      if (change) {
        this.getNavigation().setUrl(createChangeUrl({change}));
      }
    } finally {
      this.deleteDialog?.close();
      this.labelToDelete = undefined;
      this.getLabels(this.filter, this.offset);
    }
  }

  private handleDeleteConfirm() {
    if (!this.repo || !this.labelToDelete) return;

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    const deleteInput: DeleteLabelInput = {};

    this.restApiService
      .deleteRepoLabel(this.repo, this.labelToDelete.name, deleteInput, errFn)
      .then(() => {
        this.deleteDialog?.close();
        this.labelToDelete = undefined;
        this.getLabels(this.filter, this.offset);
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-labels': GrRepoLabels;
  }
}
