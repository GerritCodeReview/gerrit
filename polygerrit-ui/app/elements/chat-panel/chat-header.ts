/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/iconbutton/icon-button.js';
import '@material/web/button/text-button.js';
import '@material/web/icon/icon.js';
import '@material/web/menu/menu.js';
import '@material/web/menu/menu-item.js';
import '../shared/gr-icon/gr-icon';

import {MdMenu} from '@material/web/menu/menu';
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';

import {fire} from '../../utils/event-util';
import {subscribe} from '../lit/subscription-controller';
import {ModelInfo} from '../../api/ai-code-review';
import {chatModelToken, ChatPanelMode} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';

@customElement('chat-header')
export class ChatHeader extends LitElement {
  static override styles = css`
    :host {
      display: flex;
      padding: 0 var(--spacing-xxl) 0 var(--spacing-xl);
      align-items: center;
      color: var(--primary-text-color);
    }
    .title {
      color: var(--primary-text-color);
      font-family: var(--header-font-family);
      font-size: var(--font-size-h2);
      font-weight: var(--font-weight-h2);
      line-height: var(--line-height-h2);
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    md-text-button.select-model-trigger {
      height: auto;
      min-width: 50px;
    }
    .title-group {
      display: flex;
      flex-direction: column;
      align-items: start;
    }
    .subtitle {
      font-size: 12px;
      font-weight: 500;
      color: var(--deemphasized-text-color);
      display: flex;
      flex-direction: row;
      align-items: center;
      max-width: 100%;
    }
    .subtitle-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .arrow-drop-down {
      height: 16px;
      width: 16px;
      font-size: 18px;
      margin-top: -2px;
    }
    :host > md-icon-button,
    :host > gr-icon {
      flex-shrink: 0;
    }
    md-icon-button {
      height: 40px;
      width: 40px;
      font-size: 20px;
      font-weight: 500;
    }
    md-icon-button.back-arrow {
      height: 32px;
      width: 32px;
      padding-right: 0px;
    }
    md-icon {
      vertical-align: middle;
    }
    md-icon-button:disabled md-icon {
      color: var(--deemphasized-text-color);
    }
    .gemini-icon {
      color: var(--deemphasized-text-color);
      font-size: 24px;
      margin-right: 3px;
    }
    .first-right-button {
      margin-left: auto;
    }
    .more-actions-menu md-menu-item md-icon {
      color: var(--deemphasized-text-color);
    }
    .select-model-menu {
      max-width: 500px;
    }
    md-text-button.select-model-trigger > span {
      min-width: 0;
    }
    md-icon-button {
      color: var(--primary-text-color);
      --md-icon-button-icon-color: var(--primary-text-color);
      --md-icon-button-hover-icon-color: var(--primary-text-color);
    }
    md-icon-button md-icon {
      color: var(--primary-text-color);
    }
  `;

  @state() availableModels: ModelInfo[] = [];

  @state() selectedModel?: ModelInfo;

  @state() documentationUrl?: string;

  @state() mode: ChatPanelMode = ChatPanelMode.CONVERSATION;

  @query('#selectModelMenu') private selectModelMenu?: MdMenu;

  @query('#moreActionsMenu') private moreActionsMenu?: MdMenu;

