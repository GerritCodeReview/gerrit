<!--
@license
Copyright (C) 2016 The Android Open Source Project

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

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-date-formatter/gr-date-formatter.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../../styles/gr-form-styles.html">

<dom-module id="gr-menu-editor">
  <template>
    <style include="shared-styles">
      .buttonColumn {
        width: 2em;
      }
      .moveUpButton,
      .moveDownButton {
        width: 100%
      }
      tbody tr:first-of-type td .moveUpButton,
      tbody tr:last-of-type td .moveDownButton {
        display: none;
      }
      td.urlCell {
        word-break: break-word;
      }
      .newUrlInput {
        min-width: 23em;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="gr-form-styles">
      <table>
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="url-header">URL</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[menuItems]]">
            <tr>
              <td>[[item.name]]</td>
              <td class="urlCell">[[item.url]]</td>
              <td class="buttonColumn">
                <gr-button
                    link
                    data-index$="[[index]]"
                    on-click="_handleMoveUpButton"
                    class="moveUpButton">↑</gr-button>
              </td>
              <td class="buttonColumn">
                <gr-button
                    link
                    data-index$="[[index]]"
                    on-click="_handleMoveDownButton"
                    class="moveDownButton">↓</gr-button>
              </td>
              <td>
                <gr-button
                    link
                    data-index$="[[index]]"
                    on-click="_handleDeleteButton"
                    class="remove-button">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <iron-input
                  placeholder="New Title"
                  on-keydown="_handleInputKeydown"
                  bind-value="{{_newName}}">
                <input
                    is="iron-input"
                    placeholder="New Title"
                    on-keydown="_handleInputKeydown"
                    bind-value="{{_newName}}">
              </iron-input>
            </th>
            <th>
              <iron-input
                  class="newUrlInput"
                  placeholder="New URL"
                  on-keydown="_handleInputKeydown"
                  bind-value="{{_newUrl}}">
                <input
                    class="newUrlInput"
                    is="iron-input"
                    placeholder="New URL"
                    on-keydown="_handleInputKeydown"
                    bind-value="{{_newUrl}}">
              </iron-input>
            </th>
            <th></th>
            <th></th>
            <th>
              <gr-button
                  link
                  disabled$="[[_computeAddDisabled(_newName, _newUrl)]]"
                  on-click="_handleAddButton">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
  </template>
  <script src="gr-menu-editor.js"></script>
</dom-module>
