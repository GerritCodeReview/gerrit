/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '../gr-cursor-manager/gr-cursor-manager';
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-autocomplete-dropdown_html';
import {IronFitMixin} from '../../../mixins/iron-fit-mixin/iron-fit-mixin';
import {customElement, property, observe} from '@polymer/decorators';
import {IronFitBehavior} from '@polymer/iron-fit-behavior/iron-fit-behavior';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {fireEvent} from '../../../utils/event-util';
import {addShortcut, Key} from '../../../utils/dom-util';

export interface GrAutocompleteDropdown {
  $: {
    suggestions: Element;
  };
}

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

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = IronFitMixin(PolymerElement, IronFitBehavior as IronFitBehavior);

/**
 * @attr {String} vertical-align - inherited from IronOverlay
 * @attr {String} horizontal-align - inherited from IronOverlay
 */
@customElement('gr-autocomplete-dropdown')
export class GrAutocompleteDropdown extends base {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Boolean, reflectToAttribute: true})
  isHidden = true;

  @property({type: Number})
  override verticalOffset: number | null = null;

  @property({type: Number})
  override horizontalOffset: number | null = null;

  @property({type: Array})
  suggestions: Item[] = [];

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  // visible for testing
  cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.cursorTargetClass = 'selected';
    this.cursor.focusOnMove = true;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.cleanups.push(
      addShortcut(this, {key: Key.UP}, () => this._handleUp())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.DOWN}, () => this._handleDown())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER}, () => this._handleEnter())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ESC}, () => this._handleEscape())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.TAB}, () => this._handleTab())
    );
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
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

  _handleUp() {
    if (!this.isHidden) this.cursorUp();
  }

  _handleDown() {
    if (!this.isHidden) this.cursorDown();
  }

  cursorDown() {
    if (!this.isHidden) this.cursor.next();
  }

  cursorUp() {
    if (!this.isHidden) this.cursor.previous();
  }

  _handleTab() {
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

  _handleEnter() {
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

  _handleEscape() {
    this._fireClose();
    this.close();
  }

  _handleClickItem(e: Event) {
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

  _fireClose() {
    fireEvent(this, 'dropdown-closed');
  }

  getCursorTarget() {
    return this.cursor.target;
  }

  @observe('suggestions')
  onSuggestionsChanged() {
    if (this.suggestions.length > 0) {
      if (!this.isHidden) {
        flush();
        this.cursor.stops = Array.from(
          this.$.suggestions.querySelectorAll('li')
        );
        this._resetCursorIndex();
      }
    } else {
      this.cursor.stops = [];
    }
    this.refit();
  }

  @observe('index')
  _setIndex() {
    this.cursor.index = this.index || -1;
  }

  _resetCursorIndex() {
    this.cursor.setCursorAtIndex(0);
  }

  _computeLabelClass(item: Item) {
    return item.label ? '' : 'hide';
  }
}
