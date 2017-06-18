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
      _name: Object,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _redirect(groupName) {
      return this.getBaseUrl() + '/admin/groups/' +
          this.encodeURL(groupName, true);
    },

    _handleCreateGroup() {
      return this.$.restAPI.createGroup({name: this._name})
          .then(groupRegistered => {
            if (groupRegistered.status === 201) {
              page.show(this._redirect(this._name);
            }
          });
    },
  });
})();
