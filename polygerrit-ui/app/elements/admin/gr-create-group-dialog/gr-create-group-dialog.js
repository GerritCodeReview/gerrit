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
    * @appliesMixin Gerrit.BaseUrlMixin
    * @appliesMixin Gerrit.URLEncodingMixin
    */
  class GrCreateGroupDialog extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-create-group-dialog'; }

    static get properties() {
      return {
        params: Object,
        hasNewGroupName: {
          type: Boolean,
          notify: true,
          value: false,
        },
        _name: Object,
        _groupCreated: {
          type: Boolean,
          value: false,
        },
      };
    }

    static get observers() {
      return [
        '_updateGroupName(_name)',
      ];
    }

    _computeGroupUrl(groupId) {
      return this.getBaseUrl() + '/admin/groups/' +
          this.encodeURL(groupId, true);
    }

    _updateGroupName(name) {
      this.hasNewGroupName = !!name;
    }

    handleCreateGroup() {
      return this.$.restAPI.createGroup({name: this._name})
          .then(groupRegistered => {
            if (groupRegistered.status !== 201) { return; }
            this._groupCreated = true;
            return this.$.restAPI.getGroupConfig(this._name)
                .then(group => {
                  page.show(this._computeGroupUrl(group.group_id));
                });
          });
    }
  }

  customElements.define(GrCreateGroupDialog.is, GrCreateGroupDialog);
})();
