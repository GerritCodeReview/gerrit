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
      _groupOwner: {
        type: Boolean,
        value: false,
      },
      _readOnly: {
        type: Boolean,
        value: true,
      },
    },

    observers: [
      '_handleConfigChanged(_groupConfig.*)',
    ],

    attached() {
      this._loadGroup();
    },

    _loadGroup() {
      if (!this.group) { return Promise.resolve(); }

      const promises = [];

      promises.push(this.$.restAPI.getGroupConfig(this.group).then(
          config => {
            this._groupConfig = config;
            this._loading = false;
            this.$.restAPI.checkGroupForIfYourTheOwner(config.name).then(
                configs => {
                  console.log(configs);
                  if (Object.keys(configs).length === 0 &&
                      configs.constructor === Object) {
                    this._groupOwner = true;
                  }
                });
          }));

      return Promise.all(promises);
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

    _handleRenameGroupName() {
      return this.$.restAPI.saveGroupConfig('name', this.group,
          this._groupConfig.name).then(config => {
            if (config.status === 200) {
              page.reload();
            }
            this._rename = false;
          });
    },

    _handleSaveGroupConfig() {
      return this.$.restAPI.saveGroupConfig(this.group,
          this._formatGroupConfigForSave(this._groupConfig.options))
            .then(() => {
              this._configChanged = false;
            });
    },

    _handleConfigChanged() {
      if (this._isLoading()) { return; }
      this._configChanged = true;
      this._rename = true;
    },

    _computeButtonDisabled(options, option) {
      return options || !option;
    },

    _computeHeaderClass(configChanged) {
      return configChanged ? 'edited' : '';
    },
  });
})();
