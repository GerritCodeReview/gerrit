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
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-param': GrEndpointParam;
  }
}

@customElement('gr-endpoint-param')
export class GrEndpointParam extends LegacyElementMixin(PolymerElement) {
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
