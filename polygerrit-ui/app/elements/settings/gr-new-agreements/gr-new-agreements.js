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
    is: 'gr-new-agreements',

    properties: {
      _groups: Object,
      /** @type {?} */
      _serverConfig: Object,
      _agreementsText: String,
      _agreementName: String,
      _showAgreements: {
        type: String,
        value: '',
      },
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

    _getAgreementsUrl(url) {
      let urls;
      if (!url) {
        return;
      }
      if (url.startsWith('http:') || url.startsWith('https:')) {
        urls = url;
      } else {
        urls = '/' + url;
      }
      return this.$.restAPI.getAgreementsFromHtml(urls)
          .then(url => {
            return window.document.getElementById('agreementsUrl')
                .innerHTML = url;
          });
    },

    _handleAgreements(e) {
      this._agreementName = e.target.getAttribute('data-name');
      const url = e.target.getAttribute('data-url');
      this._getAgreementsUrl(url);
      this._showAgreements = 'show';
    },

    _handleSaveAgreements(e) {
      const name = this._agreementName;
      return this.$.restAPI.saveAccountAgreement({name}).then(ok => {
        if (ok.status === 200) {
          this.loadData();
          this._agreementsText = '';
          this._showAgreements = '';
        }
      });
    },

    _disableAggreements(item, groups) {
      for (let i = 0; i < groups.length; i++) {
        if (item && item.auto_verify_group &&
            item.auto_verify_group.name === groups[i].name) {
          return true;
        }
      }

      return false;
    },

    _disableAgreementsText(text) {
      if (text === 'I AGREE') {
        return false;
      }

      return true;
    },

    _disableAgreementsTextBox(name, config) {
      for (let key in config) {
        if (config.hasOwnProperty(key)) {
          let obj = config[key];
          for (let prop in obj) {
            if (obj.hasOwnProperty(prop)) {
              if (name === config[key].name &&
                  !config[key].auto_verify_group) {
                return true;
              }
            }
          }
        }
      }
    },
  });
})();
