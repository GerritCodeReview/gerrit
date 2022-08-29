/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '@polymer/iron-dropdown/iron-dropdown';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {LitElement, html, css} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {strToClassName} from '../../../utils/dom-util';
import {fireAlert} from '../../../utils/event-util';
import {when} from 'lit/directives/when.js';
import {IronDropdownElement} from '@polymer/iron-dropdown';
import {queryAndAssert} from '../../../utils/common-util';

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

  /** dropdown status is tracked here to lazy-load the inner DOM contents */
  @state() isDropdownOpen = false;

  @query('iron-dropdown') private dropdown?: IronDropdownElement;

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      css`
        iron-dropdown {
          box-shadow: var(--elevation-level-2);
          width: 520px;
          background-color: var(--dialog-background-color);
          border-radius: 4px;
        }
        [slot='dropdown-content'] {
          padding: var(--spacing-m) var(--spacing-l) var(--spacing-m);
        }
        .copy-link-row {
          margin-bottom: var(--spacing-m);
          display: flex;
          align-items: center;
        }
        .copy-link-row label {
          flex: 0 0 100px;
          color: var(--deemphasized-text-color);
        }
        .copy-link-row input {
          width: 320px;
        }
        .copy-link-row .shortcut {
          width: 25px;
          margin: 0 var(--spacing-m);
          color: var(--deemphasized-text-color);
        }
        .copy-link-row gr-copy-clipboard {
          flex: 0 0 20px;
        }
      `,
    ];
  }

  override render() {
    return html`<iron-dropdown
      .horizontalAlign=${'auto'}
      .verticalAlign=${'auto'}
      .verticalOffset=${24}
      @keydown=${this.shortcutsKeys}
      @opened-changed=${(e: CustomEvent) =>
        (this.isDropdownOpen = e.detail.value)}
    >
      ${when(this.isDropdownOpen, () => this.renderCopyLinks())}
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
      <label for=${id}>${label}</label
      ><input type="text" readonly="" id=${id} class="input" .value=${value} />
      <span class="shortcut">${`l - ${shortcut}`}</span>
      <gr-copy-clipboard hideInput="" text=${value}></gr-copy-clipboard>
    </div>`;
  }

  private async shortcutsKeys(e: KeyboardEvent) {
    const copyLink = this.copyLinks?.find(link => link.shortcut === e.key);
    if (!copyLink) return;
    await navigator.clipboard.writeText(copyLink.value);
    fireAlert(this, `${copyLink.label} was copied to clipboard`);
    this.closeDropdown();
  }

  toggleDropdown() {
    this.isDropdownOpen ? this.closeDropdown() : this.openDropdown();
  }

  private closeDropdown() {
    this.isDropdownOpen = false;
    this.dropdown?.close();
  }

  openDropdown() {
    this.dropdown?.open();
    this.awaitOpen(() => {
      queryAndAssert<HTMLInputElement>(this.dropdown, 'input')?.select();
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
