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
import {css} from 'lit';

export const hovercardStyles = css`
  :host {
    position: absolute;
    display: none;
    z-index: 200;
    max-width: 600px;
    outline: none;
  }
  :host(.hovered) {
    display: block;
  }
  :host(.hide) {
    visibility: hidden;
  }
  /* You have to use a <div class="container"> in your hovercard in order
      to pick up this consistent styling. */
  #container {
    background: var(--dialog-background-color);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    box-shadow: var(--elevation-level-5);
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-hovercard-styles">
  <template>
    <style>
    ${hovercardStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
