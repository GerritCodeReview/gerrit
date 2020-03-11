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
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-group-list">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
        #groups .nameColumn {
          min-width: 11em;
          width: auto;
        }
        .descriptionHeader {
          min-width: 21.5em;
        }
        .visibleCell {
          text-align: center;
          width: 6em;
        }
      </style>
    <div class="gr-form-styles">
      <table id="groups">
        <thead>
          <tr>
            <th class="nameHeader">Name</th>
            <th class="descriptionHeader">Description</th>
            <th class="visibleCell">Visible to all</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_groups]]">
            <tr>
              <td class="nameColumn">
                <a href$="[[_computeGroupPath(item)]]">
                  [[item.name]]
                </a>
              </td>
              <td>[[item.description]]</td>
              <td class="visibleCell">[[_computeVisibleToAll(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-group-list.js"></script>
</dom-module>
