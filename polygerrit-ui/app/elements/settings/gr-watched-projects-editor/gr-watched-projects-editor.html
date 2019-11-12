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
<link rel="import" href="../../shared/gr-autocomplete/gr-autocomplete.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-watched-projects-editor">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      #watchedProjects .notifType {
        text-align: center;
        padding: 0 var(--spacing-s);
      }
      .notifControl {
        cursor: pointer;
        text-align: center;
      }
      .notifControl:hover {
        outline: 1px solid var(--border-color);
      }
      .projectFilter {
        color: var(--deemphasized-text-color);
        font-style: italic;
        margin-left: var(--spacing-l);
      }
      .newFilterInput {
        width: 100%;
      }
    </style>
    <div class="gr-form-styles">
      <table id="watchedProjects">
        <thead>
          <tr>
            <th>Repo</th>
            <template is="dom-repeat" items="[[_getTypes()]]">
              <th class="notifType">[[item.name]]</th>
            </template>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template
              is="dom-repeat"
              items="[[_projects]]"
              as="project"
              index-as="projectIndex">
            <tr>
              <td>
                [[project.project]]
                <template is="dom-if" if="[[project.filter]]">
                  <div class="projectFilter">[[project.filter]]</div>
                </template>
              </td>
              <template
                  is="dom-repeat"
                  items="[[_getTypes()]]"
                  as="type">
                <td class="notifControl" on-click="_handleNotifCellClick">
                  <input
                      type="checkbox"
                      data-index$="[[projectIndex]]"
                      data-key$="[[type.key]]"
                      on-change="_handleCheckboxChange"
                      checked$="[[_computeCheckboxChecked(project, type.key)]]">
                </td>
              </template>
              <td>
                <gr-button
                    link
                    data-index$="[[projectIndex]]"
                    on-click="_handleRemoveProject">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <gr-autocomplete
                  id="newProject"
                  query="[[_query]]"
                  threshold="1"
                  allow-non-suggested-values
                  tab-complete
                  placeholder="Repo"></gr-autocomplete>
            </th>
            <th colspan$="[[_getTypeCount()]]">
              <iron-input
                  class="newFilterInput"
                  placeholder="branch:name, or other search expression">
                <input
                    id="newFilter"
                    class="newFilterInput"
                    is="iron-input"
                    placeholder="branch:name, or other search expression">
              </iron-input>
            </th>
            <th>
              <gr-button link on-click="_handleAddProject">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-watched-projects-editor.js"></script>
</dom-module>
