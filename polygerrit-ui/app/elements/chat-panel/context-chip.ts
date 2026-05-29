/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/chips/filter-chip.js';
import '@material/web/icon/icon.js';
import '../shared/gr-icon/gr-icon';

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {truncatePath} from '../../utils/path-list-util';
import {ContextItem} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {fire} from '../../utils/event-util';
import {subscribe} from '../lit/subscription-controller';
import {classMap} from 'lit/directives/class-map.js';
import {materialStyles} from '../../styles/gr-material-styles';

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

  @state() private supportsThisChange = true;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().provider$,
      provider => {
        this.supportsThisChange = provider?.supports_this_change ?? true;
      }
    );
  }

  static override styles = [
    materialStyles,
    css`
      :host {
        overflow: hidden;
        max-width: 300px;
      }
      md-filter-chip.suggested-chip {
        opacity: 0.5;
        border-style: dashed;
        border-width: 1px;
        border-color: var(--border-color);
        --md-filter-chip-outline-color: transparent;
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
        --md-filter-chip-label-text-color: var(--primary-text-color);
        --md-filter-chip-container-height: 20px;
        --md-filter-chip-label-text-size: var(--font-size-small);
        --md-filter-chip-label-text-weight: var(--font-weight-medium);
        --md-filter-chip-unselected-container-color: transparent;
        --md-filter-chip-outline-color: var(--border-color);
        --md-filter-chip-hover-label-text-color: var(--primary-text-color);
        --md-filter-chip-focus-label-text-color: var(--primary-text-color);
        --md-filter-chip-pressed-label-text-color: var(--primary-text-color);
        overflow: hidden;
        margin: 0;
        border-radius: 8px;
      }
      md-filter-chip.no-link {
        --md-filter-chip-unselected-hover-state-layer-color: transparent;
        --md-ripple-hover-color: transparent;
        --md-ripple-pressed-color: transparent;
        --md-ripple-focus-color: transparent;
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
      .hidden {
        visibility: hidden;
        pointer-events: none;
      }
      .context-chip-container {
        display: flex;
        flex-direction: row;
        flex-wrap: nowrap;
        align-items: center;
      }
      .context-chip-title {
        padding-top: 2px;
      }
    `,
  ];

  override render() {
    const type = this.getChatModel().contextItemToType(this.contextItem);
    const icon = type?.icon ?? '';
    return html`
      <md-filter-chip
        class=${classMap({
          'context-chip': true,
          'suggested-chip': this.isSuggestion,
          'custom-action-chip': this.isCustomAction,
          'no-link': !this.contextItem?.link,
          hidden: !this.supportsThisChange,
        })}
        .title=${this.contextItem?.tooltip ??
        this.tooltip ??
        this.contextItem?.title ??
        this.text}
        @click=${this.handleChipClick}
        ?removable=${this.isRemovable && !this.isSuggestion}
        @remove=${this.onRemoveContextChip}
      >
        ${when(
          icon,
          () => html` <gr-icon
            slot="icon"
            class=${this.isCustomAction ? 'custom-action-icon' : ''}
            .icon=${icon}
          ></gr-icon>`
        )}
        <div class="context-chip-container">
          <span class="context-chip-title">
            ${truncatePath(this.contextItem?.title ?? this.text, 2)}
            ${when(
              this.subText,
              () => html`<span class="subtext">: ${this.subText}</span>`
            )}
          </span>
          ${when(
            this.isSuggestion,
            () => html` <gr-icon icon="add"></gr-icon> `
          )}
        </div>
      </md-filter-chip>
    `;
  }

  private onRemoveContextChip() {
    fire(this, 'remove-context-chip', {});
  }

  protected handleChipClick(e: MouseEvent) {
    // Always prevent the default filter chip behavior (selection/checkmark).
    e.preventDefault();
    e.stopPropagation();

    if (this.isSuggestion) {
      fire(this, 'accept-context-item-suggestion', {});
      return;
    }

    const link = this.contextItem?.link?.trim();
    if (link) {
      const url = link.startsWith('http') ? link : `http://${link}`;
      window.open(url, '_blank');
    }
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
