/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/iconbutton/icon-button.js';
import '@material/web/progress/circular-progress.js';

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';

import {Action, ContextItemType} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {parseLink} from '../../models/chat/context-item-util';
import {resolve} from '../../models/dependency';
import {isDefined} from '../../types/types';
import {fireAlert} from '../../utils/event-util';
import {subscribe} from '../lit/subscription-controller';

@customElement('splash-page-action')
export class SplashPageAction extends LitElement {
  @property({type: Object}) action?: Action;

  @property({type: Boolean}) isFirst = false;

  @property({type: Boolean}) isLast = false;

  @state() private contextItemTypes: readonly ContextItemType[] = [];

  private readonly getChatModel = resolve(this, chatModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().contextItemTypes$,
      types => (this.contextItemTypes = types)
    );
  }

  static override styles = css`
    :host {
      display: flex;
      justify-content: center;
      flex-wrap: wrap;
      width: 100%;
      position: relative;
    }

    .action-chip {
      display: flex;
      background-color: var(--chat-splash-page-info-panel-bg-color);
      color: var(--primary-default);
      height: 60px;
      align-items: center;
      border-radius: 4px;
      margin: 0;
      width: 100%;
      overflow: hidden;
      cursor: pointer;
    }

    .action-chip[disabled] {
      opacity: 0.6;
      cursor: default;
    }

    .action-chip.first-action-chip {
      border-top-left-radius: 16px;
      border-top-right-radius: 16px;
    }

    .action-chip.last-action-chip {
      border-bottom-left-radius: 16px;
      border-bottom-right-radius: 16px;
    }

    .action-chip.custom-action-chip {
      background-color: var(--custom-action-chip-bg-color);
    }

    .action-icon {
      padding: 4px;
      border-radius: 8px;
      background-color: var(--surface-default);
      flex-shrink: 0;
    }
    .action-text-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-x: hidden;
    }
    .main-action-text-container {
      margin-left: 20px;
      margin-top: 20px;
      font-weight: 400;
      display: flex;
      align-items: center;
    }
    .main-action-text-container.has-subtext {
      margin-top: 12px;
      margin-bottom: -2px;
    }
    .action-text {
      font-size: 1em;
      color: var(--text-default);
      overflow-x: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .action-subtext {
      vertical-align: super;
      margin-left: 5px;
      padding: 0px 15px 8px;
      font-size: 0.8em;
      color: var(--chat-splash-page-question-color);
      overflow-x: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .action-subtext.is-passed {
      color: var(--file-reviewed-color);
    }
    .action-subtext.is-actionable {
      color: var(--tonal-red);
    }
    .info-button {
      margin-left: 10px;
      margin-right: 10px;
    }
  `;

  override render() {
    if (!this.action) return;

    const chipClasses = {
      'action-chip': true,
      'first-action-chip': this.isFirst,
      'last-action-chip': this.isLast,
    };

    return html`
      <div
        class=${classMap(chipClasses)}
        title=${this.action.hover_text ?? ''}
        @click=${this.handleAction}
      >
        <gr-icon
          class="action-icon"
          icon=${this.action.icon ?? 'warning'}
        ></gr-icon>
        <div class="action-text-container">
          <div
            class=${classMap({
              'main-action-text-container': true,
              'has-subtext': !!this.action.subtext,
            })}
          >
            <span class="action-text">${this.action.display_text}</span>
            <md-icon-button
              class="small-icon info-button"
              aria-label="Show capability details"
              title="Capability details"
              @click=${(e: MouseEvent) => this.displayDetailsCard(e)}
            >
              <gr-icon icon="info" class="small-icon"></gr-icon>
            </md-icon-button>
          </div>
          ${when(
            this.action.subtext,
            () => html` <span
              class=${classMap({
                'action-subtext': true,
              })}
              >${this.action?.subtext}</span
            >`
          )}
        </div>
      </div>
    `;
  }

  handleAction() {
    const action = this.action;
    if (!action) return;

    const contextItems = (action.context_item_links ?? []).map(link =>
      parseLink(link, this.contextItemTypes)
    );
    if (contextItems.some(item => !item)) {
      fireAlert(this, 'Failed to parse one or more context item links.');
    }
    if (action.enable_send_without_input) {
      this.getChatModel().startNewChatWithPredefinedPrompt(
        action.id,
        contextItems.filter(isDefined)
      );
    } else {
      this.getChatModel().startNewChatWithUserInput(
        action.initial_user_prompt ?? '',
        action.id,
        contextItems.filter(isDefined)
      );
    }
  }

  displayDetailsCard(event: MouseEvent) {
    event.stopPropagation();
    // TODO: Implement this.
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'splash-page-action': SplashPageAction;
  }
}
