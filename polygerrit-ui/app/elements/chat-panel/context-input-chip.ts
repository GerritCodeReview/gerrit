/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/chips/assist-chip.js';
import '@material/web/icon/icon.js';
import '@material/web/menu/menu.js';
import '@material/web/menu/menu-item.js';
import '@material/web/textfield/filled-text-field.js';

import {MdMenu} from '@material/web/menu/menu';
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

import {ContextItem, ContextItemType} from '../../api/ai-code-review';
import {chatModelToken} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {assertIsDefined} from '../../utils/common-util';
import {fireAlert} from '../../utils/event-util';
import {subscribe} from '../lit/subscription-controller';

@customElement('context-input-chip')
export class ContextInputChip extends LitElement {
  @query('#contextMenu') private contextMenu?: MdMenu;

  @state() protected linkInputText = '';

  @state() protected selectedContextMenuItem: ContextItemType | null = null;

  @state() protected addLinkDialogOpened = false;

  private readonly getChatModel = resolve(this, chatModelToken);

  @state() protected contextMenuItems: readonly ContextItemType[] = [];

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().contextItemTypes$,
      (contextItemTypes: readonly ContextItemType[]) => {
        this.contextMenuItems = contextItemTypes;
      }
    );
  }

  static override styles = css`
    .context-input-container {
      position: relative;
    }
    /* .mat-mdc-standard-chip replaced by md-assist-chip */
    md-assist-chip {
      --md-assist-chip-container-height: 20px;
      --md-assist-chip-label-text-size: var(--font-size-small);
      --md-assist-chip-label-text-weight: var(--font-weight-medium);
      overflow: hidden;
      margin: 0;
      border-color: var(--chat-panel-chip-border-color);
      background-color: transparent;
      border-radius: 8px;
      padding: 0 4px 0 8px;
    }
    .add-icon {
      color: var(--primary-foreground-color);
    }
    .add-context-text {
      font-size: var(--font-size-small);
      font-weight: var(--font-weight-medium);
      color: var(--primary-foreground-color);
    }
    .add-link-container {
      position: absolute;
      text-align: center;
      width: 200px;
      left: 0;
      bottom: 25px;
    }
    .add-link-input {
      padding: var(--spacing-m);
      margin-left: var(--spacing-m);
      margin-right: var(--spacing-m);
      border: 1px solid var(--hairline-color);
      border-radius: 10px;
      color: currentColor;
      background-color: var(--surface-color);
      font-family: var(--font-family);
      font-size: var(--font-size-normal);
      outline: none;
      width: 100%;
      height: 23px;
      max-width: 100%;
    }
    .add-link-input::placeholder {
      color: var(--chat-card-placeholder-text-color);
    }
    .add-link-input:focus {
      background-color: var(--surface-color);
      border: 1px solid var(--hairline-color);
    }
    .context-menu-icon {
      width: 14px;
      height: 14px;
      margin-left: var(--spacing-m);
    }
  `;

  override render() {
    return html`
      <div class="context-input-container">
        <md-assist-chip
          id="addContextChip"
          .label=${'Add Context'}
          title="Add context to your query"
          aria-label="Add context to your query"
          @click=${() => this.contextMenu && (this.contextMenu.open = true)}
        >
          <md-icon slot="icon" class="add-icon">add</md-icon>
        </md-assist-chip>
        <md-menu id="contextMenu" anchor="addContextChip" y-offset="4">
          ${this.contextMenuItems.map(
            (item: ContextItemType) => html`
              <md-menu-item @click=${() => this.showLinkDialogInput(item)}>
                <md-icon slot="start">${item.icon}</md-icon>
                <div slot="headline">${item.name}</div>
              </md-menu-item>
            `
          )}
        </md-menu>
        ${when(
          this.addLinkDialogOpened,
          () => html`
            <div class="add-link-container">
              <input
                class="add-link-input"
                name="search"
                role="searchbox"
                tabindex="0"
                autocomplete="off"
                spellcheck="false"
                .placeholder=${this.selectedContextMenuItem!.placeholder}
                aria-label="Add external link"
                .value=${this.linkInputText}
                @input=${(e: Event) =>
                  (this.linkInputText = (e.target as HTMLInputElement).value)}
                @keydown=${(e: KeyboardEvent) => {
                  if (e.key === 'Enter') this.addLinkContext();
                }}
              />
            </div>
          `
        )}
      </div>
    `;
  }

  protected showLinkDialogInput(contextMenuItem: ContextItemType) {
    this.addLinkDialogOpened = true;
    this.selectedContextMenuItem = contextMenuItem;
  }

  protected addLinkContext() {
    assertIsDefined(this.selectedContextMenuItem, 'selected context menu item');
    const contextItem = this.selectedContextMenuItem.parse(this.linkInputText);
    if (contextItem) {
      this.dispatchEvent(
        new CustomEvent<ContextItem>('context-item-added', {
          detail: contextItem,
          bubbles: true,
          composed: true,
        })
      );
    } else {
      fireAlert(this, 'Could not parse the provided link.');
    }
    this.closeMenu();
    this.linkInputText = '';
  }

  private closeMenu() {
    this.addLinkDialogOpened = false;
    if (this.contextMenu) this.contextMenu.open = false;
  }
}

export interface ContextItemAddedEvent extends CustomEvent<ContextItem> {
  type: 'context-item-added';
}

declare global {
  interface HTMLElementEventMap {
    'context-item-added': ContextItemAddedEvent;
  }
  interface HTMLElementTagNameMap {
    'context-input-chip': ContextInputChip;
  }
}
