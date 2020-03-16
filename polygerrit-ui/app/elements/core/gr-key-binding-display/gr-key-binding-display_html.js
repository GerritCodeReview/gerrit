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
      .key {
        background-color: var(--chip-background-color);
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        display: inline-block;
        font-weight: var(--font-weight-bold);
        padding: var(--spacing-xxs) var(--spacing-m);
        text-align: center;
      }
    </style>
    <template is="dom-repeat" items="[[binding]]">
      <template is="dom-if" if="[[index]]">
        or
      </template>
      <template is="dom-repeat" items="[[_computeModifiers(item)]]" as="modifier">
        <span class="key modifier">[[modifier]]</span>
      </template>
      <span class="key">[[_computeKey(item)]]</span>
    </template>
`;
