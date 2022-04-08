/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-param': GrEndpointParam;
  }
}

@customElement('gr-endpoint-param')
export class GrEndpointParam extends PolymerElement {
  @property({type: String, reflectToAttribute: true})
  name = '';

  @property({
    type: Object,
    notify: true,
    observer: '_valueChanged',
  })
  value?: unknown;

  _valueChanged(value: unknown) {
    /* In polymer 2 the following change was made:
    "Property change notifications (property-changed events) aren't fired when
    the value changes as a result of a binding from the host"
    (see https://polymer-library.polymer-project.org/2.0/docs/about_20).
    To workaround this problem, we fire the event from the observer.
    In some cases this fire the event twice, but our code is
    ready for it.
    */
    this.dispatchEvent(new CustomEvent('value-changed', {detail: {value}}));
  }
}
