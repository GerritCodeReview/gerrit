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

  const OPTIONS = {
    submitFalse: {
      value: false,
      label: 'False',
    },
    submitTrue: {
      value: true,
      label: 'True',
    },
  };

  Polymer({
    is: 'gr-group',

    /**
     * Fired when the group name changes.
     *
     * @event name-changed
     */

    properties: {
      groupId: Number,
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
      /** @type {?} */
      _groupConfig: Object,
      _groupName: Object,
      _groupOwner: {
        type: Boolean,
        value: false,
      },
      _submitTypes: {
        type: Array,
        value() {
          return Object.values(OPTIONS);
        },
      },
      _query: {
        type: Function,
        value() {
          return this._getGroupSuggestions.bind(this);
        },
      },
      _isAdmin: {
        type: Boolean,
        value: false,
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

    _handleGetChangeDetailError(response) {
      this.fire('page-error-admin', {response});
    },

    _loadGroup() {
      if (!this.groupId) { return; }

      const promises = [];

      return this.$.restAPI.getGroupConfig(
        this.groupId, this._handleGetChangeDetailError.bind(this)).then(
          config => {
            if (!config || !config.name) { return Promise.resolve(); }

            this._groupName = config.name;

            promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
              this._isAdmin = isAdmin ? true : false;
            }));

            promises.push(this.$.restAPI.getIsGroupOwner(config.name)
                .then(isOwner => {
                  this._groupOwner = isOwner ? true : false;
                }));

            // If visible to all is undefined, set to false. If it is defined
            // as false, setting to false is fine. If any optional values
            // are added with a default of true, then this would need to be an
            // undefined check and not a truthy/falsy check.
            if (!config.options.visible_to_all) {
              config.options.visible_to_all = false;
            }
            this._groupConfig = config;

            this.fire('title-change', {title: config.name});

            return Promise.all(promises).then(() => {
              this._loading = false;
            });
          });
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _isLoading() {
      return this._loading || this._loading === undefined;
    },

    _handleSaveName() {
      return this.$.restAPI.saveGroupName(this.groupId, this._groupConfig.name)
          .then(config => {
            if (config.status === 200) {
              this._groupName = this._groupConfig.name;
              this.fire('name-changed', {name: this._groupConfig.name});
              this._rename = false;
            }
          });
    },

    _handleSaveOwner() {
      return this.$.restAPI.saveGroupOwner(this.groupId,
          this._groupConfig.owner).then(config => {
            this._owner = false;
          });
    },

    _handleSaveDescription() {
      return this.$.restAPI.saveGroupDescription(this.groupId,
          this._groupConfig.description).then(config => {
            this._description = false;
          });
    },

    _handleSaveOptions() {
      let options;
      // The value is in string so we have to convert it to a boolean.
      if (this._groupConfig.options.visible_to_all) {
        options = {visible_to_all: true};
      } else if (!this._groupConfig.options.visible_to_all) {
        options = {visible_to_all: false};
      }
      return this.$.restAPI.saveGroupOptions(this.groupId,
          options).then(config => {
            this._options = false;
          });
    },

    _handleConfigName() {
      if (this._isLoading()) { return; }
      this._rename = true;
    },

    _handleConfigOwner() {
      if (this._isLoading()) { return; }
      this._owner = true;
    },

    _handleConfigDescription() {
      if (this._isLoading()) { return; }
      this._description = true;
    },

    _handleConfigOptions() {
      if (this._isLoading()) { return; }
      this._options = true;
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

    _computeGroupDisabled(owner, admin) {
      return admin || owner ? false : true;
    },
  });
})();
