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

import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../gr-limited-text/gr-limited-text';
import '../../../styles/shared-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {htmlTemplate} from './gr-linked-chip_html';
import {fireEvent} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-linked-chip': GrLinkedChip;
  }
}

@customElement('gr-linked-chip')
export class GrLinkedChip extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  href?: string;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Boolean})
  removable = false;

  @property({type: String})
  text?: string;

  @property({type: Boolean})
  transparentBackground = false;

  /**  If provided, sets the maximum length of the content. */
  @property({type: Number})
  limit?: number;

  _getBackgroundClass(transparent: boolean) {
    return transparent ? 'transparentBackground' : '';
  }

  _handleRemoveTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'remove');
  }
}
