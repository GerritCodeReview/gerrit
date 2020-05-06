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
    button {
      background-color: transparent;
      cursor: pointer;
    }
    iron-icon.active {
      fill: var(--link-color);
    }
    iron-icon {
      vertical-align: top;
      --iron-icon-height: var(
        --gr-change-star-size,
        var(--line-height-normal, 20px)
      );
      --iron-icon-width: var(
        --gr-change-star-size,
        var(--line-height-normal, 20px)
      );
    }
  </style>
  <button aria-label="Change star" on-click="toggleStar">
    <iron-icon
      class$="[[_computeStarClass(change.starred)]]"
      icon$="[[_computeStarIcon(change.starred)]]"
    ></iron-icon>
  </button>
`;
