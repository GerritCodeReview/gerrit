/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement, PropertyValues} from 'lit';
import {customElement} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {fire} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-select': GrSelect;
  }
  interface HTMLElementEventMap {
    'bind-value-changed': BindValueChangeEvent;
  }
}

/**
 * GrSelect `gr-select` component.
 * TODO: Figure out if this class still has merit over native <select>
 */
@customElement('gr-select')
export class GrSelect extends LitElement {
  private _bindValue?: string | number | boolean;

  get bindValue() {
    return this._bindValue;
  }

  set bindValue(bindValue: string | number | boolean | undefined) {
    if (this._bindValue === bindValue) return;
    this._bindValue = bindValue;
    this._updateValue();
    // It's possible to have a value of 0.
    if (this.bindValue !== undefined) {
      // Set for chrome/safari so it happens instantly
      this.nativeSelect.value = String(this.bindValue);
      // Async needed for firefox to populate value. It was trying to do it
      // before options from a dom-repeat were rendered previously.
      // See https://issues.gerritcodereview.com/issues/40007948
      setTimeout(() => {
        this.nativeSelect.value = String(this.bindValue);
      }, 1);
    }
    // TODO: bind-value-changed is polymer-specific.  Move to a new event
    // name and rely on ValueChangedEvent instead of BindValueChangeEvent.
    fire(this, 'bind-value-changed', {value: this.convert(this._bindValue)});
  }

  get nativeSelect() {
    return this.querySelector('select')!;
  }

  constructor() {
    super();
    this.addEventListener('change', () => {
      this.bindValue = this.nativeSelect.value;
    });
  }

  override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    // If not set via the property, set bind-value to the element value.
    if (this.bindValue === undefined && this.nativeSelect.options.length > 0) {
      this.bindValue = this.nativeSelect.value;
    }
  }

  override render() {
    return html`<slot></slot>`;
  }

  _updateValue() {
    // It's possible to have a value of 0.
    if (this.bindValue !== undefined) {
      // Set for chrome/safari so it happens instantly
      this.nativeSelect.value = this.convert(this.bindValue) ?? '';
      // Async needed for firefox to populate value. It was trying to do it
      // before options from a dom-repeat were rendered previously.
      // See https://g-issues.gerritcodereview.com/issues/40007948
      setTimeout(() => {
        this.nativeSelect.value = this.convert(this.bindValue) ?? '';
      }, 1);
    }
  }

  private convert(value: string | boolean | number | undefined) {
    if (value === undefined) return undefined;
    if (typeof value === 'string') return value;
    return String(value);
  }

  override focus() {
    this.nativeSelect.focus();
  }
}
