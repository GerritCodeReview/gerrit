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
      align-items: center;
      display: flex;
    }
    select {
      max-width: 15em;
    }
    .arrow {
      color: var(--deemphasized-text-color);
      margin: 0 var(--spacing-m);
    }
    gr-dropdown-list {
      --trigger-style: {
        color: var(--deemphasized-text-color);
        text-transform: none;
        font-family: var(--font-family);
      }
      --trigger-hover-color: rgba(0, 0, 0, 0.6);
    }
    @media screen and (max-width: 50em) {
      .filesWeblinks {
        display: none;
      }
      gr-dropdown-list {
        --native-select-style: {
          max-width: 5.25em;
        }
        --dropdown-content-stype: {
          max-width: 300px;
        }
      }
    }
    /*
    * Typically icons are 20px, which is the normal line-height.
    * The copy icon is too prominent at 20px, so we choose 16px
    * here, but add 2x2px padding below, so the entire
    * component should still fit nicely into a normal inline
    * layout flow.
    */
    iron-icon {
      height: 16px;
      width: 16px;
      position: relative;
      top: 3px;
    }
    gr-button {
      --gr-button: {
        padding: 2px;
      }
    }
  </style>
  <span class="patchRange" aria-label="patch range starts with">
    <template is="dom-if" if="[[_showDownArrow(basePatchNum)]]">
      <gr-button
        link=""
        on-click="_handleDownArrowClicked"
        has-tooltip
        title="Change to Base"
      >
        <iron-icon icon="gr-icons:downArrow"></iron-icon>
      </gr-button>
    </template>
    <gr-dropdown-list
      id="basePatchDropdown"
      value="[[basePatchNum]]"
      on-value-change="_handlePatchChange"
      items="[[_baseDropdownContent]]"
    >
    </gr-dropdown-list>
  </span>
  <span is="dom-if" if="[[filesWeblinks.meta_a]]" class="filesWeblinks">
    <template is="dom-repeat" items="[[filesWeblinks.meta_a]]" as="weblink">
      <a target="_blank" rel="noopener" href$="[[weblink.url]]"
        >[[weblink.name]]</a
      >
    </template>
  </span>
  <span aria-hidden="true" class="arrow">→</span>
  <span class="patchRange" aria-label="patch range ends with">
    <gr-dropdown-list
      id="patchNumDropdown"
      value="[[patchNum]]"
      on-value-change="_handlePatchChange"
      items="[[_patchDropdownContent]]"
    >
    </gr-dropdown-list>
    <template is="dom-if" if="[[_showUpArrow(patchNum, _sortedRevisions)]]">
      <gr-button
        link=""
        on-click="_handleUpArrowClicked"
        has-tooltip
        title="Change to latest"
      >
        <iron-icon icon="gr-icons:upArrow"></iron-icon>
      </gr-button>
    </template>
    <span is="dom-if" if="[[filesWeblinks.meta_b]]" class="filesWeblinks">
      <template is="dom-repeat" items="[[filesWeblinks.meta_b]]" as="weblink">
        <a target="_blank" href$="[[weblink.url]]">[[weblink.name]]</a>
      </template>
    </span>
  </span>
`;
