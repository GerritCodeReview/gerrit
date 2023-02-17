/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-param': GrEndpointParam;
  }
}

@customElement('gr-endpoint-param')
export class GrEndpointParam extends LitElement {
  @property({type: String, reflect: true})
  name = '';

  @property({type: Object})
  value?: unknown;

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('value')) {
      fireNoBubbleNoCompose(this, 'value-changed', {value: this.value});
    }
  }
}
