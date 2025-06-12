/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '@polymer/iron-dropdown/iron-dropdown';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {css, html, LitElement, nothing} from 'lit';
import {createRef, ref, Ref} from 'lit/directives/ref.js';
import {customElement, property, query, state} from 'lit/decorators.js';
import {strToClassName} from '../../../utils/dom-util';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {copyToClipboard, queryAndAssert} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';
import {formStyles} from '../../../styles/form-styles';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';

export interface CopyLink {
  label: string;
  shortcut: string;
  value: string;
  multiline?: boolean;
}

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;

@customElement('gr-copy-links')
export class GrCopyLinks extends LitElement {
  copyClipboardRef: Ref<GrCopyClipboard> = createRef();

  @property({type: Array})
  copyLinks: CopyLink[] = [];

  @property({type: String})
  horizontalAlign: 'left' | 'right' = 'left';

  @property({type: String})
  shortcutPrefix = 'l - ';

  @state() isDropdownOpen = false;

  // private but used in screenshot tests
  @query('iron-dropdown') dropdown?: IronDropdownElement;

  static override get styles() {
    return [
      formStyles,
      css`
        iron-dropdown {
          box-shadow: var(--elevation-level-2);
          width: min(90vw, 640px);
          background-color: var(--dialog-background-color);
          border-radius: var(--border-radius);
        }
        [slot='dropdown-content'] {
          padding: var(--spacing-m) var(--spacing-l) var(--spacing-m);
        }
        .copy-link-row {
          margin-bottom: var(--spacing-m);
        }
        gr-copy-clipboard::part(text-container-wrapper-style) {
          flex: 1 1 420px;
        }
      `,
    ];
  }

  override render() {
    if (!this.copyLinks) return nothing;
    return html`<iron-dropdown
      .horizontalAlign=${this.horizontalAlign}
      .verticalAlign=${'top'}
      .verticalOffset=${20}
      @keydown=${this.handleKeydown}
      @opened-changed=${(e: ValueChangedEvent<boolean>) =>
        (this.isDropdownOpen = e.detail.value)}
    >
      ${this.renderCopyLinks()}
    </iron-dropdown>`;
  }

  private renderCopyLinks() {
    return html`<div slot="dropdown-content">
      ${this.copyLinks?.map((link, index) =>
        this.renderCopyLinkRow(link, index)
      )}
    </div>`;
  }

  private renderCopyLinkRow(copyLink: CopyLink, index?: number) {
    const {label, shortcut, value, multiline} = copyLink;
    const id = `${strToClassName(label, '')}-field`;
    return html`<div class="copy-link-row">
      <gr-copy-clipboard
        text=${value}
        label=${label}
        shortcut=${`${this.shortcutPrefix}${shortcut}`}
        id=${`${id}-copy-clipboard`}
        nowrap
        ?multiline=${multiline}
        ${index === 0 && ref(this.copyClipboardRef)}
      ></gr-copy-clipboard>
    </div>`;
  }

  private async handleKeydown(e: KeyboardEvent) {
    const copyLink = this.copyLinks?.find(link => link.shortcut === e.key);
    if (!copyLink) return;
    await copyToClipboard(copyLink.value, copyLink.label);
    this.closeDropdown();
  }

  toggleDropdown() {
    this.isDropdownOpen ? this.closeDropdown() : this.openDropdown();
  }

  private closeDropdown() {
    this.dropdown?.close();
  }

  openDropdown() {
    this.dropdown?.open();
    this.awaitOpen(() => {
      if (!this.copyClipboardRef?.value) return;
      queryAndAssert<HTMLInputElement>(
        this.copyClipboardRef.value,
        'input'
      )?.select();
    });
  }

  /**
   * NOTE: (milutin) Slightly hacky way to listen to the overlay actually
   * opening. It's from gr-editable-label. It will be removed when we
   * migrate out of iron-* components.
   */
  private awaitOpen(fn: () => void) {
    let iters = 0;
    const step = () => {
      setTimeout(() => {
        if (this.dropdown?.style.display !== 'none') {
          fn.call(this);
        } else if (iters++ < AWAIT_MAX_ITERS) {
          step.call(this);
        }
      }, AWAIT_STEP);
    };
    step.call(this);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-copy-links': GrCopyLinks;
  }
}
