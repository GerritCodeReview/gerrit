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
  };

  const SUBMIT_TYPES = {
    mergeIfNecessary: {
      value: 'MERGE_IF_NECESSARY',
      label: 'Merge if necessary',
    },
    fastForwardOnly: {
      value: 'FAST_FORWARD_ONLY',
      label: 'Fast forward only',
    },
    rebaseIfNecessary: {
      value: 'REBASE_IF_NECESSARY',
      label: 'Rebase if necessary',
    },
    mergeAlways: {
      value: 'MERGE_ALWAYS',
      label: 'Merge always',
    },
    cherryPick: {
      value: 'CHERRY_PICK',
      label: 'Cherry pick',
    },
  };

  Polymer({
    is: 'gr-admin-project',

    properties: {
      params: Object,

      _configChanged: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _projectConfig: Object,
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

    observers: [
      '_handleConfigChanged(_projectConfig.*)',
    ],


    attached() {
      this._loadProject();
    },

    _loadProject() {
      if (!this.project) { return Promise.resolve(); }

      const promises = [];
      promises.push(this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getProjectAccess(this.project).then(access => {
            // If the user is not an owner, is_owner is not a property.
            this._readOnly = !access[this.project].is_owner;
          });
        }
      }));

      promises.push(this.$.restAPI.getProjectConfig(this.project).then(
          config => {
            this._projectConfig = config;
            if (!this._projectConfig.state) {
              this._projectConfig.state = STATES.active.value;
            }
            this._loading = false;
          }));

      return Promise.all(promises);
    },

    _formatBooleanSelect(item) {
      if (!item) { return; }
      let inheritLabel = 'Inherit';
      if (item.inherited_value) {
        inheritLabel = `Inherit (${item.inherited_value})`;
      }
      return [
        {
          label: inheritLabel,
          value: 'INHERIT',
        },
        {
          label: 'True',
          value: 'TRUE',
        }, {
          label: 'False',
          value: 'FALSE',
        },
      ];
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _formatProjectConfigForSave(p) {
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

    _handleSaveProjectConfig() {
      return this.$.restAPI.saveProjectConfig(this.project,
          this._formatProjectConfigForSave(this._projectConfig)).then(() => {
            this._configChanged = false;
          });
    },

    _handleConfigChanged() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
    },

    _computeButtonDisabled(readOnly, configChanged) {
      return readOnly || !configChanged;
    },

    _computeHeaderClass(configChanged) {
      return configChanged ? 'edited' : '';
    },
  });
})();
