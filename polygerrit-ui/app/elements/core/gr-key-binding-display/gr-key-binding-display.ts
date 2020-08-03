/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-key-binding-display_html';
import {customElement, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-key-binding-display': GrKeyBindingDisplay;
  }
}

@customElement('gr-key-binding-display')
export class GrKeyBindingDisplay extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  binding: string[][] = [];

  _computeModifiers(binding: string[][]) {
    return binding.slice(0, binding.length - 1);
  }

  _computeKey(binding: string[][]) {
    return binding[binding.length - 1];
  }
}
