/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/chips/filter-chip.js';
import '@material/web/icon/icon.js';
import '../shared/gr-icon/gr-icon';

import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {ContextItem} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {fire} from '../../utils/event-util';

@customElement('context-chip')
export class ContextChip extends LitElement {
  private readonly getChatModel = resolve(this, chatModelToken);

  @property({type: String}) text = '';

  @property({type: Object}) contextItem?: ContextItem;

  @property({type: String}) subText?: string;

  @property({type: Boolean}) isSuggestion = false;

  @property({type: Boolean}) isCustomAction = false;

  @property({type: String}) tooltip?: string;

  @property({type: Boolean}) isRemovable = true;

  static override styles = css`
    :host {
      overflow: hidden;
      max-width: 300px;
    }
    md-filter-chip.suggested-chip {
      opacity: 0.5;
      border-style: dashed;
      border-color: var(--border-color);
    }
    md-filter-chip.suggested-chip:hover {
      opacity: 0.7;
    }
    md-filter-chip.custom-action-chip {
      --md-sys-color-primary: var(--custom-action-context-chip-color);
      --md-filter-chip-selected-container-color: transparent;
    }
    md-filter-chip {
      --md-sys-color-primary: var(--primary-text-color);
      --md-filter-chip-container-height: 20px;
      --md-filter-chip-label-text-size: var(--font-size-small);
      --md-filter-chip-label-text-weight: var(--font-weight-medium);
      --md-filter-chip-unselected-container-color: transparent;
      --md-filter-chip-outline-color: var(--border-color);
      overflow: hidden;
      margin: 0;
      border-radius: 8px;
    }
    .context-chip-icon-base {
      width: 12px;
      height: 12px;
    }
    .custom-action-icon {
      color: var(--custom-action-context-chip-color);
    }
    .external-context-text,
    .custom-action-text {
      margin-left: var(--spacing-s);
    }
    .custom-action-text {
      color: var(--custom-action-context-chip-color);
    }
    .subtext {
      color: var(--deemphasized-text-color);
    }
  `;

  override render() {
    const type = this.getChatModel().contextItemToType(this.contextItem);
    const icon = type?.icon ?? '';
    return html`
      <md-filter-chip
        class="context-chip ${this.isSuggestion ? 'suggested-chip' : ''} ${this
          .isCustomAction
          ? 'custom-action-chip'
          : ''}"
        .label=${this.text}
        ?selected=${this.isCustomAction}
        .title=${this.tooltip ?? ''}
        @click=${this.navigateToUrl}
        ?removable=${this.isRemovable && !this.isSuggestion}
        @remove=${this.onRemoveContextChip}
      >
        <gr-icon
          slot="icon"
          class=${this.isCustomAction ? 'custom-action-icon' : ''}
          .icon=${icon}
        ></gr-icon>
        ${when(
          this.subText,
          () => html`<span class="subtext">: ${this.subText}</span>`
        )}
        ${when(
          this.isSuggestion,
          () => html`
            <md-icon
              slot="trailing-icon"
              @click=${this.onAcceptContextItemSuggestion}
            >
              add
            </md-icon>
          `
        )}
      </md-filter-chip>
    `;
  }

  private onRemoveContextChip() {
    fire(this, 'remove-context-chip', {});
  }

  private onAcceptContextItemSuggestion() {
    fire(this, 'accept-context-item-suggestion', {});
  }

  private navigateToUrl() {
    const link = this.contextItem?.link?.trim();
    if (!link) return;
    const url = link.startsWith('http') ? link : `http://${link}`;
    window.open(url, '_blank');
  }
}

declare global {
  interface HTMLElementEventMap {
    'remove-context-chip': CustomEvent<{}>;
    'accept-context-item-suggestion': CustomEvent<{}>;
  }
  interface HTMLElementTagNameMap {
    'context-chip': ContextChip;
  }
}
