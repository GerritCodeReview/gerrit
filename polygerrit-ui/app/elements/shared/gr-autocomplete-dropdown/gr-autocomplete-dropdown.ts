/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-cursor-manager/gr-cursor-manager';
import '../../../styles/shared-styles';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {fire} from '../../../utils/event-util';
import {Key} from '../../../utils/dom-util';
// import {FitController} from '../../lit/fit-controller';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {ShortcutController} from '../../lit/shortcut-controller';
import '@material/web/divider/divider';
import '@material/web/menu/menu';
import '@material/web/menu/menu-item';
import {MdMenu} from '@material/web/menu/menu';
import {assertIsDefined} from '../../../utils/common-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-autocomplete-dropdown': GrAutocompleteDropdown;
  }
  interface HTMLElementEventMap {
    'dropdown-closed': CustomEvent<{}>;
  }
}

export interface Item {
  dataValue?: string;
  name?: string;
  text?: string;
  label?: string;
  value?: string;
}

export interface ItemSelectedEventDetail {
  trigger: string;
  selected: HTMLElement | null;
}

export enum AutocompleteQueryStatusType {
  LOADING = 'loading',
  ERROR = 'error',
}

export interface AutocompleteQueryStatus {
  type: AutocompleteQueryStatusType;
  message: string;
}

@customElement('gr-autocomplete-dropdown')
export class GrAutocompleteDropdown extends LitElement {
  @query('#dropdown') dropdown?: MdMenu;

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

  /** If specified a single non-interactable line is shown instead of
   * suggestions.
   */
  @property({type: Object})
  queryStatus?: AutocompleteQueryStatus;

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
  // fitController = new FitController(this);

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
        li.query-status {
          background-color: var(--disabled-background);
          cursor: default;
        }
        li.query-status.error {
          color: var(--error-foreground);
          white-space: pre-wrap;
        }
        .label {
          color: var(--deemphasized-text-color);
          padding-left: var(--spacing-l);
        }
        .hide {
          display: none;
        }
        md-menu {
          white-space: nowrap;
          --md-menu-container-color: var(--dropdown-background-color);
          --md-menu-top-space: 0px;
          --md-menu-bottom-space: 0px;
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
      `,
    ];
  }

  private isSuggestionListInteractible() {
    return !this.isHidden && !this.queryStatus;
  }

  constructor() {
    super();
    this.cursor.cursorTargetClass = 'selected';
    this.cursor.focusOnMove = true;
    this.shortcuts.addLocal({key: Key.UP, allowRepeat: true}, () =>
      this.cursorUp()
    );
    this.shortcuts.addLocal({key: Key.DOWN, allowRepeat: true}, () =>
      this.cursorDown()
    );
    this.shortcuts.addLocal({key: Key.ENTER}, () => this.handleEnter());
    this.shortcuts.addLocal({key: Key.ESC}, () => this.handleEscape());
    this.shortcuts.addLocal({key: Key.TAB}, () => this.handleTab());
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
      changedProperties.has('isHidden') ||
      changedProperties.has('queryStatus')
    ) {
      if (!this.isHidden) {
        this.computeCursorStopsAndRefit();
      }
    }
  }

  private renderStatus() {
    return html`
      <li
        tabindex="-1"
        aria-label="autocomplete query status"
        class="query-status ${this.queryStatus?.type}"
      >
        <span>${this.queryStatus?.message}</span>
        <span class="label"
          >${this.queryStatus?.type === AutocompleteQueryStatusType.ERROR
            ? 'ERROR'
            : ''}</span
        >
      </li>
    `;
  }

  override render() {
    return html`
      <md-menu
        id="dropdown"
        default-focus="none"
        tabindex="-1"
        .menuCorner=${'start-start'}
        .yOffset=${this.verticalOffset}
        ?quick=${true}
        @click=${this.handleDropdownClick}
      >
        ${when(
          this.queryStatus,
          () => this.renderStatus(),
          () => html`
            ${repeat(
              this.suggestions,
              (item, index) => html`
                <md-menu-item
                  ?selected=${index === 0}
                  ?active=${index === 0}
                  data-index=${index}
                  data-value=${item.dataValue ?? ''}
                  @click=${this.handleClickItem}
                  @keydown=${(e: KeyboardEvent) => {
                    if (e.key === Key.ENTER || e.key === Key.SPACE) {
                      e.preventDefault();
                      e.stopPropagation();
                      this.handleEnter();
                    }
                    if (e.key === Key.UP) {
                      e.preventDefault();
                      e.stopPropagation();
                      // this.handleUp();
                    }
                    if (e.key === Key.DOWN) {
                      e.preventDefault();
                      e.stopPropagation();
                      // this.handleDown();
                    }
                  }}
                >
                  <span>${item.text}</span>
                  <span class="label ${this.computeLabelClass(item)}"
                    >${item.label}</span
                  >
                </md-menu-item>
                ${index < this.suggestions.length - 1
                  ? html`<md-divider
                      role="separator"
                      tabindex="-1"
                    ></md-divider>`
                  : nothing}
              `
            )}
          `
        )}
      </md-menu>
    `;
  }

  close() {
    this.isHidden = true;
    this.dropdown?.close();
  }

  open() {
    this.isHidden = false;
    this.dropdown?.show();
  }

  getCurrentText() {
    if (!this.queryStatus) {
      return this.getCursorTarget()?.dataset['value'] || '';
    }
    return '';
  }

  setPositionTarget(target?: HTMLElement) {
    assertIsDefined(this.dropdown);
    if (target) {
      this.dropdown.anchorElement = target;
    }
    // this.fitController.setPositionTarget(target);
  }

  cursorDown() {
    if (this.isSuggestionListInteractible()) this.cursor.next();
  }

  cursorUp() {
    if (this.isSuggestionListInteractible()) this.cursor.previous();
  }

  // private but used in tests
  handleTab() {
    if (this.isSuggestionListInteractible()) {
      fire(this, 'item-selected', {
        trigger: 'tab',
        selected: this.cursor.target,
      });
    }
  }

  // private but used in tests
  handleEnter() {
    if (this.isSuggestionListInteractible()) {
      fire(this, 'item-selected', {
        trigger: 'enter',
        selected: this.cursor.target,
      });
    }
  }

  private handleEscape() {
    this.fireClose();
    this.close();
  }

  private handleClickItem(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    const selected = e.target! as HTMLElement;
    console.log('handleClickItem');
    console.log(selected);
    fire(this, 'item-selected', {
      trigger: 'click',
      selected,
    });
  }

  private fireClose() {
    fire(this, 'dropdown-closed', {});
  }

  getCursorTarget() {
    return this.cursor.target;
  }

  computeCursorStopsAndRefit() {
    console.log('computeCursorStopsAndRefit');
    console.log(this.dropdown?.open);
    if (this.suggestions.length > 0 && this.dropdown?.open) {
      this.cursor.stops = Array.from(
        this.shadowRoot?.querySelectorAll('md-menu-item') ?? []
      );
      this.resetCursorIndex();
    }
    // this.fitController.refit();
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

  private handleDropdownClick() {
    assertIsDefined(this.dropdown);
    this.dropdown.close();
  }
}
