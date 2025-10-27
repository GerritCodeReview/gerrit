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
import {customElement, property, query} from 'lit/decorators.js';
import {styleMap} from 'lit/directives/style-map.js';

import {ModelInfo} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';

@customElement('chat-header')
export class ChatHeader extends LitElement {
  static override styles = css`
    :host {
      display: flex;
      padding: 0 var(--spacing-xxl) 0 var(--spacing-xl);
      align-items: center;
      height: 56px;
    }
    .title {
      font-size: 20px;
      font-weight: 500;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .md-text-button.select-model-trigger {
      height: auto;
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
      font-size: inherit;
      font-weight: inherit;
      margin-right: 3px;
    }
    .first-right-button {
      margin-left: auto;
    }

    /* 
     * Styles for menus in overlays. 
     * These may need to be moved to a global stylesheet to correctly style 
     * elements that are rendered outside of the component's shadow DOM.
    */
    .more-actions-menu md-menu-item md-icon {
      color: var(--deemphasized-text-color);
    }
    .select-model-menu {
      max-width: 500px;
    }
    md-text-button.select-model-trigger > span {
      min-width: 0;
    }
  `;

  @property({type: Boolean}) displayHistory = false;
  @property({type: Boolean}) displayAutoFix = false;
  @property({type: Array}) availableModels: ModelInfo[] = [];
  @property({type: Object}) activeModel?: ModelInfo;

  @query('#selectModelMenu') private selectModelMenu?: MdMenu;
  @query('#moreActionsMenu') private moreActionsMenu?: MdMenu;

  private readonly getChatModel = resolve(this, chatModelToken);

  override render() {
    return html`
      ${this.renderLeftSection()} ${this.renderRightButtons()}
      ${this.renderMenus()}
    `;
  }

  private renderLeftSection() {
    return html`
        <gr-icon class="gemini-icon" icon="ai-mark"></gr-icon>
        <md-text-button
          id="selectModelTrigger"
          class="select-model-trigger"
          @click=${
        () => this.selectModelMenu && (this.selectModelMenu.open = true)}
          ?disabled=${!this.activeModel}
        >
          <div class="title-group">
            <span class="title">Review Agent</span>
            ${
        this.activeModel ? html`
                  <div class="subtitle">
                    <span class="subtitle-text"
                      >${this.activeModel.short_text}</span
                    >
                    <md-icon class="arrow-drop-down">arrow_drop_down</md-icon>
                  </div>
                ` :
                           ''}
          </div>
        </md-text-button>
      `;
  }

  private renderRightButtons() {
    return html`
      <md-icon-button
        id="moreActionsTrigger"
        class="more-actions-trigger"
        aria-label="More actions"
        title="More"
        @click=${
        () => this.moreActionsMenu && (this.moreActionsMenu.open = true)}
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
        title="Close AI Chat panel"
        aria-label="Close AI Chat panel"
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
        ${this.availableModels.map(option => html`
            <md-menu-item @click=${() => this.onSwitchModel(option)}>
              <md-icon
                slot="start"
                style=${styleMap({
                                   visibility: this.activeModel?.model_id ===
                                           option.model_id ?
                                       'visible' :
                                       'hidden',
                                   })}
                >done</md-icon
              >
              ${option.full_display_text}
            </md-menu-item>
          `)}
      </md-menu>

      <md-menu
        id="moreActionsMenu"
        anchor="moreActionsTrigger"
        class="more-actions-menu"
        menu-corner="start-end"
        anchor-corner="end-end"
      >
        <a
          href="http://go/gob/users/ai-features"
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
    // this.store?.dispatch(switchModel({model}));
  }

  private closePanel() {
    this.dispatchEvent(new CustomEvent('close-chat-panel'));
  }

  private startNewConversation() {
    this.getChatModel().startEmptyNewChat('clear history button', true);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'chat-header': ChatHeader;
  }
}
