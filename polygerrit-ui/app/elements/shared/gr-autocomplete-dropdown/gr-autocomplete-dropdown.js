/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '@polymer/iron-dropdown/iron-dropdown.js';
import '../gr-cursor-manager/gr-cursor-manager.js';
import '../../../styles/shared-styles.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-autocomplete-dropdown_html.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {IronFitMixin} from '../../../mixins/iron-fit-mixin/iron-fit-mixin.js';

/**
 * @extends PolymerElement
 */
class GrAutocompleteDropdown extends IronFitMixin(KeyboardShortcutMixin(
    GestureEventListeners(LegacyElementMixin(PolymerElement)))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-autocomplete-dropdown'; }
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

  static get properties() {
    return {
      index: Number,
      isHidden: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
      verticalOffset: {
        type: Number,
        value: null,
      },
      horizontalOffset: {
        type: Number,
        value: null,
      },
      suggestions: {
        type: Array,
        value: () => [],
        observer: '_resetCursorStops',
      },
      _suggestionEls: Array,
    };
  }

  get keyBindings() {
    return {
      up: '_handleUp',
      down: '_handleDown',
      enter: '_handleEnter',
      esc: '_handleEscape',
      tab: '_handleTab',
    };
  }

  close() {
    this.isHidden = true;
  }

  open() {
    this.isHidden = false;
    this._resetCursorStops();
    // Refit should run after we call Polymer.flush inside _resetCursorStops
    this.refit();
  }

  getCurrentText() {
    return this.getCursorTarget().dataset.value;
  }

  _handleUp(e) {
    if (!this.isHidden) {
      e.preventDefault();
      e.stopPropagation();
      this.cursorUp();
    }
  }

  _handleDown(e) {
    if (!this.isHidden) {
      e.preventDefault();
      e.stopPropagation();
      this.cursorDown();
    }
  }

  cursorDown() {
    if (!this.isHidden) {
      this.$.cursor.next();
    }
  }

  cursorUp() {
    if (!this.isHidden) {
      this.$.cursor.previous();
    }
  }

  _handleTab(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('item-selected', {
      detail: {
        trigger: 'tab',
        selected: this.$.cursor.target,
      },
      composed: true, bubbles: true,
    }));
  }

  _handleEnter(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('item-selected', {
      detail: {
        trigger: 'enter',
        selected: this.$.cursor.target,
      },
      composed: true, bubbles: true,
    }));
  }

  _handleEscape() {
    this._fireClose();
    this.close();
  }

  _handleClickItem(e) {
    e.preventDefault();
    e.stopPropagation();
    let selected = e.target;
    while (!selected.classList.contains('autocompleteOption')) {
      if (!selected || selected === this) { return; }
      selected = selected.parentElement;
    }
    this.dispatchEvent(new CustomEvent('item-selected', {
      detail: {
        trigger: 'click',
        selected,
      },
      composed: true, bubbles: true,
    }));
  }

  _fireClose() {
    this.dispatchEvent(new CustomEvent('dropdown-closed', {
      composed: true, bubbles: true,
    }));
  }

  getCursorTarget() {
    return this.$.cursor.target;
  }

  _resetCursorStops() {
    if (this.suggestions.length > 0) {
      if (!this.isHidden) {
        flush();
        this._suggestionEls = Array.from(
            this.$.suggestions.querySelectorAll('li'));
        this._resetCursorIndex();
      }
    } else {
      this._suggestionEls = [];
    }
  }

  _resetCursorIndex() {
    this.$.cursor.setCursorAtIndex(0);
  }

  _computeLabelClass(item) {
    return item.label ? '' : 'hide';
  }
}

customElements.define(GrAutocompleteDropdown.is, GrAutocompleteDropdown);
