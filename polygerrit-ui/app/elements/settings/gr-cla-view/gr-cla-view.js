/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-cla-view',

    properties: {
      _groups: Object,
      /** @type {?} */
      _serverConfig: Object,
      _agreementsText: String,
      _agreementName: String,
      _signedAgreements: Array,
      _showAgreements: {
        type: Boolean,
        value: false,
      },
      _agreementsUrl: String,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.FireBehavior,
    ],

    attached() {
      this.loadData();

      this.fire('title-change', {title: 'New Contributor Agreement'});
    },

    loadData() {
      const promises = [];
      promises.push(this.$.restAPI.getConfig(true).then(config => {
        this._serverConfig = config;
      }));

      promises.push(this.$.restAPI.getAccountGroups().then(groups => {
        this._groups = groups.sort((a, b) => {
          return a.name.localeCompare(b.name);
        });
      }));

      promises.push(this.$.restAPI.getAccountAgreements().then(agreements => {
        this._signedAgreements = agreements || [];
      }));

      return Promise.all(promises);
    },

    _getAgreementsUrl(configUrl) {
      let url;
      if (!configUrl) {
        return '';
      }
      if (configUrl.startsWith('http:') || configUrl.startsWith('https:')) {
        url = configUrl;
      } else {
        url = this.getBaseUrl() + '/' + configUrl;
      }

      return url;
    },

    _handleShowAgreement(e) {
      this._agreementName = e.target.getAttribute('data-name');
      this._agreementsUrl =
          this._getAgreementsUrl(e.target.getAttribute('data-url'));
      this._showAgreements = true;
    },

    _handleSaveAgreements(e) {
      this._createToast('Agreement saving...');

      const name = this._agreementName;
      return this.$.restAPI.saveAccountAgreement({name}).then(res => {
        let message = 'Agreement failed to be submitted, please try again';
        if (res.status === 200) {
          message = 'Agreement has been successfully submited.';
        }
        this._createToast(message);
        this.loadData();
        this._agreementsText = '';
        this._showAgreements = false;
      });
    },

    _createToast(message) {
      this.dispatchEvent(new CustomEvent(
          'show-alert', {detail: {message}, bubbles: true, composed: true}));
    },

    _computeShowAgreementsClass(agreements) {
      return agreements ? 'show' : '';
    },

    _disableAggreements(item, groups, signedAgreements) {
      for (const group of groups) {
        if ((item && item.auto_verify_group &&
            item.auto_verify_group.id === group.id) ||
            signedAgreements.find(i => i.name === item.name)) {
          return true;
        }
      }
      return false;
    },

    _hideAggreements(item, groups, signedAgreements) {
      return this._disableAggreements(item, groups, signedAgreements) ?
          '' : 'hide';
    },

    _disableAgreementsText(text) {
      return text.toLowerCase() === 'i agree' ? false : true;
    },

    // This checks for auto_verify_group,
    // if specified it returns 'hideAgreementsTextBox' which
    // then hides the text box and submit button.
    _computeHideAgreementClass(name, config) {
      for (const key in config) {
        if (!config.hasOwnProperty(key)) {
          continue;
        }
        for (const prop in config[key]) {
          if (!config[key].hasOwnProperty(prop)) {
            continue;
          }
          if (name === config[key].name &&
              !config[key].auto_verify_group) {
            return 'hideAgreementsTextBox';
          }
        }
      }
    },
  });
})();
