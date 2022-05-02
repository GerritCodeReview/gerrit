/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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

import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement} from '@polymer/decorators';
import {html} from '@polymer/polymer/lib/utils/html-tag';

@customElement('gr-css-mixins')
export class GrCssMixins extends PolymerElement {
  /* eslint-disable lit/prefer-static-styles */
  static get template() {
    return html`
      <style>
        :host {
          /* If you want to use css-mixins in Lit elements, then you have to first
          use them in a PolymerElement somewhere. We are collecting all css-
          mixin usage here, but we may move them somewhere else later when
          converting gr-app-element to Lit. In the Lit element you can then use
          the css variables directly such as --paper-input-container_-_padding,
          so you don't have to mess with mixins at all.
          */
          --paper-input-container: {
            padding: 8px 0;
          }
          --paper-input-container-input: {
            font-size: var(--font-size-normal);
            line-height: var(--line-height-normal);
            color: var(--primary-text-color);
          }
          --paper-input-container-underline: {
            height: 0;
            display: none;
          }
          --paper-input-container-underline-focus: {
            height: 0;
            display: none;
          }
          --paper-input-container-underline-disabled: {
            height: 0;
            display: none;
          }
          --paper-input-container-label: {
            display: none;
          }
        }
      </style>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-css-mixins': GrCssMixins;
  }
}
