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
    is: 'gr-admin-project-list',

    properties: {
      _project: Array,
    },

    attached: function() {
      this.loadData();
    },

    loadData: function() {
      return this.$.restAPI.getProjects().then(function(projects) {
        if (!projects) {
          return [];
        }
        var keys = Object.keys(projects);
        this._project = keys.map(function(key) { return key; });
      }.bind(this));
    };
})();
