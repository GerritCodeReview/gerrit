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
        color: var(--primary-text-color);
        display: block;
        max-height: 90vh;
        overflow: auto;
      }
      .container {
        display: flex;
        flex-direction: column;
        max-height: 90vh;
        padding: var(--spacing-xl);
      }
      header {
        flex-shrink: 0;
        padding-bottom: var(--spacing-xl);
      }
      main {
        display: flex;
        flex-shrink: 1;
        width: 100%;
        flex: 1;
        /* IMPORTANT: required for firefox */
        min-height: 0px;
      }
      main .overflow-container {
        flex: 1;
        overflow: auto;
      }
      footer {
        display: flex;
        flex-shrink: 0;
        justify-content: flex-end;
        padding-top: var(--spacing-xl);
      }
      gr-button {
        margin-left: var(--spacing-l);
      }
      .hidden {
        display: none;
      }
    </style>
    <div class="container" on-keydown="_handleKeydown">
      <header class="font-h3"><slot name="header"></slot></header>
      <main>
        <div class="overflow-container">
          <slot name="main"></slot>
        </div>
      </main>
      <footer>
        <slot name="footer"></slot>
        <gr-button id="cancel" class\$="[[_computeCancelClass(cancelLabel)]]" link="" on-click="_handleCancelTap">
          [[cancelLabel]]
        </gr-button>
        <gr-button id="confirm" link="" primary="" on-click="_handleConfirm" disabled="[[disabled]]" title\$="[[confirmTooltip]]">
          [[confirmLabel]]
        </gr-button>
      </footer>
    </div>
`;
