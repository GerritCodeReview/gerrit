/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {fire} from '../../../utils/event-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {GrButton} from '../../shared/gr-button/gr-button';
import {getAppContext} from '../../../services/app-context';
import {fireError} from '../../../utils/event-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {ParsedChangeInfo} from '../../../types/types';
import {PatchSetNum} from '../../../types/common';
import {HELP_ME_REVIEW_PROMPT, IMPROVE_COMMIT_MESSAGE} from './prompts';
import {when} from 'lit/directives/when.js';
import {copyToClipboard} from '../../../utils/common-util';
import '@material/web/select/outlined-select';
import '@material/web/select/select-option';
import {materialStyles} from '../../../styles/gr-material-styles';

const PROMPT_TEMPLATES = {
  HELP_REVIEW: {
    id: 'help_review',
    label: 'Help me with review',
    prompt: HELP_ME_REVIEW_PROMPT,
  },
  IMPROVE_COMMIT_MESSAGE: {
    id: 'improve_commit_message',
    label: 'Improve commit message',
    prompt: IMPROVE_COMMIT_MESSAGE,
  },
  PATCH_ONLY: {
    id: 'patch_only',
    label: 'Just patch content',
    prompt: '{{patch}}',
  },
};

const CONTEXT_OPTIONS = [
  {label: '3 lines (default)', value: 3},
  {label: '10 lines', value: 10},
  {label: '25 lines', value: 25},
  {label: '50 lines', value: 50},
  {label: '100 lines', value: 100},
];

type PromptTemplateId = keyof typeof PROMPT_TEMPLATES;

@customElement('gr-ai-prompt-dialog')
export class GrAiPromptDialog extends LitElement {
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @query('#closeButton') protected closeButton?: GrButton;

  @state() change?: ParsedChangeInfo;

  @state() patchNum?: PatchSetNum;

  @state() patchContent?: string;

  @state() loading = false;

  @state() selectedTemplate: PromptTemplateId = 'HELP_REVIEW';

  @state() private context = 3;

  @state() private promptContent = '';

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.patchNum = x)
    );
  }

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      modalStyles,
      materialStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-m) 0;
        }
        section {
          display: flex;
          padding: var(--spacing-m) var(--spacing-xl);
        }
        .flexContainer {
          display: flex;
          justify-content: space-between;
          padding-top: var(--spacing-m);
        }
        .footer {
          justify-content: flex-end;
        }
        .info-text {
          font-size: var(--font-size-small);
          color: var(--deactivated-text-color);
          max-width: 420px;
        }
        .closeButtonContainer {
          align-items: flex-end;
          display: flex;
          flex: 0;
          justify-content: flex-end;
        }
        .content {
          width: 100%;
        }
        .options-bar {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: var(--spacing-m);
        }
        .template-selector {
        }
        .template-options {
          display: flex;
          flex-direction: column;
          gap: var(--spacing-s);
        }
        .template-option {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        textarea {
          width: 550px;
          height: 300px;
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          padding: var(--spacing-s);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          resize: vertical;
        }
        .toolbar {
          display: flex;
          gap: var(--spacing-s);
          margin-top: var(--spacing-s);
          justify-content: space-between;
          align-items: center;
        }
        .context-selector {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    return html`
      <section>
        <h3 class="heading-3">Copy AI Prompt (experimental)</h3>
      </section>
      ${when(
        this.loading,
        () => html` <div class="loading">Loading patch ...</div>`,
        () => html` <section class="flexContainer">
          <div class="content">
            ${when(
              this.getNumParents() === 1,
              () => html`<div class="options-bar">
                  <div class="template-selector">
                    <div class="template-options">
                      ${Object.entries(PROMPT_TEMPLATES).map(
                        ([key, template]) => html`
                          <label class="template-option">
                            <input
                              type="radio"
                              name="template"
                              .value=${key}
                              ?checked=${this.selectedTemplate === key}
                              @change=${(e: Event) => {
                                const input = e.target as HTMLInputElement;
                                this.selectedTemplate =
                                  input.value as PromptTemplateId;
                              }}
                            />
                            ${template.label}
                          </label>
                        `
                      )}
                    </div>
                  </div>
                  <div class="context-selector">
                    <md-outlined-select
                      label="Context"
                      .value=${this.context.toString()}
                      @change=${(e: Event) => {
                        const select = e.target as HTMLSelectElement;
                        this.context = Number(select.value);
                      }}
                    >
                      ${CONTEXT_OPTIONS.map(
                        option =>
                          html`<md-select-option
                            .value=${option.value.toString()}
                          >
                            <div slot="headline">${option.label}</div>
                          </md-select-option>`
                      )}
                    </md-outlined-select>
                  </div>
                </div>
                <textarea
                  .value=${this.promptContent}
                  readonly
                  placeholder="Patch content will appear here..."
                ></textarea>
                <div class="toolbar">
                  <div class="info-text">
                    You can paste this prompt in an AI Model if your project
                    code can be shared with AI. We recommend a thinking model.
                    You can also use it for an AI Agent as context (a reference
                    to a git change).
                  </div>
                  <gr-button @click=${this.handleCopyPatch}>
                    <gr-icon icon="content_copy" small></gr-icon>
                    Copy Prompt
                  </gr-button>
                </div>`,
              () => html`
                <div class="info-text">
                  This change has multiple parents. Currently, the "Create AI
                  Review Prompt" feature does not support multiple parents.
                </div>
              `
            )}
          </div>
        </section>`
      )}
      <section class="footer">
        <span class="closeButtonContainer">
          <gr-button
            id="closeButton"
            link
            @click=${(e: Event) => {
              this.handleCloseTap(e);
            }}
            >Close</gr-button
          >
        </span>
      </section>
    `;
  }

  override firstUpdated(changedProperties: PropertyValues) {
    super.firstUpdated(changedProperties);
    if (!this.getAttribute('role')) this.setAttribute('role', 'dialog');
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('patchContent') ||
      changedProperties.has('selectedTemplate')
    ) {
      this.updatePromptContent();
    }
    if (changedProperties.has('context')) {
      this.loadPatchContent();
    }
  }

  open() {
    if (this.getNumParents() === 1) {
      this.loadPatchContent();
    }
  }

  private getNumParents() {
    return this.change?.revisions[this.change.current_revision].commit?.parents
      .length;
  }

  private async loadPatchContent() {
    if (!this.change || !this.patchNum) return;
    this.loading = true;
    const content = await this.restApiService.getPatchContent(
      this.change._number,
      this.patchNum,
      this.context
    );
    this.loading = false;
    if (!content) {
      fireError(this, 'Failed to get patch content');
      return;
    }
    this.patchContent = content;
  }

  private updatePromptContent() {
    if (!this.patchContent) {
      this.promptContent = '';
      return;
    }
    const template = PROMPT_TEMPLATES[this.selectedTemplate];
    this.promptContent = template.prompt.replace(
      '{{patch}}',
      this.patchContent
    );
  }

  private async handleCopyPatch(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (!this.promptContent) return;
    await copyToClipboard(this.promptContent, 'AI Prompt');
  }

  private handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'close', {});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-ai-prompt-dialog': GrAiPromptDialog;
  }
}
