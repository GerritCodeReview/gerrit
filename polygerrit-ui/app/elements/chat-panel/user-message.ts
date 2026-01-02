/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './context-chip';
import '@material/web/chips/filter-chip.js';

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {map} from 'lit/directives/map.js';
import {when} from 'lit/directives/when.js';

import {ContextItem} from '../../api/ai-code-review';
import {UserMessage as UserMessageState} from '../../models/chat/chat-model.js';
import {resolve} from '../../models/dependency';
import {userModelToken} from '../../models/user/user-model';
import {AccountDetailInfo} from '../../types/common';
import {subscribe} from '../lit/subscription-controller';

const MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED = 3;

/**
 * A component to display a single message sent by the user in the chat
 * conversation. This includes the user's textual input and any associated
 * context items (e.g., code snippets, file paths, or suggestions) that were
 * part of the message.
 */
@customElement('user-message')
export class UserMessage extends LitElement {
  @property({type: Object}) message!: UserMessageState;

  @state() account?: AccountDetailInfo;

  @state() showAllContextItems = false;

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
  }

  private get content() {
    return this.message.content;
  }

  private get contextItems() {
    return this.message.contextItems;
  }

  private toggleShowAllContext() {
    this.showAllContextItems = !this.showAllContextItems;
  }

  private get regularContextItems() {
    return this.contextItems.filter(item => !!item.title);
  }

  private get numExcessContextItems() {
    return Math.max(
      0,
      this.regularContextItems.length - MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED
    );
  }

  private get shouldShowContextToggle() {
    return this.numExcessContextItems > 0;
  }

  private get contextToggleTooltip() {
    return this.showAllContextItems
      ? 'Collapse'
      : `${this.numExcessContextItems} additional items or suggestions`;
  }

  private get contextToggleText() {
    return this.showAllContextItems ? '▲' : `+${this.numExcessContextItems}`;
  }

  static override styles = css`
    :host {
      display: flex;
      flex-direction: column;
      padding-bottom: var(--spacing-xl);
    }

    .user-input-container {
      padding-top: var(--spacing-m);
    }

    .text-content {
      white-space: pre-wrap;
      margin: 0px;
    }

    .context-chip-set {
      margin-top: var(--spacing-m);
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px;
    }

    gr-avatar {
      display: block;
      height: 24px;
      width: 24px;
      margin-right: 10px;
    }

    md-filter-chip.context-toggle-chip {
      margin: 0;
      margin-left: auto;
      --md-filter-chip-unselected-outline-color: var(--border-color);
      --md-filter-chip-unselected-container-color: var(--elevation-2);
      --md-filter-chip-container-shape: 8px;
      --md-filter-chip-container-height: 20px;

      /* from @include mat.chips-overrides */
      --md-filter-chip-label-text-line-height: var(
        --line-height-small,
        1.25rem
      );
      --md-filter-chip-label-text-size: var(--font-size-small, 0.75rem);
      --md-filter-chip-label-text-weight: var(--font-weight-medium, 500);
      --md-filter-chip-label-text-color: var(--primary-default, purple);
      cursor: pointer;
    }
  `;

  override render() {
    if (!this.message) {
      return html``;
    }
    return html`
      <div class="user-info">
        <gr-avatar .account=${this.account} .imageSize=${32}></gr-avatar>
      </div>
      <div class="user-input-container">
        <p class="text-content">${this.content}</p>
        <div class="context-chip-set">
          ${map(
            this.showAllContextItems
              ? this.regularContextItems
              : this.regularContextItems.slice(
                  0,
                  MAX_VISIBLE_CONTEXT_ITEMS_COLLAPSED
                ),
            (contextItem: ContextItem) => html`
              <context-chip
                class="external-context"
                .text=${contextItem.title}
                .contextItem=${contextItem}
                .isRemovable=${false}
                .tooltip=${contextItem.tooltip}
              ></context-chip>
            `
          )}
          ${when(
            this.shouldShowContextToggle,
            () => html`
              <md-filter-chip
                class="context-toggle-chip"
                .label=${this.contextToggleText}
                .title=${this.contextToggleTooltip}
                @click=${this.toggleShowAllContext}
              ></md-filter-chip>
            `
          )}
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'user-message': UserMessage;
  }
}
