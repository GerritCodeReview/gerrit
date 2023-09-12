/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '@polymer/iron-dropdown/iron-dropdown';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {LitElement, html, css, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {strToClassName} from '../../../utils/dom-util';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {copyToClipbard, queryAndAssert} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';
import {formStyles} from '../../../styles/form-styles';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';

export interface CopyLink {
  label: string;
  shortcut: string;
  value: string;
}

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;

@customElement('gr-copy-links')
export class GrCopyLinks extends LitElement {
  @property({type: Array})
  copyLinks: CopyLink[] = [];

  @state() isDropdownOpen = false;

  @query('iron-dropdown') private dropdown?: IronDropdownElement;

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
      .horizontalAlign=${'left'}
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
      ${this.copyLinks?.map(link => this.renderCopyLinkRow(link))}
    </div>`;
  }

  private renderCopyLinkRow(copyLink: CopyLink) {
    const {label, shortcut, value} = copyLink;
    const id = `${strToClassName(label, '')}-field`;
    return html`<div class="copy-link-row">
      <gr-copy-clipboard
        text=${value}
        label=${label}
        shortcut=${`l - ${shortcut}`}
        id=${`${id}-copy-clipboard`}
        nowrap
      ></gr-copy-clipboard>
    </div>`;
  }

  private async handleKeydown(e: KeyboardEvent) {
    const copyLink = this.copyLinks?.find(link => link.shortcut === e.key);
    if (!copyLink) return;
    await copyToClipbard(copyLink.value, copyLink.label);
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
      const copyClipboard = queryAndAssert<GrCopyClipboard>(
        this.dropdown,
        'gr-copy-clipboard'
      );
      if (copyClipboard) {
        queryAndAssert<HTMLInputElement>(copyClipboard, 'input')?.select();
      }
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
