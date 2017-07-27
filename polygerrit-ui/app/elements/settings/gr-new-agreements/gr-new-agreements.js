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
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    attached() {
      this.loadData();
    },

    loadData() {
      this.$.restAPI.getConfig().then(config => {
        this._serverConfig = config;
      });

      return this.$.restAPI.getAccountGroups().then(groups => {
        this._groups = groups.sort((a, b) => {
          return a.name.localeCompare(b.name);
        });
      });
    },

    _getAgreementsUrl(url) {
      if (url.startsWith("http:") || url.startsWith("https:")) {
        url = url;
      } else {
        url = '/' + url;
      }
      this.$.restAPI.getAgreementsFromHtml(url)
          .then(url => {
            window.document.getElementById('agreementsUrl').innerHTML = url;
            return window;
          });
    },

    _handleAgreements(e) {
      this._agreementName = e.target.getAttribute('data-name');
      const url = e.target.getAttribute('data-url');
      if (!url) {
        return '';
      }
      this._getAgreementsUrl(url);
      window.document.getElementById('claNewAgreements2')
          .style.display = 'block';
    },

    _handleSaveAgreements(e) {
      if (this._agreementsText === 'I AGREE') {
        const name = this._agreementName;
        return this.$.restAPI.saveAccountAgreement({name}).then(ok => {
          if (ok.status === 200) {
            window.location.reload();
          }
        });
      }
    },

    _disableAggreements(item, groups) {
      for (let i = 0; i < groups.length; i++) {
        if (item.auto_verify_group.name === groups[i].name) {
          return true;
        }
      }

      return false;
    },
  });
})();
