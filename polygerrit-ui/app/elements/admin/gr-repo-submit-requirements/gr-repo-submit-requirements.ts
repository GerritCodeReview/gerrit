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
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, state, query} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {userModelToken} from '../../../models/user/user-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';

@customElement('gr-repo-submit-requirements')
export class GrRepoSubmitRequirements extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @query('#createDialog')
  private readonly createDialog?: HTMLDialogElement;

  @state()
  loading = true;

  @state()
  submitRequirements?: SubmitRequirementInfo[];

  @state()
  showCreateDialog = false;

  @state() isAdmin = false;

  @state()
  newRequirement: SubmitRequirementInput = {
    name: '',
    description: '',
    applicability_expression: '',
    submittability_expression: '',
    override_expression: '',
    allow_override_in_child_projects: true,
  };

  private readonly restApiService = getAppContext().restApiService;

  private readonly getUserModel = resolve(this, userModelToken);

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
        .form {
          display: flex;
          flex-direction: column;
          gap: var(--spacing-m);
          width: 90%;
          margin: 0 auto;
        }
        .form-group {
          display: flex;
          flex-direction: column;
          gap: var(--spacing-xs);
        }
        .form-group input,
        .form-group textarea {
          width: 100%;
          padding: var(--spacing-s);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
        }
        .form-group textarea {
          min-height: 100px;
          resize: vertical;
        }
        .form-group label {
          font-weight: var(--font-weight-bold);
          display: block;
          margin-bottom: var(--spacing-xs);
        }
        .form-group .checkbox-container {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: var(--spacing-m);
        }
        .form-group .checkbox-container label {
          margin: 0;
          font-weight: var(--font-weight-bold);
          white-space: nowrap;
        }
        .form-group .checkbox-container input[type='checkbox'] {
          flex-shrink: 0;
          width: 16px;
          height: 16px;
          margin: 0;
        }
        .buttons {
          display: flex;
          justify-content: flex-end;
          gap: var(--spacing-m);
          margin-top: var(--spacing-l);
        }
        gr-dialog {
          width: 30em;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().isAdmin$,
      x => (this.isAdmin = x)
    );
  }

  override render() {
    return html`
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
                  </tr>
                `
              )}`
          )}
        </tbody>
      </table>
      ${when(
        this.isAdmin,
        () => html`
          <div class="actions">
            <gr-button
              class="createButton"
              primary
              @click=${this.handleCreateClick}
            >
              Create Submit Requirement
            </gr-button>
          </div>

          <dialog id="createDialog" tabindex="-1">
            <gr-dialog
              confirm-label="Create"
              cancel-label="Cancel"
              @confirm=${this.handleCreateConfirm}
              @cancel=${this.handleCreateCancel}
            >
              <div class="header" slot="header">Create Submit Requirement</div>
              <div class="main" slot="main">
                <form class="form">
                  <div class="form-group">
                    <label for="name">Name</label>
                    <input
                      id="name"
                      type="text"
                      .value=${this.newRequirement.name}
                      @input=${(e: Event) => this.handleInputChange(e, 'name')}
                      required
                    />
                  </div>
                  <div class="form-group">
                    <label for="description">Description</label>
                    <textarea
                      id="description"
                      .value=${this.newRequirement.description}
                      @input=${(e: Event) =>
                        this.handleInputChange(e, 'description')}
                    ></textarea>
                  </div>
                  <div class="form-group">
                    <label for="applicability">Applicability Expression</label>
                    <input
                      id="applicability"
                      type="text"
                      .value=${this.newRequirement.applicability_expression}
                      @input=${(e: Event) =>
                        this.handleInputChange(e, 'applicability_expression')}
                    />
                  </div>
                  <div class="form-group">
                    <label for="submittability"
                      >Submittability Expression</label
                    >
                    <input
                      id="submittability"
                      type="text"
                      .value=${this.newRequirement.submittability_expression}
                      @input=${(e: Event) =>
                        this.handleInputChange(e, 'submittability_expression')}
                      required
                    />
                  </div>
                  <div class="form-group">
                    <label for="override">Override Expression</label>
                    <input
                      id="override"
                      type="text"
                      .value=${this.newRequirement.override_expression}
                      @input=${(e: Event) =>
                        this.handleInputChange(e, 'override_expression')}
                    />
                  </div>
                  <div class="form-group">
                    <div class="checkbox-container">
                      <label for="allowOverride"
                        >Allow override in child projects</label
                      >
                      <input
                        id="allowOverride"
                        type="checkbox"
                        .checked=${this.newRequirement
                          .allow_override_in_child_projects}
                        @change=${(e: Event) =>
                          this.handleInputChange(
                            e,
                            'allow_override_in_child_projects'
                          )}
                      />
                    </div>
                  </div>
                </form>
              </div>
            </gr-dialog>
          </dialog>
        `
      )}
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.repoChanged();
    }
  }

  private repoChanged() {
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

        this.submitRequirements = res;
        this.loading = false;
      });
  }

  private renderCheckmark(check?: boolean) {
    return check ? '✓' : '';
  }

  private handleCreateClick() {
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.showModal();
  }

  private handleCreateCancel() {
    assertIsDefined(this.createDialog, 'createDialog');
    this.createDialog.close();
    this.resetNewRequirement();
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

    this.restApiService
      .createSubmitRequirement(this.repo, this.newRequirement, errFn)
      .then(() => {
        this.createDialog?.close();
        this.resetNewRequirement();
        this.repoChanged();
      });
  }

  private handleInputChange(e: Event, field: keyof SubmitRequirementInput) {
    const target = e.target as HTMLInputElement;
    if (field === 'allow_override_in_child_projects') {
      this.newRequirement[field] = target.checked;
    } else {
      this.newRequirement[field] = target.value;
    }
  }

  private resetNewRequirement() {
    this.newRequirement = {
      name: '',
      description: '',
      applicability_expression: '',
      submittability_expression: '',
      override_expression: '',
      allow_override_in_child_projects: true,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-submit-requirements': GrRepoSubmitRequirements;
  }
}
