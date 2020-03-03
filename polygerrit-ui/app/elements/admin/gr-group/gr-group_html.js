<!--
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/gr-subpage-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-autocomplete/gr-autocomplete.html">
<link rel="import" href="../../shared/gr-copy-clipboard/gr-copy-clipboard.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../shared/gr-select/gr-select.html">

<dom-module id="gr-group">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-subpage-styles">
      h3.edited:after {
        color: var(--deemphasized-text-color);
        content: ' *';
      }
      .inputUpdateBtn {
        margin-top: var(--spacing-s);
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <main class="gr-form-styles read-only">
      <div id="loading" class$="[[_computeLoadingClass(_loading)]]">
        Loading...
      </div>
      <div id="loadedContent" class$="[[_computeLoadingClass(_loading)]]">
        <h1 id="Title">[[_groupName]]</h1>
        <h2 id="configurations">General</h2>
        <div id="form">
          <fieldset>
            <h3 id="groupUUID">Group UUID</h3>
            <fieldset>
              <gr-copy-clipboard
                  text="[[groupId]]"></gr-copy-clipboard>
            </fieldset>
            <h3 id="groupName" class$="[[_computeHeaderClass(_rename)]]">
              Group Name
            </h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete
                    id="groupNameInput"
                    text="{{_groupConfig.name}}"
                    disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"></gr-autocomplete>
              </span>
              <span class="value" disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button
                    id="inputUpdateNameBtn"
                    on-click="_handleSaveName"
                    disabled="[[!_rename]]">
                  Rename Group</gr-button>
              </span>
            </fieldset>
            <h3 class$="[[_computeHeaderClass(_owner)]]">
              Owners
            </h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete
                    id="groupOwnerInput"
                    text="{{_groupConfig.owner}}"
                    value="{{_groupConfigOwner}}"
                    query="[[_query]]"
                    disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                </gr-autocomplete>
              </span>
              <span class="value" disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button
                    on-click="_handleSaveOwner"
                    disabled="[[!_owner]]">
                  Change Owners</gr-button>
              </span>
            </fieldset>
            <h3 class$="[[_computeHeaderClass(_description)]]">
              Description
            </h3>
            <fieldset>
              <div>
                <iron-autogrow-textarea
                    class="description"
                    autocomplete="on"
                    bind-value="{{_groupConfig.description}}"
                    disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"></iron-autogrow-textarea>
              </div>
              <span class="value" disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button
                    on-click="_handleSaveDescription"
                    disabled="[[!_description]]">
                  Save Description
                </gr-button>
              </span>
            </fieldset>
            <h3 id="options" class$="[[_computeHeaderClass(_options)]]">
              Group Options
            </h3>
            <fieldset id="visableToAll">
              <section>
                <span class="title">
                  Make group visible to all registered users
                </span>
                <span class="value">
                  <gr-select
                      id="visibleToAll"
                      bind-value="{{_groupConfig.options.visible_to_all}}">
                    <select disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                      <template is="dom-repeat" items="[[_submitTypes]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <span class="value" disabled$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button
                    on-click="_handleSaveOptions"
                    disabled="[[!_options]]">
                  Save Group Options
                </gr-button>
              </span>
            </fieldset>
          </fieldset>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-group.js"></script>
</dom-module>
