// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-create-group',

    properties: {
      params: Object,

      _groupConfig: Object,
      _groupCreated: {
        type: Object,
        value: false,
      },
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    attached() {
      this._createGroup();
    },

    _createGroup() {
      this._groupConfig = [];
    },

    _formatGroupConfigForSave(p) {
      const configInputObj = {};
      for (const key in p) {
        if (p.hasOwnProperty(key)) {
          if (typeof p[key] === 'object') {
            configInputObj[key] = p[key].configured_value;
          } else {
            configInputObj[key] = p[key];
          }
        }
      }
      return configInputObj;
    },

    _redirect(groupName) {
      return this.getBaseUrl() + '/admin/groups/' +
          this.encodeURL(groupName, true);
    },

    _handleCreateGroup() {
      const config = this._formatGroupConfigForSave(this._groupConfig);
      return this.$.restAPI.createGroup(config)
          .then(groupRegistered => {
            if (groupRegistered.status === 201) {
              this._groupCreated = true;
              this.$.redirect.click();
            }
          });
    },
  });
})();
