/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './chat-header';
import './chat-history';
import './gemini-message';
import './prompt-box';
import './splash-page';
import './user-message';

import {css, html, LitElement} from 'lit';
import {customElement, query, queryAll, state} from 'lit/decorators.js';

import {
  chatModelToken,
  ChatPanelMode,
  Turn,
} from '../../models/chat/chat-model';
import {changeModelToken} from '../../models/change/change-model';
import {resolve} from '../../models/dependency';
import {subscribe} from '../lit/subscription-controller';

enum Mode {
  HISTORY,
  SPLASH_PAGE,
  CHAT,
}

@customElement('chat-panel')
export class ChatPanel extends LitElement {
  @query('#scrollableDiv') readonly scrollableDiv?: HTMLElement;

  @queryAll('user-message') private userMessages?: NodeListOf<HTMLElement>;

  @queryAll('gemini-message') private geminiMessages?: NodeListOf<HTMLElement>;

  @state() turns: readonly Turn[] = [];

  @state() conversationId?: string;

  @state() nextTurnIndex = 0;

  @state() chatPanelMode: ChatPanelMode = ChatPanelMode.CONVERSATION;

  @state() userInput = '';

  @state() lastGeminiMessageMinHeight = 0;

  @state() privacyUrl?: string;

  @state() isChangePrivate = false;

