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
    is: 'gr-project-lookup',

    properties: {
      _lookup: {
        type: Object,
        value: {}, // Intentional to share the object across instances.
      },
    },

    /**
     * @param {string|number} changeNum
     * @param {string} project
     */
    set(changeNum, project) {
      if (this._lookup[changeNum] && this._lookup[changeNum] !== project) {
        console.warn('Change set with multiple project nums.' +
            'One of them must be invalid.');
      }
      this._lookup[changeNum] = project;
    },

    /**
     * @param {string|number} changeNum
     * @return {Promise<string>}
     */
    get(changeNum) {
      const project = this._lookup[changeNum];
      return project ? Promise.resolve(project) : this._getProject(changeNum);
    },

    /**
     * Calls the restAPI to get the change, populates _lookup with the project
     * for that change, and returns the project.
     *
     * @param {string|number} changeNum
     * @return {Promise<string|undefined>}
     */
    _getProject(changeNum) {
      return this.$.restAPI.getChange(changeNum).then(change => {
        if (!change || !change.project) { return; }
        this.set(changeNum, change.project);
        return change.project;
      });
    },
  });
})();