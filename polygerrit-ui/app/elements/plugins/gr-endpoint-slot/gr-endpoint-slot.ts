/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-slot': GrEndpointSlot;
  }
}

/**
 * `gr-endpoint-slot` is used when need control over where
 * the registered element should appear inside of the endpoint.
 */
@customElement('gr-endpoint-slot')
export class GrEndpointSlot extends LitElement {
  @property({type: String})
  name!: string;
}

/**
 * Mark name as required as `gr-endpoint-slot` without a name
 * is meaningless.
 *
 * This should help catch errors when you assign an element without
 * name to GrEndpointSlot type.
 */
export interface GrEndpointSlot extends LitElement {
  name: string;
}
