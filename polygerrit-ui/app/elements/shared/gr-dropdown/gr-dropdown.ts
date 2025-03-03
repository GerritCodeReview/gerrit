/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '../gr-button/gr-button';
import {GrButton} from '../gr-button/gr-button';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import {getBaseUrl} from '../../../utils/url-util';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {property, customElement, query, state} from 'lit/decorators.js';
import {Key} from '../../../utils/dom-util';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined.js';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {assertIsDefined} from '../../../utils/common-util';
import {ShortcutController} from '../../lit/shortcut-controller';
import {DropdownLink} from '../../../types/common';

const REL_NOOPENER = 'noopener';
const REL_EXTERNAL = 'external';

declare global {
  interface HTMLElementEventMap {
    'opened-changed': ValueChangedEvent<boolean>;
  }
  interface HTMLElementTagNameMap {
    'gr-dropdown': GrDropdown;
  }
}

export interface DropdownContent {
  text: string;
  bold?: boolean;
}

@customElement('gr-dropdown')
export class GrDropdown extends LitElement {
  @query('#dropdown')
  dropdown?: IronDropdownElement;

  @query('#trigger')
  trigger?: GrButton;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        .dropdown-trigger {
          text-decoration: none;
          width: 100%;
        }
        .dropdown-content {
          background-color: var(--dropdown-background-color);
          box-shadow: var(--elevation-level-2);
          min-width: 112px;
          max-width: 280px;
        }
        gr-button {
          vertical-align: top;
        }
        gr-avatar {
          height: 2em;
          width: 2em;
          vertical-align: middle;
        }
        gr-button[link]:focus {
          outline: 5px auto -webkit-focus-ring-color;
        }
        ul {
          list-style: none;
        }
        .topContent,
        li {
          border-bottom: 1px solid var(--border-color);
        }
        li:last-of-type {
          border: none;
        }
        li .itemAction {
          cursor: pointer;
          display: block;
          padding: var(--spacing-m) var(--spacing-l);
        }
        li .itemAction {
          color: var(--gr-dropdown-item-color);
          background-color: var(--gr-dropdown-item-background-color);
          border: var(--gr-dropdown-item-border);
          text-transform: var(--gr-dropdown-item-text-transform);
        }
        li .itemAction.disabled {
          color: var(--deemphasized-text-color);
          cursor: default;
        }
        li .itemAction:link,
        li .itemAction:visited {
          text-decoration: none;
        }
        li .itemAction:not(.disabled):hover {
          background-color: var(--hover-background-color);
        }
        li:focus,
        li.selected {
          background-color: var(--selection-background-color);
          outline: none;
        }
        li:focus .itemAction,
        li.selected .itemAction {
          background-color: transparent;
        }
        .topContent {
          display: block;
          padding: var(--spacing-m) var(--spacing-l);
          color: var(--gr-dropdown-item-color);
          background-color: var(--gr-dropdown-item-background-color);
          border: var(--gr-dropdown-item-border);
          text-transform: var(--gr-dropdown-item-text-transform);
        }
        .bold-text {
          font-weight: var(--font-weight-medium);
        }
      `,
    ];
  }
  /**
   * Fired when a non-link dropdown item with the given ID is tapped.
   *
   * @event tap-item-<id>
   */

  /**
   * Fired when a non-link dropdown item is tapped.
   *
   * @event tap-item
   */

  @property({type: Array})
  items?: DropdownLink[];

  @property({type: Boolean, attribute: 'down-arrow'})
  downArrow = false;

  @property({type: Array})
  topContent?: DropdownContent[];

  @property({type: String, attribute: 'horizontal-align'})
  horizontalAlign = 'left';

  /**
   * Style the dropdown trigger as a link (rather than a button).
   */

  @property({type: Boolean})
  link = false;

  @property({type: Number, attribute: 'vertical-offset'})
  verticalOffset = 40;

  @state()
  private opened = false;

  /**
   * List the IDs of dropdown buttons to be disabled. (Note this only
   * disables buttons and not link entries.)
   */
  @property({type: Array})
  disabledIds: string[] = [];

  // Used within the tests so needs to be non-private.
  cursor = new GrCursorManager();

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    this.cursor.cursorTargetClass = 'selected';
    this.cursor.focusOnMove = true;
    this.shortcuts.addLocal({key: Key.UP}, () => this.handleUp());
    this.shortcuts.addLocal({key: Key.DOWN}, () => this.handleDown());
    this.shortcuts.addLocal({key: Key.ENTER}, () => this.handleEnter());
    this.shortcuts.addLocal({key: Key.SPACE}, () => this.handleEnter());
  }

  override connectedCallback() {
    super.connectedCallback();
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('opened')) {
      fire(this, 'opened-changed', {value: this.opened});
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('items')) {
      this.resetCursorStops();
    }
    if (changedProperties.has('opened') && this.opened) {
      this.resetCursorStops();
      this.cursor.setCursorAtIndex(0);
      if (this.cursor.target !== null) this.cursor.target.focus();
    }
  }

  override render() {
    return html` <gr-button
        ?link=${this.link}
        class="dropdown-trigger"
        id="trigger"
        ?down-arrow=${this.downArrow}
        @click=${this.dropdownTriggerTapHandler}
      >
        <slot></slot>
      </gr-button>
      <iron-dropdown
        id="dropdown"
        .verticalAlign=${'top'}
        .verticalOffset=${this.verticalOffset}
        allowOutsideScroll
        .horizontalAlign=${this.horizontalAlign}
        @click=${() => this.close()}
        @opened-changed=${(e: ValueChangedEvent<boolean>) =>
          (this.opened = e.detail.value)}
      >
        ${this.renderDropdownContent()}
      </iron-dropdown>`;
  }

  private renderDropdownContent() {
    return html` <div class="dropdown-content" slot="dropdown-content">
      <ul>
        ${this.renderTopContent()}
        ${(this.items ?? []).map(link => this.renderDropdownLink(link))}
      </ul>
    </div>`;
  }

  private renderTopContent() {
    if (!this.topContent) return nothing;
    return html`
      <div class="topContent">
        ${(this.topContent ?? []).map(item => this.renderTopContentItem(item))}
      </div>
    `;
  }

  private renderTopContentItem(item: DropdownContent) {
    return html`
      <div class="${this.getClassIfBold(item.bold)} top-item" tabindex="-1">
        ${item.text}
      </div>
    `;
  }

  private renderDropdownLink(link: DropdownLink) {
    const disabledClass = this.computeDisabledClass(link.id);
    return html`
      <li tabindex="-1">
        <gr-tooltip-content
          ?has-tooltip=${!!link.tooltip}
          title=${ifDefined(link.tooltip)}
        >
          <span
            class="itemAction ${disabledClass}"
            data-id=${ifDefined(link.id)}
            @click=${this.handleItemTap}
            ?hidden=${!!link.url}
            tabindex="-1"
            >${link.name}</span
          >
          <a
            class="itemAction"
            href=${this.computeLinkURL(link)}
            ?download=${!!link.download}
            rel=${ifDefined(this.computeLinkRel(link) ?? undefined)}
            target=${ifDefined(link.target ?? undefined)}
            ?hidden=${!link.url}
            tabindex="-1"
            >${link.name}</a
          >
        </gr-tooltip-content>
      </li>
    `;
  }

  /**
   * Handle the up key.
   */
  private handleUp() {
    assertIsDefined(this.dropdown);
    if (this.dropdown.opened) {
      this.cursor.previous();
    } else {
      this.open();
    }
  }

  /**
   * Handle the down key.
   */
  private handleDown() {
    assertIsDefined(this.dropdown);
    if (this.dropdown.opened) {
      this.cursor.next();
    } else {
      this.open();
    }
  }

  /**
   * Handle the enter key.
   */
  private handleEnter() {
    assertIsDefined(this.dropdown);
    if (this.dropdown.opened) {
      // Since gr-tooltip-content click on shadow dom is not propagated down,
      // we have to target `a` inside it.
      if (this.cursor.target !== null) {
        const el = this.cursor.target.querySelector(':not([hidden]) a');
        if (el) {
          (el as HTMLElement).click();
        }
      }
    } else {
      this.open();
    }
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  private dropdownTriggerTapHandler(e: MouseEvent) {
    assertIsDefined(this.dropdown);
    e.preventDefault();
    e.stopPropagation();
    if (this.dropdown.opened) {
      this.close();
    } else {
      this.open();
    }
  }

  /**
   * Open the dropdown and initialize the cursor.
   * Private but used in tests.
   */
  open() {
    assertIsDefined(this.dropdown);
    this.dropdown.open();
  }

  // Private but used in tests.
  close() {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    setTimeout(() => {
      this.dropdown?.close();
    }, 1);
  }

  /**
   * Get the class for a top-content item based on the given boolean.
   *
   * @param bold Whether the item is bold.
   * @return The class for the top-content item.
   *
   * Private but used in tests.
   */
  getClassIfBold(bold?: boolean) {
    return bold ? 'bold-text' : '';
  }

  /**
   * Build a URL for the given host and path. The base URL will be only added,
   * if it is not already included in the path.
   *
   * @return The scheme-relative URL.
   */
  _computeURLHelper(host: string, path: string) {
    const base = path.startsWith(getBaseUrl()) ? '' : getBaseUrl();
    return '//' + host + base + path;
  }

  /**
   * Build a scheme-relative URL for the current host. Will include the base
   * URL if one is present. Note: the URL will be scheme-relative but absolute
   * with regard to the host.
   *
   * @param path The path for the URL.
   * @return The scheme-relative URL.
   */
  private computeRelativeURL(path: string) {
    const host = window.location.host;
    return this._computeURLHelper(host, path);
  }

  /**
   * Compute the URL for a link object.
   *
   * Private but used in tests.
   */
  computeLinkURL(link: DropdownLink) {
    if (typeof link.url === 'undefined') {
      return '';
    }
    if (link.target || !link.url.startsWith('/')) {
      return link.url;
    }
    return this.computeRelativeURL(link.url);
  }

  /**
   * Compute the value for the rel attribute of an anchor for the given link
   * object. If the link has a target value, then the rel must be "noopener"
   * for security reasons.
   * Private but used in tests.
   */
  computeLinkRel(link: DropdownLink) {
    // Note: noopener takes precedence over external.
    if (link.target) {
      return REL_NOOPENER;
    }
    if (link.external) {
      return REL_EXTERNAL;
    }
    return null;
  }

  /**
   * Handle a click on an item of the dropdown.
   */
  private handleItemTap(e: MouseEvent) {
    if (e.target === null || !this.items) {
      return;
    }
    const id = (e.target as Element).getAttribute('data-id');
    const item = this.items.find(item => item.id === id);
    if (id && !this.disabledIds.includes(id)) {
      if (item) {
        fire(this, 'tap-item', item);
      }
      this.dispatchEvent(new CustomEvent('tap-item-' + id));
    }
  }

  /**
   * Recompute the stops for the dropdown item cursor.
   */
  private resetCursorStops() {
    assertIsDefined(this.dropdown);
    if (this.items && this.items.length > 0 && this.dropdown?.opened) {
      this.cursor.stops = Array.from(
        this.shadowRoot?.querySelectorAll('li') ?? []
      );
    }
  }

  /**
   * If a dropdown item is shown as a button, get the class for the button.
   *
   * @return The class for the item button.
   */
  private computeDisabledClass(id?: string) {
    return id && this.disabledIds.includes(id) ? 'disabled' : '';
  }
}
