/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement, PropertyValues} from 'lit';
import {customElement} from 'lit/decorators';
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
    fire(this, 'bind-value-changed', {value: this.convert(this._bindValue)});
  }

  get nativeSelect() {
    // gr-select is not a shadow component
    // TODO(taoalpha): maybe we should convert
    // it into a shadow dom component instead
    // TODO(TS): should warn if no `select` detected.
    return this.querySelector('select')!;
  }

  constructor() {
    super();
    this.addEventListener('change', () => this.valueChanged());
    this.addEventListener('dom-change', () => this._updateValue());
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
      this.nativeSelect.value = String(this.bindValue);
      // Async needed for firefox to populate value. It was trying to do it
      // before options from a dom-repeat were rendered previously.
      // See https://bugs.chromium.org/p/gerrit/issues/detail?id=7735
      setTimeout(() => {
        this.nativeSelect.value = String(this.bindValue);
      }, 1);
    }
  }

  private convert(value: string | boolean | number | undefined) {
    if (value === undefined) return undefined;
    if (typeof value === 'string') return value;
    return String(value);
  }

  private valueChanged() {
    this.bindValue = this.nativeSelect.value;
  }

  override focus() {
    this.nativeSelect.focus();
  }
}
