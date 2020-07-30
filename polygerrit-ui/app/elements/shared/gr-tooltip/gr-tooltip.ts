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
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-tooltip_html';
import {customElement, property} from '@polymer/decorators';

export interface GrTooltip {
  $: {};
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-tooltip': GrTooltip;
  }
}

@customElement('gr-tooltip')
export class GrTooltip extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  text = '';

  @property({type: String, observer: '_updateWidth'})
  maxWidth = '';

  @property({type: Boolean, reflectToAttribute: true})
  positionBelow = false;

  private _updateWidth(maxWidth) {
    this.updateStyles({'--tooltip-max-width': maxWidth});
  }
}