  private readonly getChatModel = resolve(this, chatModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().availableModelsMap$,
      x => (this.availableModels = [...x.values()])
    );
    subscribe(
      this,
      () => this.getChatModel().selectedModel$,
      x => (this.selectedModel = x)
    );
    subscribe(
      this,
      () => this.getChatModel().models$,
      x => (this.documentationUrl = x?.documentation_url)
    );
    subscribe(
      this,
      () => this.getChatModel().mode$,
      x => (this.mode = x ?? ChatPanelMode.CONVERSATION)
    );
  }

  override render() {
    return html`
      ${this.renderLeftSectionChat()} ${this.renderLeftSectionHistory()}
      ${this.renderRightButtons()} ${this.renderMenus()}
    `;
  }

  private renderLeftSectionHistory() {
    if (this.mode !== ChatPanelMode.HISTORY) return;
    return html`
      <md-icon-button
        class="back-arrow"
        aria-label="Back to chat"
        title="Back to chat"
        @click=${this.backToChat}
      >
        <md-icon>arrow_back_ios</md-icon>
      </md-icon-button>
      <span class="title">History</span>
    `;
  }

  private renderLeftSectionChat() {
    if (this.mode !== ChatPanelMode.CONVERSATION) return;
    return html`
      <gr-icon class="gemini-icon" icon="robot_2"></gr-icon>
      <md-text-button
        id="selectModelTrigger"
        class="select-model-trigger"
        @click=${() =>
          this.selectModelMenu && (this.selectModelMenu.open = true)}
        ?disabled=${!this.selectedModel}
      >
        <div class="title-group">
          <span class="title">Review Agent</span>
          ${this.selectedModel
            ? html`
                <div class="subtitle">
                  <span class="subtitle-text"
                    >${this.selectedModel?.short_text}</span
                  >
                  <md-icon class="arrow-drop-down">arrow_drop_down</md-icon>
                </div>
              `
            : ''}
        </div>
      </md-text-button>
    `;
  }

  private renderRightButtons() {
    return html`
      <md-icon-button
        class="history-button first-right-button"
        aria-label="Show history"
        title="Show history"
        @click=${this.showHistory}
      >
        <md-icon>history</md-icon>
      </md-icon-button>

      <md-icon-button
        id="moreActionsTrigger"
        class="more-actions-trigger"
        aria-label="More actions"
        title="More"
        @click=${() =>
          this.moreActionsMenu && (this.moreActionsMenu.open = true)}
      >
        <md-icon>more_vert</md-icon>
      </md-icon-button>

      <md-icon-button
        class="clear-history-button"
        @click=${this.startNewConversation}
        title="Start a new conversation"
        aria-label="Start a new conversation"
      >
        <md-icon>add</md-icon>
      </md-icon-button>

      <md-icon-button
        class="close-button"
        @click=${this.closePanel}
        title="Close Review Agent panel"
        aria-label="Close Review Agent panel"
      >
        <md-icon>clear</md-icon>
      </md-icon-button>
    `;
  }

  private renderMenus() {
    return html`
      <md-menu
        id="selectModelMenu"
        anchor="selectModelTrigger"
        class="select-model-menu"
      >
        ${this.availableModels.map(
          option => html`
            <md-menu-item @click=${() => this.onSwitchModel(option)}>
              <md-icon
                slot="start"
                style=${styleMap({
                  visibility:
                    this.selectedModel?.model_id === option.model_id
                      ? 'visible'
                      : 'hidden',
                })}
                >done</md-icon
              >
              ${option.full_display_text}
            </md-menu-item>
          `
        )}
      </md-menu>
      ${this.renderDocumentationMenu()}
    `;
  }

  private renderDocumentationMenu() {
    if (!this.documentationUrl) return;
    return html`
      <md-menu
        id="moreActionsMenu"
        anchor="moreActionsTrigger"
        class="more-actions-menu"
        menu-corner="start-end"
        anchor-corner="end-end"
      >
        <a
          href=${this.documentationUrl}
          target="_blank"
          rel="noopener noreferrer"
          style="text-decoration: none;"
        >
          <md-menu-item>
            <md-icon slot="start">help_outline</md-icon>
            Documentation
          </md-menu-item>
        </a>
      </md-menu>
    `;
  }

  private onSwitchModel(model: ModelInfo) {
    this.getChatModel().selectModel(model.model_id);
  }

  private closePanel() {
    fire(this, 'close-chat-panel', {});
  }

  private startNewConversation() {
    this.getChatModel().setMode(ChatPanelMode.CONVERSATION);
    this.getChatModel().startEmptyNewChat(true);
  }

  private showHistory() {
    this.getChatModel().setMode(ChatPanelMode.HISTORY);
  }

  private backToChat() {
    this.getChatModel().setMode(ChatPanelMode.CONVERSATION);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'chat-header': ChatHeader;
  }
  interface HTMLElementEventMap {
    'close-chat-panel': CustomEvent;
  }
}
