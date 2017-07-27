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

  const TOAST_DEBOUNCE_INTERVAL = 200;

  Polymer({
    is: 'gr-cla-view',

    properties: {
      _groups: Object,
      /** @type {?} */
      _serverConfig: Object,
      _agreementsText: String,
      _agreementName: String,
      _showAgreements: {
        type: Boolean,
        value: false,
      },
      _agreementsUrl: String,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    attached() {
      this.loadData();
    },

    loadData() {
      const promises = [];
      promises.push(this.$.restAPI.getConfig(true).then(config => {
        this._serverConfig = config;
      }));

      promises.push(this.$.restAPI.getAccountGroups(true).then(groups => {
        this._groups = groups.sort((a, b) => {
          return a.name.localeCompare(b.name);
        });
      }));

      return Promise.all(promises);
    },

    _getAgreementsUrl(configUrl) {
      let url;
      if (!configUrl) { return ''; }
      if (configUrl.startsWith('http:') || configUrl.startsWith('https:')) {
        url = configUrl;
      } else {
        url = this.getBaseUrl() + '/' + configUrl;
      }

      return url;
    },

    _handleShowAgreement(e) {
      this._agreementName = e.target.getAttribute('data-name');
      const url = e.target.getAttribute('data-url');
      this._agreementsUrl = this._getAgreementsUrl(url);
      this._showAgreements = true;
    },

    _handleSaveAgreements(e) {
      const name = this._agreementName;
      return this.$.restAPI.saveAccountAgreement({name}).then(res => {
        let message;
        if (res.status === 200) {
          message = 'Agreement has been successfully submited.';
        } else {
          message = 'Agreement failed to be submitted, please try again';
        }
        this.debounce('agreements-toast', () => {
          document.body.dispatchEvent(
              new CustomEvent(
                'show-alert', {detail: {message}, bubbles: true}));
        }, TOAST_DEBOUNCE_INTERVAL);
        this.loadData();
        this._agreementsText = '';
        this._showAgreements = false;
      });
    },

    _computeShowAgreementsClass(agreements) {
      if (agreements) {
        return 'show';
      }

      return '';
    },

    _disableAggreements(item, groups) {
      for (const value of groups) {
        if (item && item.auto_verify_group &&
            item.auto_verify_group.name === value.name) {
          return true;
        }
      }

      return false;
    },

    _hideAggreements(item, groups) {
      if (this._disableAggreements(item, groups)) {
        return '';
      } else {
        return 'agreementsSubmitted';
      }
    },

    _disableAgreementsText(text) {
      if (text.toLowerCase() === 'i agree') {
        return false;
      }

      return true;
    },

    // what this does is it loops through to find auto_verify_group
    // if specified, it means that you signed the agreement.
    _computeHideAgreementClass(name, config) {
      for (const key in config) {
        if (!config.hasOwnProperty(key)) { return; }
        const groupObject = config[key];
        for (const prop in groupObject) {
          if (!groupObject.hasOwnProperty(prop)) { return; }
          if (name === config[key].name &&
              !config[key].auto_verify_group) {
            return 'hideAgreementsTextBox';
          }
        }
      }
    },
  });
})();
