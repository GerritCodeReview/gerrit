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

@customElement('gr-chatbot-panel')
export class GrChatbotPanel extends LitElement {
  @property({type: Object}) change?: ParsedChangeInfo;

  @property({type: String}) patchNum?: PatchSetNum;

  @state() private input: string = '';

  @state() private messages: Array<{sender: 'user' | 'bot'; text: string}> = [];

  @state() private selectedModel: string = '';

  @state() private models: string[] = [];

  @state() private isLoading = false;

  @query('#aiPromptModal') private aiPromptModal!: HTMLDialogElement;

  private readonly restApiService = getAppContext().restApiService;

  static override styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      border-left: 1px solid var(--border-color, #ccc);
      background: var(--background-color-secondary, #f9f9f9);
    }

    .chat-container {
      display: flex;
      flex-direction: column;
      height: 100%;
    }

    .header {
      flex-shrink: 0;
      padding: 8px;
      font-weight: bold;
      font-size: 14px;
      background: var(--background-color-primary, #f1f1f1);
      border-bottom: 1px solid var(--border-color, #ccc);
    }

    .messages {
      flex: 1;
      overflow-y: auto;
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .message {
      max-width: 70%;
      padding: 10px 14px;
      border-radius: 16px;
      word-wrap: break-word;
      line-height: 1.4;
      font-size: 14px;
    }

    /* User → right */
    .message.user {
      align-self: flex-end;
      background: var(--chip-selected-background-color, #007bff);
      color: white;
      border-bottom-right-radius: 4px;
    }

    /* Bot → left */
    .message.bot {
      align-self: flex-start;
      background: #333940; /* white bubble */
      color: var(--primary-text-color, #000);
      border: 1px solid var(--border-color, #ccc); /* light border for separation */
      border-bottom-left-radius: 4px;
    }

    .input-area {
      flex-shrink: 0;
      display: flex;
      gap: 6px;
      padding: 10px;
      border-top: 1px solid var(--border-color, #ccc);
      background: var(--background-color-secondary, #fff);
    }

    textarea {
      flex: 1;
      resize: none;
      padding: 10px;
      border-radius: 8px;
      border: 1px solid #ccc;
      font-size: 14px;
    }

    button {
      padding: 6px 12px;
      border-radius: 6px;
      background: var(--primary-color, #007bff);
      color: white;
      border: none;
      cursor: pointer;
    }

    button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this.loadModels();
  }

  override render() {
    return html`
      <div class="chat-container">
        <div class="header">AI Code Review Assistant</div>
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
          <button @click=${this.send} ?disabled=${this.isLoading}>
            ${this.isLoading ? 'Wait...' : 'Send'}
          </button>
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

    const userMessage = {sender: 'user', text: this.input} as const;
    this.messages = [...this.messages, userMessage];
    this.input = '';
    this.isLoading = true;
    await this.updateComplete;
    this.scrollToBottom();

    const changeNum: NumericChangeId = Number(
      this.change?._number
    ) as NumericChangeId;
    const patchNum: PatchSetNum = this.patchNum as PatchSetNum;

    try {
      const content = (await this.restApiService.getAiCodeReview(
        this.selectedModel,
        userMessage.text,
        changeNum,
        patchNum
      )) as Record<string, {status: string; response: string}> | undefined;

      let botText = 'No response from AI 🤖';
      if (content?.[this.selectedModel]?.status === 'SUCCESS') {
        botText = content[this.selectedModel].response || botText;
      }

      this.messages = [...this.messages, {sender: 'bot', text: botText}];
    } catch (err) {
      this.messages = [
        ...this.messages,
        {sender: 'bot', text: 'Error fetching AI response'},
      ];
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

  private async loadModels() {
    try {
      const config = await this.restApiService.getConfig();
      this.models =
        config?.ai_models?.flatMap(m => m.ai_model_names ?? []) || [];
      this.selectedModel = this.models[0] || '';
    } catch (err) {
      console.error('Failed to load AI models', err);
      this.selectedModel = '';
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-chatbot-panel': GrChatbotPanel;
  }
}
