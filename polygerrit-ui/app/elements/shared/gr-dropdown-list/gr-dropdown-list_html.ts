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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      display: inline-block;
    }
    #triggerText {
      -moz-user-select: text;
      -ms-user-select: text;
      -webkit-user-select: text;
      user-select: text;
    }
    .dropdown-trigger {
      cursor: pointer;
      padding: 0;
    }
    .dropdown-content {
      background-color: var(--dropdown-background-color);
      box-shadow: var(--elevation-level-2);
      max-height: 70vh;
      min-width: 266px;
    }
    paper-listbox {
      --paper-listbox: {
        padding: 0;
      }
    }
    paper-item {
      cursor: pointer;
      flex-direction: column;
      font-size: inherit;
      /* This variable was introduced in Dec 2019. We keep both min-height
         * rules around, because --paper-item-min-height is not yet upstreamed.
         */
      --paper-item-min-height: 0;
      --paper-item: {
        min-height: 0;
        padding: 10px 16px;
      }
      --paper-item-focused-before: {
        background-color: var(--selection-background-color);
      }
      --paper-item-focused: {
        background-color: var(--selection-background-color);
      }
    }
    paper-item:hover {
      background-color: var(--hover-background-color);
    }
    paper-item:not(:last-of-type) {
      border-bottom: 1px solid var(--border-color);
    }
    .bottomContent {
      color: var(--deemphasized-text-color);
    }
    .bottomContent,
    .topContent {
      display: flex;
      justify-content: space-between;
      flex-direction: row;
      width: 100%;
    }
    gr-button {
      font-family: var(--trigger-style-font-family);
      --gr-button-text-color: var(--trigger-style-text-color);
    }
    gr-date-formatter {
      color: var(--deemphasized-text-color);
      margin-left: var(--spacing-xxl);
      white-space: nowrap;
    }
    gr-select {
      display: none;
    }
    /* Because the iron dropdown 'area' includes the trigger, and the entire
       width of the dropdown, we want to treat tapping the area above the
       dropdown content as if it is tapping whatever content is underneath it.
       The next two styles allow this to happen. */
    iron-dropdown {
      max-width: none;
      pointer-events: none;
    }
    paper-listbox {
      pointer-events: auto;
    }
    @media only screen and (max-width: 50em) {
      gr-select {
        display: inline;
        @apply --gr-select-style;
      }
      gr-button,
      iron-dropdown {
        display: none;
      }
      select {
        @apply --native-select-style;
      }
    }
  </style>
  <gr-button
    disabled="[[disabled]]"
    down-arrow=""
    link=""
    id="trigger"
    class="dropdown-trigger"
    on-click="_showDropdownTapHandler"
    slot="dropdown-trigger"
    no-uppercase
  >
    <span id="triggerText">[[text]]</span>
    <gr-copy-clipboard
      hidden="[[!showCopyForTriggerText]]"
      hideInput=""
      text="[[text]]"
    ></gr-copy-clipboard>
  </gr-button>
  <iron-dropdown
    id="dropdown"
    vertical-align="top"
    horizontal-align="left"
    dynamic-align
    no-overlap
    allow-outside-scroll="true"
    on-click="_handleDropdownClick"
  >
    <paper-listbox
      class="dropdown-content"
      slot="dropdown-content"
      attr-for-selected="data-value"
      selected="{{value}}"
    >
      <template
        is="dom-repeat"
        items="[[items]]"
        initial-count="[[initialCount]]"
      >
        <paper-item disabled="[[item.disabled]]" data-value$="[[item.value]]">
          <div class="topContent">
            <div>[[item.text]]</div>
            <template is="dom-if" if="[[item.date]]">
              <gr-date-formatter date-str="[[item.date]]"></gr-date-formatter>
            </template>
            <template is="dom-if" if="[[item.file]]">
              <gr-file-status-chip file="[[item.file]]"></gr-file-status-chip>
            </template>
          </div>
          <template is="dom-if" if="[[item.bottomText]]">
            <div class="bottomContent">
              <div>[[item.bottomText]]</div>
            </div>
          </template>
        </paper-item>
      </template>
    </paper-listbox>
  </iron-dropdown>
  <gr-select bind-value="{{value}}">
    <select>
      <template is="dom-repeat" items="[[items]]">
        <option
          disabled$="[[item.disabled]]"
          value="[[computeStringValue(item.value)]]"
        >
          [[_computeMobileText(item)]]
        </option>
      </template>
    </select>
  </gr-select>
`;
