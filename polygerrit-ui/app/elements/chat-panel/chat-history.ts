/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../shared/gr-date-formatter/gr-date-formatter';
import '../shared/gr-icon/gr-icon';

import {css, html, LitElement} from 'lit';
import {customElement, state} from 'lit/decorators.js';

import {Conversation} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {dateToTimestamp} from '../../utils/date-util';
import {subscribe} from '../lit/subscription-controller';

@customElement('chat-history')
export class ChatHistory extends LitElement {
  private readonly getChatModel = resolve(this, chatModelToken);

  @state() conversations: readonly Conversation[] = [];

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().conversations$,
      conversations => (this.conversations = conversations ?? [])
    );
  }

  static override styles = css`
    .conversation-card {
      width: 85%;
      margin: 0 auto;
      padding: var(--spacing-m);
      padding-left: 0px;
      display: flex;
      flex-direction: row;
      border-bottom: 1px solid var(--hairline);
      cursor: pointer;
      user-select: none;
    }
    .conversation-card * {
      cursor: pointer;
    }
    .conversation-card:hover {
      background-color: var(--background-color-secondary);
    }
    .conversation-icon {
      margin-top: var(--spacing-s);
    }
    .conversation-content {
      margin-left: var(--spacing-xxl);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      flex: 1;
      min-width: 0;
    }
    .conversation-content p {
      margin: 0px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      width: 100%;
    }
    .conversation-content p.ts {
      margin-top: var(--spacing-m);
      color: var(--deemphasized-text-color);
    }
  `;

  override render() {
    if (this.conversations.length === 0) {
      return html`<div>No conversations found.</div>`;
    }
    return html`
      ${this.conversations.map(
        conversation => html`
          <div
            class="conversation-card"
            @click=${() => this.loadConversation(conversation)}
          >
            <div class="conversation-icon">
              <gr-icon icon="history"></gr-icon>
            </div>
            <div class="conversation-content">
              <p>${conversation.title}</p>
              <p class="ts">
                <gr-date-formatter
                  withTooltip
                  .dateStr=${dateToTimestamp(
                    new Date(conversation.timestamp_millis)
                  )}
                ></gr-date-formatter>
              </p>
            </div>
          </div>
        `
      )}
    `;
  }

  // visible for testing
  loadConversation(conversation: Conversation) {
    this.getChatModel().loadConversation(conversation.id);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'chat-history': ChatHistory;
  }
}
