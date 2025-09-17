/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {ParsedChangeInfo} from '../../../types/types';
import {NumericChangeId, PatchSetNum} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {assertIsDefined} from '../../../utils/common-util';
import '../../shared/gr-button/gr-button';
import {materialStyles} from "../../../styles/gr-material-styles";

@customElement('gr-chatbot-panel')
export class GrChatbotPanel extends LitElement {
  @property({type: Object}) change?: ParsedChangeInfo;
  @property({type: String}) patchNum?: PatchSetNum;

  @state() private input: string = '';
  @state() private messages: Array<{sender: 'user' | 'bot'; text: string}> = [];
  @state() private selectedModel: string = '';
  @state() private models: string[] = [];
  @state() private pluginName: string = '';
  @state() private isLoading = false;
  @state() private promptFetched = false;

  @query('#aiPromptModal') private aiPromptModal!: HTMLDialogElement;

  private cachedPrompt: string | null = null;
  private readonly restApiService = getAppContext().restApiService;

  static override styles = [
    materialStyles,
    css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      border-left: 1px solid var(--border-color);
      background: var(--background-color-secondary);

      /* Material select overrides (theme-aware) */
      --md-outlined-select-container-shape: 6px;
      --md-outlined-select-outline-width: 1px;
      --md-outlined-select-outline-color: var(--border-color);
      --md-outlined-select-text-field-size: 28px;
      --md-outlined-select-text-field-padding: 4px 8px;
      --md-outlined-select-label-text-size: 13px;
      --md-outlined-select-focus-outline-color: var(--primary-color);
      --md-sys-color-on-surface: var(--primary-text-color);
      --md-sys-color-on-surface-variant: var(--primary-text-color);
      --md-sys-color-outline: var(--border-color);
    }

    .chat-container {
      display: flex;
      flex-direction: column;
      height: 100%;
    }

    .header {
      flex-shrink: 0;
      padding: var(--spacing-s);
      font-weight: bold;
      font-size: var(--font-size-normal);
      background: var(--background-color-primary);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: var(--spacing-m);
    }

    .model-select {
      font-size: var(--font-size-small);
      cursor: pointer;
    }

    .close-btn {
      border-radius: var(--border-radius);
      border: none;
      cursor: pointer;
      font-weight: bold;
      background: var(--primary-button-background-color);
      color: var(--primary-button-text-color);
    }

    .model-list {
      flex-shrink: 0;
      padding: var(--spacing-s) var(--spacing-m);
      font-size: var(--font-size-small);
      color: var(--secondary-text-color);
      border-bottom: 1px solid var(--border-color);
      background: var(--background-color-primary);
    }

    .messages {
      flex: 1;
      overflow-y: auto;
      padding: var(--spacing-l);
      display: flex;
      flex-direction: column;
      gap: var(--spacing-m);
    }

    .message {
      max-width: 70%;
      padding: var(--spacing-m) var(--spacing-l);
      border-radius: var(--border-radius-large);
      word-wrap: break-word;
      line-height: 1.4;
      font-size: var(--font-size-normal);
    }

    .message.user {
      align-self: flex-end;
      background: var(--chip-selected-background-color);
      color: var(--chip-selected-text-color);
      border-bottom-right-radius: var(--border-radius-small);
    }

    .message.bot {
      align-self: flex-start;
      background: var(--background-color-tertiary);
      color: var(--primary-text-color);
      border: 1px solid var(--border-color);
      border-bottom-left-radius: var(--border-radius-small);
    }

    .input-area {
      flex-shrink: 0;
      display: flex;
      gap: var(--spacing-m);
      padding: var(--spacing-m);
      border-top: 1px solid var(--border-color);
      background: var(--background-color-secondary);
    }
    
    textarea {
      flex: 1;
      resize: none;
      padding: var(--spacing-m);
      border-radius: var(--border-radius);
      border: 1px solid var(--border-color);
      font-size: var(--font-size-normal);
      color: var(--primary-text-color);
      background: var(--background-color-primary);
    }

    textarea:disabled {
      background: var(--disabled-background, var(--background-color-disabled));
      color: var(--disabled-text-color, var(--secondary-text-color));
      cursor: not-allowed;
      opacity: 0.7;
    }

    button {
      padding: var(--spacing-s) var(--spacing-l);
      border-radius: var(--border-radius);
      background: var(--primary-button-background-color);
      color: var(--primary-button-text-color);
      border: none;
      cursor: pointer;
    }

