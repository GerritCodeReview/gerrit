// Copyright (C) 2016 The Android Open Source Project
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
    is: 'gr-settings-view',

    properties: {
      account: {
        type: Object,
        value: function() { return {}; },
      },
      prefs: {
        type: Object,
        value: function() { return {}; },
      },
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    attached: function() {
      var promises = [];

      promises.push(this.$.restAPI.getAccount().then(function(account) {
        this.account = account;
      }.bind(this)));

      promises.push(this.$.restAPI.getPreferences().then(function(prefs) {
        this.prefs = prefs;
      }.bind(this)));

      Promise.all(promises).then(function() {
        this._loading = false;
      }.bind(this));
    },

    _computeRegistered: function(registered) {
      if (!registered) { return ''; }
      return util.parseDate(registered).toGMTString();
    },
  });
})();
