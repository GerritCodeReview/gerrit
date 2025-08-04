/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {createRef, ref, Ref} from 'lit/directives/ref.js';
import {customElement, property, query, state} from 'lit/decorators.js';
import {strToClassName} from '../../../utils/dom-util';
import {copyToClipboard, queryAndAssert} from '../../../utils/common-util';
import {formStyles} from '../../../styles/form-styles';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '@material/web/menu/menu';
import {MdMenu} from '@material/web/menu/menu';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

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

  @property({type: Number})
  verticalOffset = 10;

  @state() isDropdownOpen = false;

  // private but used in screenshot tests
  @query('md-menu') dropdown?: MdMenu;

  static override get styles() {
    return [
      formStyles,
      css`
        md-menu {
          white-space: nowrap;
          --md-menu-container-color: var(--dialog-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
        }
        .dropdown-content {
          width: min(90vw, 640px);
          padding: var(--spacing-m) var(--spacing-l) var(--spacing-m);
          box-shadow: var(--elevation-level-2);
          border-radius: var(--border-radius);
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

  override connectedCallback() {
    super.connectedCallback();
    if (this.isDropdownOpen) {
      this.setUpGlobalEventListeners();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('isDropdownOpen')) {
      if (this.isDropdownOpen) {
        this.setUpGlobalEventListeners();
      } else {
        this.cleanUpGlobalEventListeners();
      }
    }
  }

  private setUpGlobalEventListeners() {
    const passiveOptions: AddEventListenerOptions = {passive: true};

    window.addEventListener('resize', this.onWindowResize, passiveOptions);
    window.addEventListener('scroll', this.onWindowResize, passiveOptions);
  }

  private cleanUpGlobalEventListeners() {
    const passiveOptions: AddEventListenerOptions = {passive: true};

    window.removeEventListener('resize', this.onWindowResize, passiveOptions);
    window.removeEventListener('scroll', this.onWindowResize, passiveOptions);
  }

  private readonly onWindowResize = () => {
    this.dropdown?.reposition();
  };

  override render() {
    if (!this.copyLinks) return nothing;
    return html`<md-menu
      default-focus="none"
      tabindex="-1"
      .menuCorner=${this.horizontalAlign === 'left'
        ? 'start-start'
        : 'end-start'}
      ?quick=${true}
      .yOffset=${this.verticalOffset}
      @opened=${() => {
        this.isDropdownOpen = true;
      }}
      @closed=${() => {
        this.isDropdownOpen = false;
      }}
      @keydown=${this.handleKeydown}
    >
      ${this.renderCopyLinks()}
    </md-menu> `;
  }

  private renderCopyLinks() {
    return html`<div class="dropdown-content">
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
        ?multiline=${!!multiline}
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

  toggleDropdown(button?: HTMLElement) {
    if (button) {
      this.dropdown!.anchorElement = button;
    }
    this.isDropdownOpen ? this.closeDropdown() : this.openDropdown();
  }

  private closeDropdown() {
    this.dropdown?.close();
  }

  openDropdown(button?: HTMLElement) {
    if (button) {
      this.dropdown!.anchorElement = button;
    }
    this.dropdown?.show();
    this.awaitOpen(() => {
      if (!this.copyClipboardRef?.value) return;
      queryAndAssert<MdOutlinedTextField>(
        this.copyClipboardRef.value,
        'md-outlined-text-field'
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
