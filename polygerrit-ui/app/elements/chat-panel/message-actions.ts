/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/iconbutton/icon-button.js';
import '@material/web/icon/icon.js';
import '../shared/gr-copy-clipboard/gr-copy-clipboard';

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';

import {
  chatModelToken,
  ResponsePartType,
  Turn,
  UniqueTurnId,
} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {subscribe} from '../lit/subscription-controller';

/**
 * Component to display message actions for a Gemini message (e.g. thumbs up,
 * down and retry).
 */
@customElement('message-actions')
export class MessageActions extends LitElement {
  static override styles = css`
    :host {
      display: flex;
    }
    md-icon-button {
      margin-right: var(--spacing-l);
    }
    .feedback-button.thumbs-up-icon {
      margin-left: auto;
    }
  `;

  @property({type: Object}) turnId!: UniqueTurnId;

  @property({type: Boolean}) isLatest = false;

  @state() protected turns: readonly Turn[] = [];

  @state() protected conversationId?: string;

  private readonly getChatModel = resolve(this, chatModelToken);

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
  }

  override render() {
    return html`
      <gr-copy-clipboard
        ?hidden=${!this.isLatest}
        .text=${this.getGeminiMessageText()}
      ></gr-copy-clipboard>

      <md-icon-button
        ?hidden=${!this.regenerationIsEnabled()}
        class="regenerate-button"
        @click=${this.onRegenerate}
        aria-label="Regenerate response"
        title="Regenerate response"
      >
        <md-icon>refresh</md-icon>
      </md-icon-button>
    `;
  }

  protected onRegenerate() {
    this.getChatModel().regenerateMessage(this.turnId);
  }

  protected regenerationIsEnabled() {
    return this.isLatest;
  }

  private getGeminiMessageText() {
    const turns = this.turns;
    if (!turns || turns.length <= this.turnId.turnIndex) {
      return '';
    }
    const turn = turns[this.turnId.turnIndex];
    let text = '';
    turn.geminiMessage.responseParts.forEach(part => {
      if (part.type === ResponsePartType.TEXT) {
        text += part.content;
      }
    });
    return text;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'message-actions': MessageActions;
  }
}
