/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-cursor-manager/gr-cursor-manager';
import '../../../styles/shared-styles';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {fireEvent} from '../../../utils/event-util';
import {Key} from '../../../utils/dom-util';
import {FitController} from '../../lit/fit-controller';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {ShortcutController} from '../../lit/shortcut-controller';

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
  verticalOffset = 0;

  @property({type: Number})
  horizontalOffset = 0;

  @property({type: Array})
  suggestions: Item[] = [];

  @query('#suggestions') suggestionsDiv?: HTMLDivElement;

  private readonly shortcuts = new ShortcutController(this);

  // visible for testing
  cursor = new GrCursorManager();

  // visible for testing
  fitController = new FitController(this);

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          z-index: 100;
          box-shadow: var(--elevation-level-2);
          overflow: auto;
          background: var(--dropdown-background-color);
          border-radius: var(--border-radius);
          max-height: 50vh;
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
    this.shortcuts.addLocal({key: Key.UP, allowRepeat: true}, () =>
      this.handleUp()
    );
    this.shortcuts.addLocal({key: Key.DOWN, allowRepeat: true}, () =>
      this.handleDown()
    );
    this.shortcuts.addLocal({key: Key.ENTER}, () => this.handleEnter());
    this.shortcuts.addLocal({key: Key.ESC}, () => this.handleEscape());
    this.shortcuts.addLocal({key: Key.TAB}, () => this.handleTab());
  }

  override connectedCallback() {
    super.connectedCallback();
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('index')) {
      this.setIndex();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (
      changedProperties.has('suggestions') ||
      changedProperties.has('isHidden')
    ) {
      if (!this.isHidden) {
        this.computeCursorStopsAndRefit();
      }
    }
  }

  override render() {
    return html`
      <div class="dropdown-content" id="suggestions" role="listbox">
        <ul>
          ${repeat(
            this.suggestions,
            (item, index) => html`
              <li
                data-index=${index}
                data-value=${item.dataValue ?? ''}
                tabindex="-1"
                aria-label=${item.name ?? ''}
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
  }

  getCurrentText() {
    return this.getCursorTarget()?.dataset['value'] || '';
  }

  setPositionTarget(target: HTMLElement) {
    this.fitController.setPositionTarget(target);
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

  computeCursorStopsAndRefit() {
    if (this.suggestions.length > 0) {
      this.cursor.stops = Array.from(
        this.suggestionsDiv?.querySelectorAll('li') ?? []
      );
      this.resetCursorIndex();
    } else {
      this.cursor.stops = [];
    }
    this.fitController.refit();
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
