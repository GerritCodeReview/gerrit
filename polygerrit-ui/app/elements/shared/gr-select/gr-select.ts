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
import {customElement, property, queryAssignedElements} from 'lit/decorators';
import {html, LitElement, PropertyValues} from 'lit';

declare global {
  interface HTMLElementTagNameMap {
    'gr-select': GrSelect;
  }
}

/**
 * GrSelect `gr-select` component. Must be given a <select> child.
 */
@customElement('gr-select')
export class GrSelect extends LitElement {
  /**
   * @event bind-value-changed
   */

  @property({type: String})
  bindValue?: string | number | boolean;

  @queryAssignedElements({selector: 'select'})
  private nativeSelectsInSlot?: HTMLSelectElement[];

  get nativeSelect(): HTMLSelectElement | undefined {
    return this.nativeSelectsInSlot?.[0];
  }

  constructor() {
    super();
    this.addEventListener('change', () => this.valueChanged());
    this.addEventListener('dom-change', () => this.updateValue());
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('bindValue')) {
      this.updateValue();
    }
  }

  override render() {
    return html`<slot></slot>`;
  }

  override firstUpdated() {
    if (!this.nativeSelect || this.nativeSelect.options.length === 0) {
      return;
    }
    // If not set via the property, set bind-value to the element value.
    if (this.bindValue === undefined) {
      this.bindValue = this.convertToBindValueType(this.nativeSelect.value);
    } else {
      // once we render we can set the default. This can't happen earlier
      // because the native select + options are owned by the parent.
      this.updateValue();
    }
  }

  override focus() {
    this.nativeSelect?.focus();
  }

  private updateValue() {
    // It's possible to have a value of 0.
    if (this.bindValue !== undefined && this.nativeSelect) {
      // Set for chrome/safari so it happens instantly
      this.nativeSelect.value = String(this.bindValue);
      this.valueChanged();
      // Async needed for firefox to populate value. It was trying to do it
      // before options from a dom-repeat were rendered previously.
      // See https://bugs.chromium.org/p/gerrit/issues/detail?id=7735
      setTimeout(() => {
        if (this.nativeSelect) {
          this.nativeSelect.value = String(this.bindValue);
        }
      }, 1);
    }
  }

  // HTMLOptionElement.value is typed string, but we want to accept and return
  // other primitives as convenience for callers.
  private convertToBindValueType(value?: string) {
    if (value === undefined) {
      return undefined;
    } else if (typeof this.bindValue === 'boolean') {
      // Boolean('false') === true, so can't use that
      return value === 'true';
    } else if (typeof this.bindValue === 'number') {
      return Number(value);
    }
    // string case
    return value;
  }

  private valueChanged() {
    this.bindValue = this.convertToBindValueType(this.nativeSelect?.value);

    // Relay the event.
    this.dispatchEvent(
      new CustomEvent('bind-value-changed', {
        detail: {value: this.bindValue},
        composed: true,
        bubbles: true,
      })
    );
  }
}
