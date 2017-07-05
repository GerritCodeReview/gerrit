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
    is: 'gr-admin-group',

    properties: {
      params: Object,
      group: String,

      _configChanged: {
        type: Boolean,
        value: false,
      },
      _rename: {
        type: Boolean,
        value: false,
      },
      _description: {
        type: Boolean,
        value: false,
      },
      _owner: {
        type: Boolean,
        value: false,
      },
      _options: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _loggedIn: {
        type: Boolean,
        value: false,
        observer: '_loggedInChanged',
      },
      _groupConfig: Object,
      _groupName: Object,
      _groupOwner: {
        type: Boolean,
        value: false,
      },
      _submitTypes: {
        type: Array,
        value() {
          return Object.values(
              {
                submitFalse: {
                  value: 'false',
                  label: 'False',
                },
                submitTrue: {
                  value: 'true',
                  label: 'True',
                },
              }
          );
        },
      },
      _query: {
        type: Function,
        value() {
          return this._getGroupSuggestions.bind(this);
        },
      },
    },

    observers: [
      '_handleConfigName(_groupConfig.name)',
      '_handleConfigOwner(_groupConfig.owner)',
      '_handleConfigDescription(_groupConfig.description)',
      '_handleConfigOptions(_groupConfig.options.visible_to_all)',
    ],

    attached() {
      this._loadGroup();
    },

    _loadGroup() {
      if (!this.group) { return; }

      this.$.restAPI.getGroupConfig(this.group).then(
          config => {
            this._groupConfig = config;
            this._groupName = config.name;
            this._loading = false;
            this.$.restAPI.getIsGroupOwner(config.name).then(
                configs => {
                  if (Object.keys(configs).length === 0 &&
                      configs.constructor === Object) {
                    this._groupOwner = true;
                  }
                });
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _loggedInChanged(_loggedIn) {
      if (!_loggedIn) { return; }
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
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

    _handleSaveName() {
      return this.$.restAPI.saveGroupConfig('name', this.group,
          this._groupConfig.name).then(config => {
            if (config.status === 200) {
              page('/admin/groups/' + this.group);
            }
            this._configChanged = false;
            this._rename = false;
          });
    },

    _handleSaveOwner() {
      return this.$.restAPI.saveGroupConfig('owner', this.group,
          this._groupConfig.owner).then(config => {
            if (config.status === 200) {
              page('/admin/groups/' + this.group);
            }
            this._configChanged = false;
            this._owner = false;
          });
    },

    _handleSaveDescription() {
      return this.$.restAPI.saveGroupConfig('description', this.group,
          this._groupConfig.description).then(config => {
            if (config.status === 200) {
              page('/admin/groups/' + this.group);
            }
            this._configChanged = false;
            this._description = false;
          });
    },

    _handleSaveOptions() {
      return this.$.restAPI.saveGroupConfig('options', this.group,
          this._groupConfig.options).then(config => {
            if (config.status === 200) {
              page('/admin/groups/' + this.group);
            }
            this._configChanged = false;
            this._options = false;
          });
    },

    _handleConfigName() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
      this._rename = true;
    },

    _handleConfigOwner() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
      this._owner = true;
    },

    _handleConfigDescription() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
      this._description = true;
    },

    _handleConfigOptions() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
      this._options = true;
    },

    _computeButtonDisabled(options, option) {
      return options || !option;
    },

    _computeHeaderClass(configChanged) {
      return configChanged ? 'edited' : '';
    },

    _getGroupSuggestions(input) {
      return this.$.restAPI.getSuggestedGroups(input)
          .then(response => {
            const groups = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              groups.push({
                name: key,
                value: response[key],
              });
            }
            return groups;
          });
    },
  });
})();
