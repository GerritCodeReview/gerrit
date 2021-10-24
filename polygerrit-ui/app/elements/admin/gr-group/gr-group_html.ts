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
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-subpage-styles">
    h3.edited:after {
      color: var(--deemphasized-text-color);
      content: ' *';
    }
  </style>
  <style include="gr-form-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <div class="main gr-form-styles read-only">
    <div id="loading" class$="[[_computeLoadingClass(_loading)]]">
      Loading...
    </div>
    <div id="loadedContent" class$="[[_computeLoadingClass(_loading)]]">
      <h1 id="Title" class="heading-1">[[convertToString(_groupName)]]</h1>
      <h2 id="configurations" class="heading-2">General</h2>
      <div id="form">
        <fieldset>
          <h3 id="groupUUID" class="heading-3">Group UUID</h3>
          <fieldset>
            <gr-copy-clipboard
              id="uuid"
              text="[[_getGroupUUID(_groupConfig.id)]]"
            ></gr-copy-clipboard>
          </fieldset>
          <h3
            id="groupName"
            class$="heading-3 [[_computeHeaderClass(_rename)]]"
          >
            Group Name
          </h3>
          <fieldset>
            <span class="value">
              <gr-autocomplete
                id="groupNameInput"
                text="[[convertToString(_groupConfig.name)]]"
                disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
                on-text-changed="handleNameTextChanged"
              ></gr-autocomplete>
            </span>
            <span
              class="value"
              disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
            >
              <gr-button
                id="inputUpdateNameBtn"
                on-click="_handleSaveName"
                disabled="[[!_rename]]"
              >
                Rename Group</gr-button
              >
            </span>
          </fieldset>
          <h3
            id="groupOwner"
            class$="heading-3 [[_computeHeaderClass(_owner)]]"
          >
            Owners
          </h3>
          <fieldset>
            <span class="value">
              <gr-autocomplete
                id="groupOwnerInput"
                text="[[convertToString(_groupConfig.owner)]]"
                value="[[convertToString(_groupConfigOwner)]]"
                query="[[_query]]"
                disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
                on-text-changed="handleOwnerTextChanged"
                on-value-changed="handleOwnerValueChanged"
              >
              </gr-autocomplete>
            </span>
            <span
              class="value"
              disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
            >
              <gr-button
                id="inputUpdateOwnerBtn"
                on-click="_handleSaveOwner"
                disabled="[[!_owner]]"
              >
                Change Owners</gr-button
              >
            </span>
          </fieldset>
          <h3 class$="heading-3 [[_computeHeaderClass(_description)]]">
            Description
          </h3>
          <fieldset>
            <div>
              <iron-autogrow-textarea
                class="description"
                autocomplete="on"
                bind-value="[[convertToString(_groupConfig.description)]]"
                disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
                on-bind-value-changed="handleDescriptionBindValueChanged"
              ></iron-autogrow-textarea>
            </div>
            <span
              class="value"
              disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
            >
              <gr-button
                on-click="_handleSaveDescription"
                disabled="[[!_description]]"
              >
                Save Description
              </gr-button>
            </span>
          </fieldset>
          <h3 id="options" class$="heading-3 [[_computeHeaderClass(_options)]]">
            Group Options
          </h3>
          <fieldset>
            <section>
              <span class="title">
                Make group visible to all registered users
              </span>
              <span class="value">
                <gr-select
                  id="visibleToAll"
                  bind-value="{{_groupConfig.options.visible_to_all}}"
                >
                  <select
                    disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
                  >
                    <template is="dom-repeat" items="[[_submitTypes]]">
                      <option value="[[convertToString(item.value)]]">
                        [[item.label]]
                      </option>
                    </template>
                  </select>
                </gr-select>
              </span>
            </section>
            <span
              class="value"
              disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"
            >
              <gr-button on-click="_handleSaveOptions" disabled="[[!_options]]">
                Save Group Options
              </gr-button>
            </span>
          </fieldset>
        </fieldset>
      </div>
    </div>
  </div>
`;
