/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {getAppContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {NumericChangeId, PatchSetNum} from '../../../api/rest-api';
import {property} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {GrAiPromptDialog} from './gr-ai-prompt-dialog';

@customElement('gr-chatbot')
export class GrChatbot extends LitElement {
  @state() private input: string = '';

  @state() private isOpen = false;

  @property({type: Object}) change?: ParsedChangeInfo;

  @property({type: String}) patchNum?: PatchSetNum;

  @state() private messages: Array<{sender: 'user' | 'bot'; text: string}> = [];

  @state() private selectedModel: string = '';

  @state() private models: string[] = [];

  @state() private isLoading: boolean = false;

  private readonly restApiService = getAppContext().restApiService;

  @query('#aiPromptModal') private aiPromptModal!: HTMLDialogElement;

  @query('#aiPromptDialog') private aiPromptDialog!: GrAiPromptDialog;

  static override styles = css`
    :host {
      position: fixed;
      bottom: var(--spacing-m, 16px);
      right: var(--spacing-m, 16px);
      z-index: 1000;
    }

    .chatbot {
      width: 1000px;
      height: 750px;
      display: flex;
      flex-direction: column;
      background: var(--dialog-background-color, var(--background-color));
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius, 8px);
      box-shadow: var(--elevation-level-3);
      color: var(--primary-text-color);
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: var(--spacing-m, 8px);
      font-weight: var(--font-weight-bold, bold);
      background: var(
        --header-background-color,
        var(--table-header-background-color)
      );
      color: var(--header-text-color, var(--primary-text-color));
    }

    .header-controls {
      display: flex;
      align-items: center;
      gap: var(--spacing-s, 8px);
    }

    .messages {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow-y: auto;
      padding: var(--spacing-m, 8px);
      font-size: var(--font-size-normal, 14px);
    }

    .message {
      margin-bottom: var(--spacing-s, 6px);
      word-break: break-word;
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius, 8px);
      padding: var(--spacing-s, 8px) var(--spacing-m, 12px);
      font-size: var(--font-size-normal, 14px);
      line-height: 1.4;
      max-width: 70%;
    }

    .message.user {
      align-self: flex-end;
      background: var(--chip-selected-background-color, var(--primary-color));
      color: var(--chip-selected-text-color, #fff);
    }

    .message.bot {
      align-self: flex-start;
      background: var(--table-header-background-color);
      color: var(--primary-text-color);
    }

    .input-area {
      display: flex;
      border-top: 1px solid var(--border-color);
      padding: var(--spacing-s, 4px);
      background: var(
        --background-color-secondary,
        var(--background-color-secondary, #f9f9f9)
      );
    }

    /* Shared input/textarea styles */

    input,
    textarea {
      flex: 1;
      border: none;
      outline: none;
      padding: var(--spacing-m, 8px);
      background: var(--background-color);
      color: var(--primary-text-color);
      font-size: var(--font-size-normal, 14px);
    }

    textarea {
      resize: vertical;
      font-family: monospace;
    }

    select {
      background: var(--background-color);
      color: var(--primary-text-color);
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius, 6px);
      padding: var(--spacing-s, 6px) var(--spacing-m, 12px);
      font-size: var(--font-size-normal, 14px);
      cursor: pointer;
      transition: background 0.2s ease, box-shadow 0.2s ease;
    }

    select:focus {
      outline: none;
      border-color: var(--primary-color);
      box-shadow: 0 0 4px var(--primary-color);
    }

    dialog.themed-dialog {
      background: var(--dialog-background-color, var(--background-color));
      color: var(--primary-text-color);
      border: none; /* Remove default gray/white border */
      padding: 0; /* Remove default padding */
      border-radius: var(--border-radius, 8px);
      box-shadow: var(--elevation-level-3); /* Match Gerrit dialogs */
    }

    .button-primary,
    .header-controls button,
    .header-controls select,
    .input-area button,
    .ai-prompt-btn {
      background: var(--primary-button-background-color, var(--primary-color));
      color: var(--primary-button-text-color, #fff);
      border: none;
      border-radius: var(--border-radius, 6px);
      padding: var(--spacing-s, 6px) var(--spacing-m, 12px);
      font-size: var(--font-size-normal, 13px);
      cursor: pointer;
      transition: background 0.2s ease, box-shadow 0.2s ease;
    }

    .button-primary:hover,
    .header-controls button:hover,
    .input-area button:hover,
    .ai-prompt-btn:hover {
      background: var(--primary-button-hover-color, var(--button-hover-color));
    }

    .button-primary:active,
    .header-controls button:active,
    .input-area button:active,
    .ai-prompt-btn:active {
      background: var(--primary-button-active-color, var(--primary-color));
      color: var(--primary-button-active-text-color, #fff);
    }

    button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  `;

  override render() {
    if (!this.isOpen) {
      return html``;
    }
    return html`
      <div class="chatbot">
        <div class="header">
          <span>🤖 AI Code Review Assistant</span>
          <div class="header-controls">
            <button class="model-select" @click=${
              this.handleOpenAiPromptDialog
            }>
              Create AI Review Prompt
            </button>
            <select
                id="model-select"
                .value=${this.selectedModel}
                @change=${(e: Event) => {
                  this.selectedModel = (e.target as HTMLSelectElement).value;
                }}
            >
              ${
                this.models.length === 0
                  ? html` <option disabled>Loading models...</option>`
                  : this.models.map(
                      model => html` <option value=${model}>${model}</option>`
                    )
              }
            </select>
            <button @click=${this.close}>✖</button>
          </div>
        </div>
        <div class="messages">
          ${this.messages.map(
            m => html`
              <div class="message ${m.sender}">
                <gr-formatted-text
                  .content=${m.text}
                  .markdown=${true}
                ></gr-formatted-text>
              </div>
            `
          )}
        </div>
        <div class="input-area">
          <textarea
              .value=${this.input}
              @input=${(e: Event) => {
                this.input = (e.target as HTMLTextAreaElement).value;
              }}
              @keydown=${(e: KeyboardEvent) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault(); // prevent newline
                  this.send();
                }
              }}
              placeholder="Type a prompt message..."
              rows="4">
          </textarea>
          <button
              @click=${this.send}
              ?disabled=${this.isLoading}>
            ${this.isLoading ? 'Wait...' : 'Send'}
          </button>
        </div>
        <dialog id="aiPromptModal" class="themed-dialog" tabindex="-1">
          <gr-ai-prompt-dialog
              id="aiPromptDialog"
              @close=${this.handleAiPromptDialogClose}
          ></gr-ai-prompt-dialog>
        </dialog>
      </div>
      </div>
    `;
  }

  open() {
    this.isOpen = true;
  }

  close() {
    this.isOpen = false;
  }

  private async send() {
    if (!this.input.trim()) return;

    this.isLoading = true;

    const prompt: string = this.input;

    const userMessage: {sender: 'user'; text: string} = {
      sender: 'user',
      text: this.input,
    };
    this.messages = [...this.messages, userMessage];
    this.input = '';

    await this.updateComplete;
    this.scrollToBottom();

    const changeNum: NumericChangeId = Number(
      this.change?._number
    ) as NumericChangeId;
    const patchNum: PatchSetNum = this.patchNum as PatchSetNum;

    const content = (await this.restApiService.getAiCodeReview(
      this.selectedModel,
      prompt,
      changeNum,
      patchNum
    )) as AiResponseMap | undefined;

    let modelResponse = 'No response from AI 🤖';

    if (content) {
      const modelData = content[this.selectedModel];
      if (modelData?.status === 'SUCCESS') {
        modelResponse = modelData.response || modelResponse;
      }
    }

    setTimeout(() => {
      const botMessage: {sender: 'bot'; text: string} = {
        sender: 'bot',
        text: modelResponse,
      };
      this.messages = [...this.messages, botMessage];
      this.updateComplete.then(() => this.scrollToBottom());
      this.isLoading = false;
    }, 500);
  }

  /** 👇 New handlers for the dialog */
  private handleOpenAiPromptDialog() {
    assertIsDefined(this.aiPromptModal, 'aiPromptModal');

    if (!this.change) {
      console.warn('Cannot open AI prompt dialog: no change set in chatbot.');
      return;
    }

    // Pass props into the dialog BEFORE opening it
    if (this.aiPromptDialog) {
      this.aiPromptDialog.change = this.change;
      this.aiPromptDialog.patchNum = this.patchNum;
    }

    this.aiPromptModal.showModal();
    this.aiPromptDialog?.open();
  }

  private handleAiPromptDialogClose() {
    assertIsDefined(this.aiPromptModal, 'aiPromptModal');
    this.aiPromptModal.close();
  }

  private scrollToBottom() {
    const messagesEl = this.shadowRoot?.querySelector('.messages');
    if (messagesEl) {
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadModels();
  }

  private async loadModels() {
    try {
      const config = await this.restApiService.getConfig();

      if (config?.ai_models && Array.isArray(config.ai_models)) {
        this.models = config.ai_models.flatMap(m => m.ai_model_names ?? []);
      } else {
        this.models = [];
      }

      this.selectedModel = this.models[0];
    } catch (err) {
      console.error('Failed to load AI models:', err);
      this.selectedModel = '';
    }
  }

  public async sendMessage(text: string) {
    if (!text || text.trim() === '') return;

    this.messages = [...this.messages, {sender: 'user', text}];
    const changeNum: NumericChangeId = Number(
      this.change?._number
    ) as NumericChangeId;
    const patchNum: PatchSetNum = this.patchNum as PatchSetNum;

    // TODO: maybe this code as a function.
    const content = (await this.restApiService.getAiCodeReview(
      this.selectedModel,
      text,
      changeNum,
      patchNum
    )) as AiResponseMap | undefined;
    let modelResponse = 'No response from AI 🤖';
    if (content) {
      const modelData = content[this.selectedModel];
      if (modelData?.status === 'SUCCESS') {
        modelResponse = modelData.response || modelResponse;
      }
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-chatbot': GrChatbot;
  }
}

interface AiModelResponse {
  response: string;
  status: 'SUCCESS' | 'FAILURE' | string;
}

interface AiResponseMap {
  [model: string]: AiModelResponse;
}
