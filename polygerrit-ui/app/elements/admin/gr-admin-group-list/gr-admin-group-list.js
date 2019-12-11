/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  /**
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.ListViewMixin
   */
  class GrAdminGroupList extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.ListViewBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-admin-group-list'; }

    static get properties() {
      return {
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
      };
    }

    attached() {
      super.attached();
      this._getCreateGroupCapability();
      this.fire('title-change', {title: 'Groups'});
      this._maybeOpenCreateOverlay(this.params);
    }

    _paramsChanged(params) {
      this._loading = true;
      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      return this._getGroups(this._filter, this._groupsPerPage,
          this._offset);
    }

    /**
     * Opens the create overlay if the route has a hash 'create'
     *
     * @param {!Object} params
     */
    _maybeOpenCreateOverlay(params) {
      if (params && params.openCreateModal) {
        this.$.createOverlay.open();
      }
    }

    _computeGroupUrl(id) {
      return Gerrit.Nav.getUrlForGroup(id);
    }

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
    }

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
    }

    _refreshGroupsList() {
      this.$.restAPI.invalidateGroupsCache();
      return this._getGroups(this._filter, this._groupsPerPage,
          this._offset);
    }

    _handleCreateGroup() {
      this.$.createNewModal.handleCreateGroup().then(() => {
        this._refreshGroupsList();
      });
    }

    _handleCloseCreate() {
      this.$.createOverlay.close();
    }

    _handleCreateClicked() {
      this.$.createOverlay.open();
    }

    _visibleToAll(item) {
      return item.options.visible_to_all === true ? 'Y' : 'N';
    }
  }

  customElements.define(GrAdminGroupList.is, GrAdminGroupList);
})();
