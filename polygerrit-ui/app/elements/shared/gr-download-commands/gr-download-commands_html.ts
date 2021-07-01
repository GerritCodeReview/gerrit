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
    paper-tabs {
      height: 3rem;
      margin-bottom: var(--spacing-m);
      --paper-tabs-selection-bar-color: var(--link-color);
    }
    paper-tab {
      max-width: 15rem;
      text-transform: uppercase;
      --paper-tab-ink: var(--link-color);
    }
    label,
    input {
      display: block;
    }
    label {
      font-weight: var(--font-weight-bold);
    }
    .schemes {
      display: flex;
      justify-content: space-between;
    }
    .commands {
      display: flex;
      flex-direction: column;
    }
    gr-shell-command {
      margin-bottom: var(--spacing-m);
    }
    .hidden {
      display: none;
    }
  </style>
  <div class="schemes">
    <paper-tabs
      id="downloadTabs"
      class$="[[_computeShowTabs(schemes)]]"
      selected="[[_computeSelected(schemes, selectedScheme)]]"
      on-selected-changed="_handleTabChange"
    >
      <template is="dom-repeat" items="[[schemes]]" as="scheme">
        <paper-tab data-scheme$="[[scheme]]">[[scheme]]</paper-tab>
      </template>
    </paper-tabs>
  </div>
  <div class="commands" hidden$="[[!schemes.length]]" hidden="">
    <template is="dom-repeat" items="[[commands]]" as="command" indexAs="index">
      <gr-shell-command
        class$="[[_computeClass(command.title)]]"
        label="[[command.title]]"
        command="[[command.command]]"
        tooltip="[[_computeTooltip(showKeyboardShortcutTooltips, index)]]"
      ></gr-shell-command>
    </template>
  </div>
`;