    button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  `,
  ];

  override connectedCallback() {
    super.connectedCallback();
    this.loadModels();
  }

  override updated(changedProperties: Map<string, unknown>) {
    super.updated(changedProperties);
    if (!this.promptFetched && this.change && this.patchNum && this.selectedModel) {
      this.promptFetched = true;
      this.fetchInitialPrompt();
    }
  }

  private closeChatbot() {
    this.style.display = 'none';
  }

  private async loadModels() {
    try {
      const config = await this.restApiService.getConfig();
      this.models = config?.ai_models?.flatMap(m => m.ai_model_names ?? []) || [];
      this.pluginName = config?.ai_models?.[0]?.plugin_name || ""; //fix this later.
      this.selectedModel = this.models[0] || '';
    } catch (err) {
      console.error('Failed to load AI models', err);
      this.selectedModel = '';
    }
  }

  private async fetchInitialPrompt() {
    if (!this.change || !this.patchNum || !this.selectedModel) return;

    const userMessage = { sender: 'user', text: 'Please review my change and share code review insights' } as const;
    this.messages = [...this.messages, userMessage];
    this.isLoading = true;
    await this.updateComplete;
    this.scrollToBottom();

    const changeNum: NumericChangeId = Number(this.change._number) as NumericChangeId;
    const patchNum: PatchSetNum = this.patchNum;

    try {
      if (this.cachedPrompt === null) {
        this.cachedPrompt = (await this.restApiService.getPromptContent(changeNum, patchNum, 'code_review')) ?? '';
      }

      const aiPrompt = this.cachedPrompt;
      const content = await this.restApiService.getAiCodeReview(
          this.selectedModel,
          this.pluginName,
          aiPrompt,
          changeNum,
          patchNum
      );

      let botText = 'No response from AI 🤖';
      if (content && content[this.selectedModel]?.status === 'SUCCESS') {
        botText = content[this.selectedModel].response;
      } else if (content && content[this.selectedModel]) {
        botText = content[this.selectedModel].response;
      }

      this.messages = [...this.messages, { sender: 'bot', text: botText }];
    } catch (err) {
      this.messages = [...this.messages, { sender: 'bot', text: 'Error fetching AI review' }];
    } finally {
      this.isLoading = false;
      await this.updateComplete;
      this.scrollToBottom();
    }
  }

  override render() {
    return html`
      <div class="chat-container">
        <div class="header">
          <span>AI Code Review Assistant</span>

          <div class="header-right">
            <!-- AI Model dropdown -->
            <md-outlined-select
                class="model-select"
                .value=${this.selectedModel}
                @change=${(e: Event) =>
                    (this.selectedModel = (e.target as HTMLSelectElement).value)}
            >
              ${this.models.map(
                  model => html`<md-select-option value=${model}>${model}</md-select-option>`
              )}
            </md-outlined-select>
            <gr-button class="close-btn" @click=${this.closeChatbot}>✕</gr-button>
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
            ?disabled=${this.isLoading}
            @input=${(e: Event) =>
                (this.input = (e.target as HTMLTextAreaElement).value)}
            @keydown=${(e: KeyboardEvent) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.send();
              }
            }}
            placeholder="Type a message..."
            rows="3"
        ></textarea>
        </div>

        <dialog id="aiPromptModal">
          <gr-ai-prompt-dialog
              id="aiPromptDialog"
              .change=${this.change}
              .patchNum=${this.patchNum}
              @close=${this.handleAiPromptDialogClose}
          ></gr-ai-prompt-dialog>
        </dialog>
      </div>
    `;
  }

  private async send() {
    if (!this.input.trim()) return;

    const userMessage = { sender: 'user', text: this.input } as const;
    this.messages = [...this.messages, userMessage];
    this.input = '';
    this.isLoading = true;
    await this.updateComplete;
    this.scrollToBottom();

    const changeNum: NumericChangeId = Number(this.change?._number) as NumericChangeId;
    const patchNum: PatchSetNum = this.patchNum as PatchSetNum;

    try {
      const content = (await this.restApiService.getAiCodeReview(
          this.selectedModel,
          this.pluginName,
          userMessage.text,
          changeNum,
          patchNum
      )) as Record<string, {status: string; response: string}> | undefined;

      let botText = 'No response from AI 🤖';
      if (content?.[this.selectedModel]?.status === 'SUCCESS') {
        botText = content[this.selectedModel].response || botText;
      }

      this.messages = [...this.messages, { sender: 'bot', text: botText }];
    } catch (err) {
      this.messages = [...this.messages, { sender: 'bot', text: 'Error fetching AI response' }];
    } finally {
      this.isLoading = false;
      await this.updateComplete;
      this.scrollToBottom();
    }
  }

  private scrollToBottom() {
    const messagesEl = this.shadowRoot?.querySelector('.messages');
    if (messagesEl) {
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }
  }


  private handleAiPromptDialogClose() {
    assertIsDefined(this.aiPromptModal, 'aiPromptModal');
    this.aiPromptModal.close();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-chatbot-panel': GrChatbotPanel;
  }
}
