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
import '../../../styles/gr-page-nav-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-settings-menu-item_html';
import {property, customElement} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-menu-item': GrSettingsMenuItem;
  }
}

@customElement('gr-settings-menu-item')
class GrSettingsMenuItem extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  href?: string;

  @property({type: String})
  title = '';
}
