/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        box-sizing: border-box;
        display: block;
        min-height: var(--header-height);
        position: relative;
      }
      header {
        background: inherit;
        border: inherit;
        display: inline;
        height: inherit;
      }
      .floating {
        left: 0;
        position: fixed;
        width: 100%;
        will-change: top;
      }
      .fixedAtTop {
        border-bottom: 1px solid #a4a4a4;
        box-shadow: var(--elevation-level-2);
      }
    </style>
    <header id="header" class\$="[[_computeHeaderClass(_headerFloating, _topLast)]]">
      <slot></slot>
    </header>
`;
