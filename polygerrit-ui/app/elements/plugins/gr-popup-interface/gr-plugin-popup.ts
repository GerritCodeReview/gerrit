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
import '../../shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, query} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-popup': GrPluginPopup;
  }
}

@customElement('gr-plugin-popup')
export class GrPluginPopup extends LitElement {
  @query('#overlay') protected overlay!: GrOverlay;

  static override get styles() {
    return [sharedStyles];
  }

  override render() {
    return html`<gr-overlay id="overlay" with-backdrop="">
      <slot></slot>
    </gr-overlay>`;
  }

  get opened() {
    return this.overlay.opened;
  }

  open() {
    return this.overlay.open();
  }

  close() {
    return this.overlay.close();
  }
}
