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

  const STATES = {
    active: {value: 'ACTIVE', label: 'Active'},
    readOnly: {value: 'READ_ONLY', label: 'Read Only'},
    hidden: {value: 'HIDDEN', label: 'Hidden'},
  }

  const SUBMIT_TYPES = {
    mergeIfNecessary: {value: 'MERGE_IF_NECESSARY', label: 'Merge if necessary'},
    fastForwardOnly: {value: 'FAST_FORWARD_ONLY', label: 'Fast forward only'},
    rebaseIfNecessary: {value: 'REBASE_IF_NECESSARY', label: 'Rebase if necessary'},
    mergeAlways: {value: 'MERGE_ALWAYS', label: 'Merge always'},
    cherryPick: {value: 'CHERRY_PICK', label: 'Cherry pick'},
  }

  Polymer({
    is: 'gr-admin-project',

    properties: {
      params: Object,

      _loading: {
        type: Boolean,
        value: true,
      },
      _projectConfig: {
        type: Object,
        value() { return {}; },
      },
      _readOnly: {
        type: Boolean,
        value: true,
      },
      _states: {
        type: Array,
        value() {
          return Object.values(STATES);
        },
      },
      _submitTypes: {
        type: Array,
        value() {
          return Object.values(SUBMIT_TYPES);
        },
      },
    },

    attached() {
      this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn && this.params.project) {

          this.$.restAPI.getProjectConfig(this.params.project).then(
              (config) => {
            this._projectConfig = config;
            // if (!this._projectConfig.state) {
            //   this._projectConfig.state = STATES.ACTIVE_STATE;
            // }
            this._loading = false;
          });

          this.$.restAPI.getProjectAccess(this.params.project).then((access) => {
            // If the user is not an owner, is_owner is not a property.
            this._readOnly = !access[this.params.project].is_owner;
          });
        }
      });
    },

    _formatBooleanSelect(item) {
      if (!item) { return; }
      return [
        {
          label: `Inherit (${item.inherited_value})`,
          value: 'INHERIT',
        },
        {
          label: 'True',
          value: 'TRUE',
        }, {
          label: 'False',
          value: 'FALSE',
        }
      ];
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _formatProjectConfigForSave(p) {
      let configInputObj = {};
      for (const key in p) {
        if (p.hasOwnProperty(key)) {
          if (typeof p[key] === 'object') {
            configInputObj[key] = p[key].configured_value
          } else {
            configInputObj[key] = p[key]
          }
        }
      }
      return configInputObj;
    },

    _handleSaveProjectConfig() {
      this.$.restAPI.saveProjectConfig(this.params.project,
          this._formatProjectConfigForSave(this._projectConfig));
    },
  });
})();
