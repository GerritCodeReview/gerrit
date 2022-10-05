/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {fireEvent} from '../../../utils/event-util';
import {addShortcut, Key} from '../../../utils/dom-util';
import {FitController} from '../../lit/fit-controller';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {queryAndAssert} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-autocomplete-dropdown': GrAutocompleteDropdown;
  }
}

export interface Item {
  dataValue?: string;
  name?: string;
  text?: string;
  label?: string;
  value?: string;
}

export interface ItemSelectedEvent {
  trigger: string;
  selected: HTMLElement | null;
}

/**
 * @attr {String} vertical-align - inherited from IronOverlay
 * @attr {String} horizontal-align - inherited from IronOverlay
 */
@customElement('gr-autocomplete-dropdown')
export class GrAutocompleteDropdown extends LitElement {
  /**
   * Fired when the dropdown is closed.
   *
   * @event dropdown-closed
   */

  /**
   * Fired when item is selected.
   *
   * @event item-selected
   */

  @property({type: Number})
  index: number | null = null;

  @property({type: Boolean, reflect: true, attribute: 'is-hidden'})
  isHidden = true;

  @property({type: Number})
  verticalOffset: number | undefined = undefined;

  @property({type: String, attribute: 'vertical-align'})
  verticalAlign?: string;

  @property({type: String, attribute: 'horizontal-align'})
  horizontalAlign?: string;

  @property({type: Number})
  horizontalOffset: number | undefined = undefined;

  @property({type: Array})
  suggestions: Item[] = [];

  @property({type: Object})
  positionTarget?: HTMLElement;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  // visible for testing
  cursor = new GrCursorManager();

  private fitController?: FitController;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          z-index: 100;
        }
        :host([is-hidden]) {
          display: none;
        }
        ul {
          list-style: none;
        }
        li {
          border-bottom: 1px solid var(--border-color);
          cursor: pointer;
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-m) var(--spacing-l);
        }
        li:last-of-type {
          border: none;
        }
        li:focus {
          outline: none;
        }
        li:hover {
          background-color: var(--hover-background-color);
        }
        li.selected {
          background-color: var(--hover-background-color);
        }
        .dropdown-content {
          background: var(--dropdown-background-color);
          box-shadow: var(--elevation-level-2);
          border-radius: var(--border-radius);
          max-height: 50vh;
          overflow: auto;
        }
        @media only screen and (max-height: 35em) {
          .dropdown-content {
            max-height: 80vh;
          }
        }
        .label {
          color: var(--deemphasized-text-color);
          padding-left: var(--spacing-l);
        }
        .hide {
          display: none;
        }
      `,
    ];
  }

  constructor() {
    super();
    this.cursor.cursorTargetClass = 'selected';
    this.cursor.focusOnMove = true;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.fitController = new FitController(
      this,
      this.horizontalOffset,
      this.verticalOffset,
      this.horizontalAlign,
      this.verticalAlign,
      this.positionTarget
    );
    this.cleanups.push(addShortcut(this, {key: Key.UP}, () => this.handleUp()));
    this.cleanups.push(
      addShortcut(this, {key: Key.DOWN}, () => this.handleDown())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER}, () => this.handleEnter())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ESC}, () => this.handleEscape())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.TAB}, () => this.handleTab())
    );
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('index')) {
      this.setIndex();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('suggestions')) {
      this.onSuggestionsChanged();
    }
  }

  override render() {
    return html`
      <div
        class="dropdown-content"
        slot="dropdown-content"
        id="suggestions"
        role="listbox"
      >
        <ul>
          ${repeat(
            this.suggestions,
            (item, index) => html`
              <li
                data-index=${index}
                data-value=${item.dataValue}
                tabindex="-1"
                aria-label=${item.name}
                class="autocompleteOption"
                role="option"
                @click=${this.handleClickItem}
              >
                <span>${item.text}</span>
                <span class="label ${this.computeLabelClass(item)}"
                  >${item.label}</span
                >
              </li>
            `
          )}
        </ul>
      </div>
    `;
  }

  close() {
    this.isHidden = true;
  }

  open() {
    this.isHidden = false;
    this.onSuggestionsChanged();
  }

  getCurrentText() {
    return this.getCursorTarget()?.dataset['value'] || '';
  }

  setPositionTarget(target: HTMLElement) {
    this.fitController?.setPositionTarget(target);
  }

  private handleUp() {
    if (!this.isHidden) this.cursorUp();
  }

  private handleDown() {
    if (!this.isHidden) this.cursorDown();
  }

  cursorDown() {
    if (!this.isHidden) this.cursor.next();
  }

  cursorUp() {
    if (!this.isHidden) this.cursor.previous();
  }

  // private but used in tests
  handleTab() {
    this.dispatchEvent(
      new CustomEvent<ItemSelectedEvent>('item-selected', {
        detail: {
          trigger: 'tab',
          selected: this.cursor.target,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  // private but used in tests
  handleEnter() {
    this.dispatchEvent(
      new CustomEvent<ItemSelectedEvent>('item-selected', {
        detail: {
          trigger: 'enter',
          selected: this.cursor.target,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  private handleEscape() {
    this.fireClose();
    this.close();
  }

  private handleClickItem(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    let selected = e.target! as HTMLElement;
    while (!selected.classList.contains('autocompleteOption')) {
      if (!selected || selected === this) {
        return;
      }
      selected = selected.parentElement!;
    }
    this.dispatchEvent(
      new CustomEvent<ItemSelectedEvent>('item-selected', {
        detail: {
          trigger: 'click',
          selected,
        },
        composed: true,
        bubbles: true,
      })
    );
  }

  private fireClose() {
    fireEvent(this, 'dropdown-closed');
  }

  getCursorTarget() {
    return this.cursor.target;
  }

  async onSuggestionsChanged() {
    if (this.suggestions.length > 0) {
      if (!this.isHidden) {
        flush();
        this.cursor.stops = Array.from(
          queryAndAssert(this, '#suggestions').querySelectorAll('li')
        );
        this.resetCursorIndex();
      }
    } else {
      this.cursor.stops = [];
    }
    await this.updateComplete;
    this.fitController?.refit();
  }

  private setIndex() {
    this.cursor.index = this.index || -1;
  }

  private resetCursorIndex() {
    this.cursor.setCursorAtIndex(0);
  }

  private computeLabelClass(item: Item) {
    return item.label ? '' : 'hide';
  }
}
