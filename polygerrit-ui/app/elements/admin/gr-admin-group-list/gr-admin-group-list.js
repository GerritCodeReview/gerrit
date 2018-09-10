/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-list-view/gr-list-view.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-create-group-dialog/gr-create-group-dialog.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-table-styles"></style>
    <gr-list-view create-new="[[_createNewCapability]]" filter="[[_filter]]" items="[[_groups]]" items-per-page="[[_groupsPerPage]]" loading="[[_loading]]" offset="[[_offset]]" on-create-clicked="_handleCreateClicked" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Group Name</th>
          <th class="description topHeader">Group Description</th>
          <th class="visibleToAll topHeader">Visible To All</th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownGroups]]">
            <tr class="table">
              <td class="name">
                <a href\$="[[_computeGroupUrl(item.group_id)]]">[[item.name]]</a>
              </td>
              <td class="description">[[item.description]]</td>
              <td class="visibleToAll">[[_visibleToAll(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-overlay id="createOverlay" with-backdrop="">
      <gr-dialog id="createDialog" class="confirmDialog" disabled="[[!_hasNewGroupName]]" confirm-label="Create" confirm-on-enter="" on-confirm="_handleCreateGroup" on-cancel="_handleCloseCreate">
        <div class="header" slot="header">
          Create Group
        </div>
        <div class="main" slot="main">
          <gr-create-group-dialog has-new-group-name="{{_hasNewGroupName}}" params="[[params]]" id="createNewModal"></gr-create-group-dialog>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-admin-group-list',

  properties: {
    /**
     * URL params passed from the router.
     */
    params: {
      type: Object,
      observer: '_paramsChanged',
    },

    /**
     * Offset of currently visible query results.
     */
    _offset: Number,
    _path: {
      type: String,
      readOnly: true,
      value: '/admin/groups',
    },
    _hasNewGroupName: Boolean,
    _createNewCapability: {
      type: Boolean,
      value: false,
    },
    _groups: Array,

    /**
     * Because  we request one more than the groupsPerPage, _shownGroups
     * may be one less than _groups.
     * */
    _shownGroups: {
      type: Array,
      computed: 'computeShownItems(_groups)',
    },

    _groupsPerPage: {
      type: Number,
      value: 25,
    },

    _loading: {
      type: Boolean,
      value: true,
    },
    _filter: String,
  },

  behaviors: [
    Gerrit.ListViewBehavior,
  ],

  attached() {
    this._getCreateGroupCapability();
    this.fire('title-change', {title: 'Groups'});
    this._maybeOpenCreateOverlay(this.params);
  },

  _paramsChanged(params) {
    this._loading = true;
    this._filter = this.getFilterValue(params);
    this._offset = this.getOffsetValue(params);

    return this._getGroups(this._filter, this._groupsPerPage,
        this._offset);
  },

  /**
   * Opens the create overlay if the route has a hash 'create'
   * @param {!Object} params
   */
  _maybeOpenCreateOverlay(params) {
    if (params && params.openCreateModal) {
      this.$.createOverlay.open();
    }
  },

  _computeGroupUrl(id) {
    return Gerrit.Nav.getUrlForGroup(id);
  },

  _getCreateGroupCapability() {
    return this.$.restAPI.getAccount().then(account => {
      if (!account) { return; }
      return this.$.restAPI.getAccountCapabilities(['createGroup'])
          .then(capabilities => {
            if (capabilities.createGroup) {
              this._createNewCapability = true;
            }
          });
    });
  },

  _getGroups(filter, groupsPerPage, offset) {
    this._groups = [];
    return this.$.restAPI.getGroups(filter, groupsPerPage, offset)
        .then(groups => {
          if (!groups) {
            return;
          }
          this._groups = Object.keys(groups)
           .map(key => {
             const group = groups[key];
             group.name = key;
             return group;
           });
          this._loading = false;
        });
  },

  _handleCreateGroup() {
    this.$.createNewModal.handleCreateGroup();
  },

  _handleCloseCreate() {
    this.$.createOverlay.close();
  },

  _handleCreateClicked() {
    this.$.createOverlay.open();
  },

  _visibleToAll(item) {
    return item.options.visible_to_all === true ? 'Y' : 'N';
  }
});
