/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-button/gr-button';
import {GrButton} from '../gr-button/gr-button';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import {getBaseUrl} from '../../../utils/url-util';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {customElement, property, query, state} from 'lit/decorators.js';
import {isSafari, Key} from '../../../utils/dom-util';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined.js';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {assertIsDefined} from '../../../utils/common-util';
import {DropdownLink} from '../../../types/common';
import '@material/web/divider/divider';
import '@material/web/menu/menu';
import '@material/web/menu/menu-item';
import {MdMenu} from '@material/web/menu/menu';

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
  dropdown?: MdMenu;

  @query('#trigger')
  trigger?: GrButton;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        .container {
          position: relative;
        }
        .dropdown-trigger {
          text-decoration: none;
          width: 100%;
        }
        .dropdown-content {
          min-width: 112px;
          max-width: 280px;
        }
        md-menu {
          white-space: nowrap;
          --md-menu-container-color: var(--dropdown-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
          --md-focus-ring-duration: 0s;
          max-height: calc(100vh - 48px);
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
        md-divider {
          margin: auto;
          --md-divider-color: var(--border-color);
        }
        md-menu-item {
          --md-sys-color-on-surface: var(
            --gr-dropdown-item-color,
            var(--primary-text-color, black)
          );
          --md-sys-color-on-secondary-container: var(
            --gr-dropdown-item-color,
            var(--primary-text-color, black)
          );
          --md-sys-typescale-body-large-font: inherit;
          --md-menu-item-hover-state-layer-color: var(
            --selection-background-color
          );
          --md-menu-item-hover-state-layer-opacity: 1;
          --md-menu-item-selected-container-color: var(
            --selection-background-color
          );
          --md-focus-ring-color: var(--gr-dropdown-focus-ring-color);
          --md-menu-item-one-line-container-height: auto;
        }
        .itemAction:link,
        .itemAction:visited {
          text-decoration: none;
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
  verticalOffset = 0;

  @state()
  private opened = false;

  /**
   * List the IDs of dropdown buttons to be disabled. (Note this only
   * disables buttons and not link entries.)
   */
  @property({type: Array})
  disabledIds: string[] = [];

  @state() private hadKeyboardEvent = false;

  // Used within the tests so needs to be non-private.
  cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.cursorTargetAttribute = 'selected';
    this.cursor.focusOnMove = true;
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

    if (changedProperties.has('opened')) {
      if (this.opened) {
        this.resetCursorStops();
        this.cursor.setCursorAtIndex(0);
        if (this.cursor.target !== null) {
          this.cursor.target.focus();
          if (isSafari() && !this.hadKeyboardEvent) {
            const mdFocusRing = this.cursor.target?.shadowRoot
              ?.querySelector('md-item')
              ?.querySelector('md-focus-ring');
            if (mdFocusRing) mdFocusRing.visible = false;
          }
        }
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
    return html`<div class="container">
      <gr-button
        id="trigger"
        ?link=${this.link}
        class="dropdown-trigger"
        ?down-arrow=${this.downArrow}
        @click=${this.dropdownTriggerTapHandler}
        @keydown=${(e: KeyboardEvent) => {
          this.hadKeyboardEvent = true;
          if (
            (e.key === Key.DOWN || e.key === Key.UP) &&
            !this.dropdown?.open
          ) {
            e.preventDefault();
            e.stopPropagation();
            this.dropdown?.show();
          }
        }}
        @mousedown=${() => {
          this.hadKeyboardEvent = false;
        }}
        @pointerdown=${() => {
          this.hadKeyboardEvent = false;
        }}
        @touchstart=${() => {
          this.hadKeyboardEvent = false;
        }}
      >
        <slot></slot>
      </gr-button>
      <md-menu
        default-focus="none"
        id="dropdown"
        anchor="trigger"
        tabindex="-1"
        .menuCorner=${this.horizontalAlign === 'left'
          ? 'start-start'
          : this.horizontalAlign === 'center'
          ? 'start-end'
          : 'end-start'}
        .yOffset=${this.verticalOffset}
        ?quick=${true}
        .skipRestoreFocus=${true}
        @opened=${() => {
          this.opened = true;
        }}
        @closing=${this.handleMenuClosing}
        @closed=${() => {
          this.opened = false;
          this.hadKeyboardEvent = false;
          // This is an ugly hack but works.
          this.cursor.target?.removeAttribute('selected');
        }}
      >
        ${this.renderDropdownContent()}
      </md-menu>
    </div>`;
  }

  private renderDropdownContent() {
    return html`
      <div class="dropdown-content">
        ${this.renderTopContent()}
        ${(this.items ?? []).map((link, index) =>
          this.renderDropdownLink(link, index)
        )}
      </div>
    `;
  }

  private renderTopContent() {
    if (!this.topContent) return nothing;
    return html`
      <div class="topContent">
        ${(this.topContent ?? []).map(item => this.renderTopContentItem(item))}
      </div>
      <md-divider role="separator" tabindex="-1"></md-divider>
    `;
  }

  private renderTopContentItem(item: DropdownContent) {
    return html`
      <div class="${this.getClassIfBold(item.bold)} top-item" tabindex="-1">
        ${item.text}
      </div>
    `;
  }

  private renderDropdownLink(link: DropdownLink, index: number) {
    const itemContent = html`
      <md-menu-item
        data-index=${index}
        ?selected=${index === 0}
        ?active=${index === 0}
        ?disabled=${!!link.id && this.disabledIds.includes(link.id)}
        @keydown=${(e: KeyboardEvent) => {
          if (e.key === Key.ENTER || e.key === Key.SPACE) {
            e.preventDefault();
            e.stopPropagation();
            this.handleEnter();
          }
          if (e.key === Key.UP) {
            e.preventDefault();
            e.stopPropagation();
            this.handleUp();
          }
          if (e.key === Key.DOWN) {
            e.preventDefault();
            e.stopPropagation();
            this.handleDown();
          }
        }}
      >
        ${link.name}
      </md-menu-item>
    `;

    const linkWrapper = link.url
      ? html`
          <a
            class="itemAction"
            href=${this.computeLinkURL(link)}
            ?download=${!!link.download}
            rel=${ifDefined(this.computeLinkRel(link) ?? undefined)}
            target=${ifDefined(link.target ?? undefined)}
            ?hidden=${!link.url}
            tabindex="-1"
          >
            ${itemContent}
          </a>
        `
      : html`<span
          class="itemAction"
          data-id=${ifDefined(link.id)}
          @click=${this.handleItemTap}
          ?hidden=${!!link.url}
          tabindex="-1"
          >${itemContent}</span
        >`;
    return html`
      <gr-tooltip-content
        ?has-tooltip=${!!link.tooltip}
        title=${ifDefined(link.tooltip)}
      >
        ${linkWrapper}
      </gr-tooltip-content>
      ${index < this.items!.length - 1
        ? html`<md-divider role="separator" tabindex="-1"></md-divider>`
        : nothing}
    `;
  }

  /**
   * Handle the up key.
   */
  private handleUp() {
    this.cursor.previous();
  }

  /**
   * Handle the down key.
   */
  private handleDown() {
    this.cursor.next();
  }

  /**
   * Handle the enter key.
   */
  private handleEnter() {
    // Since gr-tooltip-content click on shadow dom is not propagated down,
    // we have to target `a` inside it.
    if (this.cursor.target !== null) {
      const el = this.cursor.target.shadowRoot?.querySelector(':not([hidden])');
      if (el) {
        (el as HTMLElement).click();
      }
    }
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  dropdownTriggerTapHandler() {
    assertIsDefined(this.dropdown);

    this.dropdown.open = !this.dropdown.open;
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
   * Private but used in tests.
   *
   * @return The scheme-relative URL.
   */
  computeURLHelper(host: string, path: string) {
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
    return this.computeURLHelper(host, path);
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
    if (!link.url.startsWith('/')) {
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
    if (e.currentTarget === null || !this.items) {
      return;
    }

    const target = e.currentTarget as HTMLElement;
    const id = target.getAttribute('data-id');
    const item = this.items.find(item => item.id === id);
    if (id && !this.disabledIds.includes(id)) {
      if (item) {
        fire(this, 'tap-item', item);
      }
      this.dispatchEvent(new CustomEvent('tap-item-' + id));
    }

    // For some reason this is needed, otherwise a console warning is thrown,
    // with something about aria-hidden can't be set because md-menu is focused already.
    target.blur();
  }

  /**
   * Recompute the stops for the dropdown item cursor.
   */
  resetCursorStops() {
    assertIsDefined(this.dropdown);
    if (this.items && this.items.length > 0 && this.dropdown.open) {
      this.cursor.stops = Array.from(
        this.shadowRoot?.querySelectorAll('md-menu-item') ?? []
      );
    }
  }

  private handleMenuClosing() {
    // Blur focused item before aria-hidden="true" is applied
    // Fixes console warning about aria-hidden can't be set because
    // md-menu is focused already.
    const active = (this.shadowRoot?.activeElement ??
      document.activeElement) as HTMLElement;
    if (active && this.dropdown?.contains(active)) {
      active.blur();
    }
  }
}
