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
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-menu-item': GrSettingsMenuItem;
  }
}

@customElement('gr-settings-menu-item')
export class GrSettingsMenuItem extends LitElement {
  @property({type: String})
  href?: string;

  @property({type: String})
  override title = '';

  static get styles() {
    return [sharedStyles, pageNavStyles];
  }

  override render() {
    const href = this.href ?? '';
    return html` <div class="navStyles">
      <li><a href="${href}">${this.title}</a></li>
    </div>`;
  }
}
