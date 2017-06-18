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
      /*promises.push(this._getLoggedIn().then(loggedIn => {
        this._loggedIn = loggedIn;
        if (loggedIn) {
          this.$.restAPI.getGroupAccess(this.group).then(access => {
            // If the user is not an owner, is_owner is not a property.
            this._readOnly = !access[this.group].is_owner;
          });
        }
      }));*/

      promises.push(this.$.restAPI.getGroupConfig(this.group).then(
          config => {
            this._groupConfig = config;
            this._loading = false;
          }));

      return Promise.all(promises);
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _computeDownloadClass(schemes) {
      return !schemes || !schemes.length ? 'hideDownload' : '';
    },

    _loggedInChanged(_loggedIn) {
      if (!_loggedIn) { return; }
      this.$.restAPI.getPreferences().then(prefs => {
        if (prefs.download_scheme) {
          // Note (issue 5180): normalize the download scheme with lower-case.
          this._selectedScheme = prefs.download_scheme.toLowerCase();
        }
      });
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

    _handleSaveGroupConfig() {
      return this.$.restAPI.saveGroupConfig(this.group,
          this._formatGroupConfigForSave(this._groupConfig.options)).then(() => {
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
