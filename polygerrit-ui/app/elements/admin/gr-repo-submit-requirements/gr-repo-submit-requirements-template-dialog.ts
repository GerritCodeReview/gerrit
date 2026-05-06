/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName, SubmitRequirementInfo} from '../../../types/common';
import {firePageError} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {modalStyles} from '../../../styles/gr-modal-styles';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '../../shared/gr-button/gr-button';
import {tableStyles} from '../../../styles/gr-table-styles';

@customElement('gr-repo-submit-requirements-template-dialog')
export class GrRepoSubmitRequirementsTemplateDialog extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @query('#templateDialog')
  private readonly templateDialog?: HTMLDialogElement;

  @state()
  templates?: SubmitRequirementInfo[];

  @state()
  loading = false;

  @state()
  selectedTemplate?: SubmitRequirementInfo;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      tableStyles,
      grFormStyles,
      modalStyles,
      css`
        :host {
          display: block;
        }
        gr-dialog {
          width: 50em;
        }
        gr-dialog .footer {
          width: 100%;
          display: flex;
          justify-content: flex-end;
        }
        .template-list {
          max-height: 400px;
          overflow-y: auto;
          border: 1px solid var(--border-color);
          border-radius: 4px;
        }
        .template-item {
          padding: var(--spacing-m);
          border-bottom: 1px solid var(--border-color);
          cursor: pointer;
          transition: background-color 0.2s;
        }
        .template-item:hover {
          background-color: var(--hover-background-color);
        }
        .template-item.selected {
          background-color: var(--selection-background-color);
          border-left: 3px solid var(--primary-text-color);
          padding-left: calc(var(--spacing-m) - 3px);
        }
        .template-item-name {
          font-weight: 600;
          margin-bottom: var(--spacing-s);
          color: var(--primary-text-color);
        }
        .template-field {
          display: flex;
          gap: var(--spacing-s);
          margin-top: var(--spacing-s);
          flex-wrap: wrap;
        }
        .field-label {
          font-weight: 500;
          color: var(--deemphasized-text-color);
          white-space: nowrap;
          flex-shrink: 0;
        }
        .field-value {
          color: var(--primary-text-color);
          word-break: break-word;
        }
        .field-value.monospace {
          font-family: monospace;
          color: var(--secondary-text-color);
        }
        .loading {
          text-align: center;
          padding: var(--spacing-l);
          color: var(--deemphasized-text-color);
        }
        .no-templates {
          padding: var(--spacing-l);
          text-align: center;
          color: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override render() {
    return html`
      <dialog id="templateDialog" tabindex="-1">
        <gr-dialog .cancelLabel=${''} .confirmLabel=${''}>
          <div class="header" slot="header">Create from Template</div>
          <div class="main" slot="main">
            <div class="gr-form-styles">
              ${when(
                this.loading,
                () => html`<div class="loading">Loading templates...</div>`,
                () =>
                  html`${when(
                    !this.templates || this.templates.length === 0,
                    () => html`<div class="no-templates">
                      No templates available
                    </div>`,
                    () => html`<div class="template-list">
                      ${this.templates!.map(
                        template => html`
                          <div
                            class="template-item ${this.selectedTemplate
                              ?.name === template.name
                              ? 'selected'
                              : ''}"
                            @click=${() => this.selectTemplate(template)}
                          >
                            <div class="template-item-name">
                              ${template.name}
                            </div>
                            ${when(
                              template.description,
                              () => html`
                                <div class="template-field">
                                  <span class="field-label">Description:</span>
                                  <span class="field-value">${template.description}</span>
                                </div>
                              `
                            )}
                            <div class="template-field">
                              <span class="field-label">Submittability:</span>
                              <span class="field-value monospace">${template.submittability_expression}</span>
                            </div>
                            ${when(
                              template.applicability_expression,
                              () => html`
                                <div class="template-field">
                                  <span class="field-label">Applicability:</span>
                                  <span class="field-value monospace">${template.applicability_expression}</span>
                                </div>
                              `
                            )}
                            ${when(
                              template.override_expression,
                              () => html`
                                <div class="template-field">
                                  <span class="field-label">Override:</span>
                                  <span class="field-value monospace">${template.override_expression}</span>
                                </div>
                              `
                            )}
                          </div>
                        `
                      )}
                    </div>`
                  )}`
              )}
            </div>
          </div>
          <div class="footer" slot="footer">
            <gr-button link @click=${this.handleCancel}>Cancel</gr-button>
            <gr-button
              link
              primary
              ?disabled=${!this.selectedTemplate}
              @click=${this.handleConfirm}
            >
              Select
            </gr-button>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  async show() {
    this.loading = true;
    this.selectedTemplate = undefined;
    this.templates = undefined;

    assertIsDefined(this.templateDialog, 'templateDialog');
    this.templateDialog.showModal();

    await this.fetchTemplates();
  }

  private async fetchTemplates() {
    if (!this.repo) {
      return;
    }

    try {
      const errFn: ErrorCallback = response => {
        firePageError(response);
      };

      const templates =
        await this.restApiService.getRepoSubmitRequirementTemplates(
          this.repo,
          errFn
        );
      this.templates = templates || [];
    } catch (e) {
      console.error('Failed to fetch templates:', e);
      this.templates = [];
    } finally {
      this.loading = false;
    }
  }

  private selectTemplate(template: SubmitRequirementInfo) {
    this.selectedTemplate = template;
  }

  private handleCancel() {
    assertIsDefined(this.templateDialog, 'templateDialog');
    this.templateDialog.close();
  }

  private handleConfirm() {
    if (!this.selectedTemplate) return;
    assertIsDefined(this.templateDialog, 'templateDialog');
    this.templateDialog.close();
    this.dispatchEvent(
      new CustomEvent('template-selected', {
        detail: {template: this.selectedTemplate},
        composed: true,
        bubbles: true,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-submit-requirements-template-dialog': GrRepoSubmitRequirementsTemplateDialog;
  }
}

