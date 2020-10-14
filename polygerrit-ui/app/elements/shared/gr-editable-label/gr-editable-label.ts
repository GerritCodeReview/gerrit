/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/paper-input/paper-input';
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {customElement, property} from '@polymer/decorators';
import {htmlTemplate} from './gr-editable-label_html';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PaperInputElementExt} from '../../../types/types';
import {CustomKeyboardEvent} from '../../../types/events';

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;

declare global {
  interface HTMLElementTagNameMap {
    'gr-editable-label': GrEditableLabel;
  }
}

export interface GrEditableLabel {
  $: {
    input: PaperInputElementExt;
    dropdown: IronDropdownElement;
  };
}

@customElement('gr-editable-label')
export class GrEditableLabel extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the value is changed.
   *
   * @event changed
   */

  @property({type: String})
  labelText?: string;

  @property({type: Boolean})
  editing = false;

  @property({type: String, notify: true, observer: '_updateTitle'})
  value = '';

  @property({type: String})
  placeholder = '';

  @property({type: Boolean})
  readOnly = false;

  @property({type: Boolean, reflectToAttribute: true})
  uppercase = false;

  @property({type: Number})
  maxLength?: number;

  @property({type: String})
  _inputText?: string;

  // This is used to push the iron-input element up on the page, so
  // the input is placed in approximately the same position as the
  // trigger.
  @property({type: Number})
  readonly _verticalOffset = -30;

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('tabindex', '0');
  }

  get keyBindings() {
    return {
      enter: '_handleEnter',
      esc: '_handleEsc',
    };
  }

  _usePlaceholder(value?: string, placeholder?: string) {
    return (!value || !value.length) && placeholder;
  }

  _computeLabel(value?: string, placeholder?: string): string {
    if (this._usePlaceholder(value, placeholder)) {
      return placeholder!;
    }
    return value || '';
  }

  _showDropdown() {
    if (this.readOnly || this.editing) return;
    return this._open().then(() => {
      this._nativeInput.focus();
      if (!this.$.input.value) return;
      this._nativeInput.setSelectionRange(0, this.$.input.value.length);
    });
  }

  open() {
    return this._open().then(() => {
      this._nativeInput.focus();
    });
  }

  _open() {
    this.$.dropdown.open();
    this._inputText = this.value;
    this.editing = true;

    return new Promise(resolve => {
      this._awaitOpen(resolve);
    });
  }

  /**
   * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
   * opening. Eventually replace with a direct way to listen to the overlay.
   */
  _awaitOpen(fn: () => void) {
    let iters = 0;
    const step = () => {
      this.async(() => {
        if (this.$.dropdown.style.display !== 'none') {
          fn.call(this);
        } else if (iters++ < AWAIT_MAX_ITERS) {
          step.call(this);
        }
      }, AWAIT_STEP);
    };
    step.call(this);
  }

  _id() {
    return this.getAttribute('id') || 'global';
  }

  _save() {
    if (!this.editing) {
      return;
    }
    this.$.dropdown.close();
    this.value = this._inputText || '';
    this.editing = false;
    this.dispatchEvent(
      new CustomEvent('changed', {
        detail: this.value,
        composed: true,
        bubbles: true,
      })
    );
  }

  _cancel() {
    if (!this.editing) {
      return;
    }
    this.$.dropdown.close();
    this.editing = false;
    this._inputText = this.value;
  }

  get _nativeInput(): HTMLInputElement {
    // In Polymer 2 inputElement isn't nativeInput anymore
    return (this.$.input.$.nativeInput ||
      this.$.input.inputElement) as HTMLInputElement;
  }

  _handleEnter(e: CustomKeyboardEvent) {
    e = this.getKeyboardEvent(e);
    const target = (dom(e) as EventApi).rootTarget;
    if (target === this._nativeInput) {
      e.preventDefault();
      this._save();
    }
  }

  _handleEsc(e: CustomKeyboardEvent) {
    e = this.getKeyboardEvent(e);
    const target = (dom(e) as EventApi).rootTarget;
    if (target === this._nativeInput) {
      e.preventDefault();
      this._cancel();
    }
  }

  _computeLabelClass(readOnly?: boolean, value?: string, placeholder?: string) {
    const classes = [];
    if (!readOnly) {
      classes.push('editable');
    }
    if (this._usePlaceholder(value, placeholder)) {
      classes.push('placeholder');
    }
    return classes.join(' ');
  }

  _updateTitle(value?: string) {
    this.setAttribute('title', this._computeLabel(value, this.placeholder));
  }
}
