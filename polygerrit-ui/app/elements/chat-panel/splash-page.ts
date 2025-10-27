/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/button/filled-tonal-button.js';
import '@material/web/chips/chip-set.js';
import '@material/web/icon/icon.js';
import '@material/web/iconbutton/icon-button.js';
import '@material/web/progress/circular-progress.js';
import './gemini-message';
import './splash-page-action';

import {css, html, LitElement} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';

import {Action} from '../../api/ai-code-review';
import {chatModelToken, Turn} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {userModelToken} from '../../models/user/user-model';
import {AccountDetailInfo} from '../../types/common';
import {subscribe} from '../lit/subscription-controller';

/**
 * A component for displaying a splash page when there are no chat messages.
 */
@customElement('splash-page')
export class SplashPage extends LitElement {
  @state() account?: AccountDetailInfo;

  @state() isBackgroundRequestExpanded = false;

  @state() turns: readonly Turn[] = [];

  @state() actions: readonly Action[] = [];

  private readonly getChatModel = resolve(this, chatModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().turns$,
      x => (this.turns = x ?? [])
    );
    subscribe(
      this,
      () => this.getChatModel().actions$,
      x =>
        (this.actions = (x ?? []).filter(
          action => !!action.enable_splash_page_card
        ))
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
  }

  private get currentTurn(): Turn | undefined {
    if (this.turns.length === 0) return undefined;
    const turn = this.turns[this.turns.length - 1];
    return turn;
  }

  get backgroundRequest(): Turn | undefined {
    const turn = this.currentTurn;
    return turn?.userMessage.isBackgroundRequest ? turn : undefined;
  }

  static override styles = css`
    :host {
      overflow: auto;
      padding-left: 20px;
      padding-right: 20px;
      padding-top: 20px;
    }
    .splash-container {
      display: flex;
      justify-content: center;
      flex-flow: column nowrap;
    }
    .splash-greeting {
      background: linear-gradient(135deg, #217bfe 0, #078efb 33%, #ac87eb 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      -webkit-box-orient: vertical;
      color: transparent;
      display: -webkit-inline-box;
      font-size: 24px;
      font-weight: 400;
    }
    .material-icon {
      color: #5f6368;
    }
    .splash-question {
      color: var(--chat-splash-page-question-color);
      margin-bottom: 16px;
      margin-top: 0px;
      font-size: 24px;
      font-weight: 400;
      font-family: 'Google Sans';
    }
    .background-request-container {
      background-color: var(--chat-splash-page-info-panel-bg-color);
      padding: 15px;
      border-radius: 15px;
      margin-bottom: 12px;
      display: flex;
      flex-direction: column;
    }
    .background-request-container-inner {
      position: relative;
      max-height: 10em;
      min-height: 10em;
      overflow: hidden;
    }
    .background-request-container-inner.expanded {
      max-height: none;
      overflow: auto;
    }
    .background-request-container-overlay {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: linear-gradient(
        to top,
        var(--chat-splash-page-info-panel-bg-color),
        transparent 50%
      );
    }
    .user-background-question {
      font-family: 'Google Sans';
      font-size: 14px;
    }
    .expansion-button-container {
      display: flex;
      justify-content: center;
      align-items: center;
    }
    .info-panel-expansion-button {
      top: 10px;
      font-size: 1.5em;
      border: none;
      background-color: transparent;
      color: var(--primary-default);
      cursor: pointer;
    }
    .background-request-header {
      display: flex;
      align-items: center;
      gap: 5px;
      margin-bottom: 12px;
      font-weight: bold;
    }
    .background-request-header .thinking-spinner {
      margin-left: auto;
    }
    .action-container {
      background-color: transparent;
      margin-bottom: 20px;
      display: flex;
      justify-content: center;
      flex-wrap: wrap;
      width: 100%;
    }
    .action-container-title {
      height: 28px;
      display: flex;
      align-items: center;
      vertical-align: middle;
      font-size: 0.9em;
      font-weight: 500;
      color: var(--chat-splash-page-action-set-title-color);
    }
    .action-container-title .autoreview-run-all-button {
      margin-left: auto;
      margin-right: 8px;
    }
    .small-icon {
      /* TODO: find small-icon styles equivalent */
    }
  `;

  override render() {
    const displayName = this.account?.display_name ?? '';
    return html`
      <div class="splash-container">
        <h1 class="splash-greeting">Hello, ${displayName}</h1>
        <p class="splash-question">How can I help you today?</p>

        ${this.renderBackgroundRequest()}

        <div class="action-container-title suggested-actions-title">
          Capabilities
        </div>

        ${this.renderActionChipSet()}
      </div>
    `;
  }

  private renderBackgroundRequest() {
    const request = this.backgroundRequest;
    if (!request) return;
    return html`
      <div class="background-request-container">
        <div
          class="background-request-container-inner ${classMap({
            expanded: this.isBackgroundRequestExpanded,
          })}"
        >
          <div class="background-request-header">
            <md-icon class="material-icon">lightbulb_tips</md-icon>
            <span class="user-background-question"
              >${request.userMessage.content}</span
            >
            ${when(
              !request.geminiMessage.responseComplete,
              () => html`<md-circular-progress
                class="thinking-spinner"
                indeterminate
                size="17"
              ></md-circular-progress>`
            )}
          </div>
          <gemini-message
            .isBackgroundRequest=${true}
            .isLatest=${true}
            .turnIndex=${0}
          ></gemini-message>
          ${when(
            !this.isBackgroundRequestExpanded,
            () => html`<div class="background-request-container-overlay"></div>`
          )}
        </div>
        <div class="expansion-button-container">
          <button
            class="info-panel-expansion-button"
            title=${this.isBackgroundRequestExpanded ? 'Hide' : 'Show more'}
            aria-label=${this.isBackgroundRequestExpanded
              ? 'Hide'
              : 'Show more'}
            @click=${this.toggleBackgroundRequestExpansion}
          >
            ${this.isBackgroundRequestExpanded ? '–' : '...'}
          </button>
        </div>
      </div>
    `;
  }

  private renderActionChipSet() {
    return html`
      <md-chip-set class="action-container">
        ${this.actions.map(
          (action, index, array) => html`
            <splash-page-action
              .action=${action}
              .isFirst=${index === 0}
              .isLast=${index === array.length - 1}
            ></splash-page-action>
          `
        )}
      </md-chip-set>
    `;
  }

  protected toggleBackgroundRequestExpansion() {
    this.isBackgroundRequestExpanded = !this.isBackgroundRequestExpanded;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'splash-page': SplashPage;
  }
}
