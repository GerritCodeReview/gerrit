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
    is: 'gr-project-access',

    properties: {
      project: {
        type: String,
        observer: '_projectChanged',
      },

      _capabilities: Object,
      /** @type {?} */
      _inheritsFrom: Object,
      _labels: Object,
      _local: Object,
      _editing: {
        type: Boolean,
        value: false,
      },
      _sections: Array,
    },

    behaviors: [
      Gerrit.AccessBehavior,
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _projectChanged(project) {
      const promises = [];
      promises.push(this.$.restAPI.getProjectAccessRights(project).then(res => {
        this._inheritsFrom = res.inherits_from;
        this._local = res.local;
        this._sections = this.toSortedArray(this._local);
      }));

      promises.push(this.$.restAPI.getCapabilities().then(res => {
        this._capabilities = res;
      }));

      promises.push(this.$.restAPI.getProject(project).then(res => {
        this._labels = res.labels;
      }));

      return Promise.all(promises);
    },

    _computeParentHref(projectName) {
      return this.getBaseUrl() +
          `/admin/projects/${this.encodeURL(projectName, true)},access`;
    },
  });
})();