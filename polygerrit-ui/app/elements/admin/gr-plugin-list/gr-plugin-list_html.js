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
<link rel="import" href="/bower_components/polymer/polymer.html">

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.html">
<link rel="import" href="../../../styles/gr-table-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-list-view/gr-list-view.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-plugin-list">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-table-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <gr-list-view
        filter="[[_filter]]"
        items-per-page="[[_pluginsPerPage]]"
        items="[[_plugins]]"
        loading="[[_loading]]"
        offset="[[_offset]]"
        path="[[_path]]">
      <table id="list" class="genericList">
        <tr class="headerRow">
          <th class="name topHeader">Plugin Name</th>
          <th class="version topHeader">Version</th>
          <th class="status topHeader">Status</th>
        </tr>
        <tr id="loading" class$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        <tbody class$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownPlugins]]">
            <tr class="table">
              <td class="name">
                <template is="dom-if" if="[[item.index_url]]">
                  <a href$="[[_computePluginUrl(item.index_url)]]">[[item.id]]</a>
                </template>
                <template is="dom-if" if="[[!item.index_url]]">
                  [[item.id]]
                </template>
              </td>
              <td class="version">[[item.version]]</td>
              <td class="status">[[_status(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-plugin-list.js"></script>
</dom-module>
