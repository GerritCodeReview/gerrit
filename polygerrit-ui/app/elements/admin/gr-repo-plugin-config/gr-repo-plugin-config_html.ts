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
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-form-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-subpage-styles">
    .inherited {
      color: var(--deemphasized-text-color);
      margin-left: var(--spacing-m);
    }
    section.section:not(.ARRAY) .title {
      align-items: center;
      display: flex;
    }
    section.section.ARRAY .title {
      padding-top: var(--spacing-m);
    }
  </style>
  <div class="gr-form-styles">
    <fieldset>
      <h4>[[pluginData.name]]</h4>
      <template is="dom-repeat" items="[[_pluginConfigOptions]]" as="option">
        <section class$="section [[option.info.type]]">
          <span class="title">
            <gr-tooltip-content
              has-tooltip="[[option.info.description]]"
              show-icon="[[option.info.description]]"
              title="[[option.info.description]]"
            >
              <span>[[option.info.display_name]]</span>
            </gr-tooltip-content>
          </span>
          <span class="value">
            <template is="dom-if" if="[[_isArray(option.info.type)]]">
              <gr-plugin-config-array-editor
                on-plugin-config-option-changed="_handleArrayChange"
                plugin-option="[[option]]"
                disabled$="[[_computeDisabled(disabled, option.info.editable)]]"
              ></gr-plugin-config-array-editor>
            </template>
            <template is="dom-if" if="[[_isBoolean(option.info.type)]]">
              <paper-toggle-button
                checked="[[_computeChecked(option.info.value)]]"
                on-change="_handleBooleanChange"
                data-option-key$="[[option._key]]"
                disabled$="[[_computeDisabled(disabled, option.info.editable)]]"
                on-tap="_onTapPluginBoolean"
              ></paper-toggle-button>
            </template>
            <template is="dom-if" if="[[_isList(option.info.type)]]">
              <gr-select
                bind-value$="[[option.info.value]]"
                on-change="_handleListChange"
              >
                <select
                  data-option-key$="[[option._key]]"
                  disabled$="[[_computeDisabled(disabled, option.info.editable)]]"
                >
                  <template
                    is="dom-repeat"
                    items="[[option.info.permitted_values]]"
                    as="value"
                  >
                    <option value$="[[value]]">[[value]]</option>
                  </template>
                </select>
              </gr-select>
            </template>
            <template is="dom-if" if="[[_isString(option.info.type)]]">
              <iron-input
                bind-value="[[option.info.value]]"
                on-input="_handleStringChange"
                data-option-key$="[[option._key]]"
                disabled$="[[_computeDisabled(disabled, option.info.editable)]]"
              >
                <input
                  is="iron-input"
                  value="[[option.info.value]]"
                  on-input="_handleStringChange"
                  data-option-key$="[[option._key]]"
                  disabled$="[[_computeDisabled(disabled, option.info.editable)]]"
                />
              </iron-input>
            </template>
            <template is="dom-if" if="[[option.info.inherited_value]]">
              <span class="inherited">
                (Inherited: [[option.info.inherited_value]])
              </span>
            </template>
          </span>
        </section>
      </template>
    </fieldset>
  </div>
`;