  private readonly getChatModel = resolve(this, chatModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  static override styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: inherit;
      background-color: var(--background-color-secondary);
    }
    .default-option.mat-mdc-outlined-button {
      height: auto;
      min-height: var(--mat-button-outlined-container-height, 40px);
    }
    chat-header {
      flex: 0 0 auto;
    }
    .chat-panel-container {
      display: flex;
      flex-direction: column;
      margin: 6px 2px 2px;
      /* subtracting 10px for the margin-top and 6px for the border. */
      height: calc(100% - 6px - 10px);
      background-color: var(--background-color-primary);
      border: 1px solid var(--border-color);
      border-radius: 16px;
    }
    splash-page {
      scrollbar-width: thin;
    }
    .messages-container {
      flex-grow: 1;
      overflow: auto;
      scrollbar-width: thin;
      padding: var(--spacing-xl) var(--spacing-xl);
      position: relative;
    }
    .prompt-section {
      margin-top: auto;
      padding: 16px var(--spacing-xl) 16px;
      border: 1px solid var(--border-color);
      border-radius: 16px;
    }
    .google-symbols {
      font-variation-settings: 'FILL' 0, 'ROND' 50, 'wght' 400, 'GRAD' 0,
        'opsz' 24;
    }
    .default-options-container {
      overflow: auto;
      display: block;
    }
    .default-options-container .default-option {
      margin-bottom: 8px;
      margin-right: 8px;
      border-radius: 32px;
      height: 36px;
    }
    .ai-policy {
      /* @include typography.text-title(); TODO: check if this is still needed*/
      font-weight: 500;
      letter-spacing: 0.1px;
      font-size: var(--font-size-small);
      color: var(--deemphasized-text-color);
      margin: 4px 0 0;
    }
    .ai-policy a {
      color: var(--deemphasized-text-color);
    }
    gemini-message {
      margin-bottom: var(--spacing-xl);
    }
    gemini-message.latest {
      margin-bottom: 0;
    }
  `;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().turns$,
      x => (this.turns = x ?? [])
    );
    subscribe(
      this,
      () => this.getChatModel().conversationId$,
      x => (this.conversationId = x)
    );
    subscribe(
      this,
      () => this.getChatModel().nextTurnIndex$,
      x => (this.nextTurnIndex = x)
    );
    subscribe(
      this,
      () => this.getChatModel().mode$,
      x => (this.chatPanelMode = x)
    );
    subscribe(
      this,
      () => this.getChatModel().userInput$,
      x => (this.userInput = x)
    );
    subscribe(
      this,
      () => this.getChatModel().models$,
      x => (this.privacyUrl = x?.privacy_url)
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.isChangePrivate = x?.is_private ?? false)
    );
  }

  override render() {
    return html`
      <div class="chat-panel-container">
        <chat-header></chat-header>
        ${this.renderContent()}
      </div>
    `;
  }

  private renderContent() {
    switch (this.mode) {
      case Mode.HISTORY:
        return html`<chat-history></chat-history>`;
      case Mode.SPLASH_PAGE:
        return html`
          <splash-page .isChangePrivate=${this.isChangePrivate}></splash-page>
          ${this.renderPromptSection()}
        `;
      case Mode.CHAT:
        return this.renderChatContent();
    }
  }

  private renderChatContent() {
    return html`
      <div id="scrollableDiv" class="messages-container">
        ${this.turns.map(
          (turn, index) => html`
            <user-message .message=${turn.userMessage}></user-message>
            <gemini-message
              .turnIndex=${index}
              .isLatest=${index === this.turns.length - 1}
              class=${index === this.turns.length - 1 ? 'latest' : ''}
              style="min-height: ${index === this.turns.length - 1
                ? this.lastGeminiMessageMinHeight
                : 0}px"
            ></gemini-message>
          `
        )}
      </div>
      ${this.renderPromptSection()}
    `;
  }

  private renderPromptSection() {
    return html`
      <div class="prompt-section">
        <prompt-box
          .userInput=${this.userInput}
          .disabledMessage=${'Review Agent is disabled on private changes'}
          .isDisabled=${this.isChangePrivate}
        ></prompt-box>
        ${this.renderPrivacySection()}
      </div>
    `;
  }

  private renderPrivacySection() {
    if (!this.privacyUrl) return;
    return html`
      <div class="ai-policy">
        Review agent may display inaccurate info.
        <a href=${this.privacyUrl} target="_blank">AI privacy policy</a>
      </div>
    `;
  }

  get mode() {
    if (this.chatPanelMode === ChatPanelMode.HISTORY) {
      return Mode.HISTORY;
    }
    if (
      this.turns.length === 0 ||
      (this.turns.length === 1 && this.turns[0].userMessage.isBackgroundRequest)
    ) {
      return Mode.SPLASH_PAGE;
    }
    return Mode.CHAT;
  }

  override updated(changedProperties: Map<string, unknown>) {
    if (changedProperties.has('turns') && this.scrollableDiv) {
      const scrollableDivElement = this.scrollableDiv;
      const lastUserMessageElement =
        this.userMessages?.[this.userMessages.length - 1];
      const lastGeminiMessageElement =
        this.geminiMessages?.[this.geminiMessages.length - 1];
      if (lastUserMessageElement) {
        const scrollTop = computeScrollTop(
          scrollableDivElement,
          lastUserMessageElement
        );
        scrollableDivElement.scrollTop = scrollTop;
      }
      if (lastUserMessageElement && lastGeminiMessageElement) {
        const minHeight = computeGeminiMessageMinHeight(
          scrollableDivElement,
          lastUserMessageElement,
          lastGeminiMessageElement
        );
        this.lastGeminiMessageMinHeight = minHeight;
      }
    }
  }
}

function getPaddingBottom(element: HTMLElement) {
  return Number(getComputedStyle(element).paddingBottom.replace('px', ''));
}

function getPaddingTop(element: HTMLElement) {
  return Number(getComputedStyle(element).paddingTop.replace('px', ''));
}

function computeScrollTop(
  scrollableDivElement: HTMLElement,
  lastUserMessageElement: HTMLElement
) {
  const scrollableDivTopPadding = getPaddingTop(scrollableDivElement);
  return lastUserMessageElement.offsetTop - scrollableDivTopPadding;
}

/**
 * Computes the minimum height of the last gemini message such that the height
 * of the last user message + the height of the last gemini message (plus
 * padding) is equal to the height of the scrollable div.
 */
function computeGeminiMessageMinHeight(
  scrollableDivElement: HTMLElement,
  userMessageElement: HTMLElement,
  geminiMessageElement: HTMLElement
) {
  const scrollableDivHeight =
    scrollableDivElement.offsetHeight -
    getPaddingTop(scrollableDivElement) -
    getPaddingBottom(scrollableDivElement);
  const geminiMessagePaddingTop = getPaddingTop(geminiMessageElement);
  const geminiMessagePaddingBottom = getPaddingBottom(geminiMessageElement);
  return (
    scrollableDivHeight -
    userMessageElement.offsetHeight -
    geminiMessagePaddingTop -
    geminiMessagePaddingBottom
  );
}

declare global {
  interface HTMLElementTagNameMap {
    'chat-panel': ChatPanel;
  }
}
