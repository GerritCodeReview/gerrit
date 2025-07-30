/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import '../gr-date-formatter/gr-date-formatter';
import '../gr-file-status/gr-file-status';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {CommentThread, Timestamp} from '../../../types/common';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {GrButton} from '../gr-button/gr-button';
import {assertIsDefined} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {incrementalRepeat} from '../../lit/incremental-repeat';
import {when} from 'lit/directives/when.js';
import {computeTruncatedPath, isMagicPath} from '../../../utils/path-list-util';
import {fireNoBubble} from '../../../utils/event-util';
import {classMap} from 'lit/directives/class-map.js';
import '@material/web/divider/divider';
import '@material/web/menu/menu';
import '@material/web/menu/menu-item';
import {MdMenu} from '@material/web/menu/menu';
import {isSafari, Key} from '../../../utils/dom-util';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';

/**
 * Required values are text and value. mobileText and triggerText will
 * fall back to text if not provided.
 *
 * If bottomText is not provided, nothing will display on the second
 * line.
 *
 * If date is not provided, nothing will be displayed in its place.
 */
export interface DropdownItem {
  text: string;
  value: string | number;
  bottomText?: string;
  triggerText?: string;
  mobileText?: string;
  date?: Timestamp;
  disabled?: boolean;
  file?: NormalizedFileInfo;
  commentThreads?: CommentThread[];
  deemphasizeReason?: string;
}

declare global {
  interface HTMLElementEventMap {
    'value-change': ValueChangedEvent<string>;
  }
}
@customElement('gr-dropdown-list')
export class GrDropdownList extends LitElement {
  @query('#dropdown')
  dropdown?: MdMenu;

  @query('#trigger')
  trigger?: GrButton;

  /**
   * Fired when the selected value changes
   *
   * @event value-change
   *
   * @property {string} value
   */

  @property({type: Number})
  initialCount = 75;

  @property({type: Array})
  items?: DropdownItem[];

  @property({type: String})
  text?: string;

  @property({type: Boolean})
  disabled = false;

  @property({type: String})
  value = '';

  @property({type: Boolean, attribute: 'show-copy-for-trigger-text'})
  showCopyForTriggerText = false;

  @state()
  selectedIndex = 0;

  @state()
  private opened = false;

  @state() private hadKeyboardEvent = false;

  cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.cursorTargetAttribute = 'selected';
    this.cursor.focusOnMove = true;
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        #triggerText {
          -moz-user-select: text;
          -ms-user-select: text;
          -webkit-user-select: text;
          user-select: text;
        }
        .dropdown-trigger {
          cursor: pointer;
          padding: 0;
        }
        md-menu {
          white-space: nowrap;
          --md-menu-container-color: var(--dropdown-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
          --md-focus-ring-duration: 0s;
          max-height: calc(100vh - 48px);
        }
        md-divider {
          margin: auto;
          --md-divider-color: var(--border-color);
        }
        md-menu-item {
          max-height: 70vh;
          min-width: 266px;
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
        md-menu-item[active] .topContent {
          font-weight: bold;
        }
        .dropdown {
          position: relative;
        }
        .bottomContent {
          color: var(--deemphasized-text-color);
          white-space: pre-wrap;
        }
        .bottomContent,
        .topContent {
          display: flex;
          justify-content: space-between;
          flex-direction: row;
          width: 100%;
        }
        gr-button {
          font-family: var(--trigger-style-font-family);
          --gr-button-text-color: var(--trigger-style-text-color);
        }
        gr-date-formatter {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-xxl);
          white-space: nowrap;
        }
        .topContent.deemphasized {
          color: var(--deemphasized-text-color);
          font-style: italic;
        }
        gr-comments-summary {
          padding-left: var(--spacing-s);
        }
        .copyClipboard {
          display: inline-flex;
          vertical-align: top;
        }
        .mobileText {
          display: none;
        }
        .desktopText {
          display: inline-block;
        }
        gr-file-status {
          margin-left: var(--spacing-xxl);
        }
        @media only screen and (max-width: 50em) {
          .mobileText {
            display: inline-block;
          }
          .desktopText {
            display: none;
          }
        }
      `,
    ];
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    if (changedProperties.has('value') || changedProperties.has('items')) {
      this.updateText();
    }
    if (changedProperties.has('value')) {
      fireNoBubble(this, 'value-change', {value: this.value});
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('items')) {
      this.resetCursorStops();
    }

    if (changedProperties.has('opened')) {
      if (this.opened) {
        this.resetCursorStops();
        this.cursor.setCursorAtIndex(this.selectedIndex);
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
    return html`<div class="dropdown">
      <gr-button
        id="trigger"
        ?disabled=${!!this.disabled}
        down-arrow
        link
        class="dropdown-trigger"
        slot="dropdown-trigger"
        @click=${this.showDropdownTapHandler}
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
        <span id="triggerText" class="desktopText">${this.text}</span>
        <span id="triggerText" class="mobileText"
          >${computeTruncatedPath(this.text)}</span
        >
        <gr-copy-clipboard
          class="copyClipboard"
          ?hidden=${!this.showCopyForTriggerText}
          hideInput
          .text=${this.text}
        ></gr-copy-clipboard>
      </gr-button>
      <md-menu
        id="dropdown"
        anchor="trigger"
        default-focus="none"
        tabindex="-1"
        .menuCorner=${'start-start'}
        ?quick=${true}
        .skipRestoreFocus=${true}
        @click=${this.handleDropdownClick}
        @opened=${(e: Event) => {
          this.opened = true;
          this.scrollToSelected(e);
        }}
        @closed=${() => {
          this.opened = false;
          this.hadKeyboardEvent = false;
          // This is an ugly hack but works.
          this.cursor.target?.removeAttribute('selected');
          this.cursor.target?.blur();
        }}
      >
        ${incrementalRepeat({
          values: this.items ?? [],
          initialCount: this.initialCount,
          mapFn: (item, index) =>
            this.renderMdMenuItem(item as DropdownItem, index),
        })}
      </md-menu>
    </div> `;
  }

  private renderMdMenuItem(item: DropdownItem, index: number) {
    if (this.value === String(item.value)) {
      this.selectedIndex = index;
    }
    return html`
      <md-menu-item
        ?selected=${this.value === String(item.value)}
        ?active=${this.value === String(item.value)}
        ?disabled=${!!item.disabled}
        @click=${() => {
          this.value = String(item.value);
        }}
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
        <div
          class=${classMap({
            topContent: true,
            deemphasized: !!item.deemphasizeReason,
          })}
        >
          <div>
            <span class="desktopText">${item.text}</span>
            <span class="mobileText">${this.computeMobileText(item)}</span>
            ${when(
              !!item.deemphasizeReason,
              () => html`<span>| ${item.deemphasizeReason}</span>`
            )}
            ${when(
              item.commentThreads,
              () => html`<gr-comments-summary
                .commentThreads=${item.commentThreads}
                emptyWhenNoComments
                showAvatarForResolved
              ></gr-comments-summary>`
            )}
          </div>
          ${when(
            item.date,
            () => html`
              <gr-date-formatter .dateStr=${item.date}></gr-date-formatter>
            `
          )}
          ${when(
            item.file?.status && !isMagicPath(item.file?.__path),
            () => html`
              <gr-file-status .status=${item.file?.status}></gr-file-status>
            `
          )}
        </div>
        ${when(
          item.bottomText,
          () => html`
            <div class="bottomContent">
              <div>${item.bottomText}</div>
            </div>
          `
        )}
      </md-menu-item>
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
    if (this.cursor.target !== null) {
      const el = this.cursor.target.shadowRoot?.querySelector(':not([hidden])');
      if (el) {
        (el as HTMLElement).click();
      }
    }
  }

  /**
   * Handle a click on the md-menu element.
   */
  private handleDropdownClick(e?: MouseEvent) {
    assertIsDefined(this.dropdown);
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }

    this.dropdown.close();

    // For some reason this is needed, otherwise a console warning is thrown,
    // with something about aria-hidden can't be set because md-menu is focused already.
    e && e.currentTarget && (e.currentTarget as HTMLElement).blur();
  }

  private updateText() {
    if (this.value === undefined || this.items === undefined) {
      return;
    }
    const selectedObj = this.items.find(item => `${item.value}` === this.value);
    if (!selectedObj) {
      return;
    }
    this.text = selectedObj.triggerText
      ? selectedObj.triggerText
      : selectedObj.text;
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  private showDropdownTapHandler(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.open();
  }

  /**
   * Open the dropdown.
   */
  open() {
    assertIsDefined(this.dropdown);
    this.dropdown.open = !this.dropdown.open;
    this.dropdown.focus();
  }

  // Private but used in tests.
  computeMobileText(item: DropdownItem) {
    return item.mobileText ? item.mobileText : item.text;
  }

  private scrollToSelected(e: Event) {
    const target = e.target as HTMLElement;
    const selected = target.querySelector<MdMenu>('md-menu-item[selected]');
    selected?.scrollIntoView({block: 'nearest'});
  }

  /**
   * Recompute the stops for the dropdown item cursor.
   */
  private resetCursorStops() {
    assertIsDefined(this.dropdown);
    if (this.items && this.items.length > 0 && this.dropdown.open) {
      this.cursor.stops = Array.from(
        this.shadowRoot?.querySelectorAll('md-menu-item') ?? []
      );
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-dropdown-list': GrDropdownList;
  }
}
