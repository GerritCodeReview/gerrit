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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-tooltip_html';
import {customElement, property, observe} from '@polymer/decorators';

export interface GrTooltip {
  $: {};
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-tooltip': GrTooltip;
  }
}

@customElement('gr-tooltip')
export class GrTooltip extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  text = '';

  @property({type: String})
  maxWidth = '';

  @property({type: Boolean, reflectToAttribute: true})
  positionBelow = false;

  @observe('maxWidth')
  _updateWidth(maxWidth: string) {
    this.updateStyles({'--tooltip-max-width': maxWidth});
  }
}
