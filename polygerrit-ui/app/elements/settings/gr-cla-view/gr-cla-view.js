/**
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../../@polymer/iron-input/iron-input.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      h1 {
        margin-bottom: .6em;
      }
      h3 {
        margin-bottom: .5em;
      }
      .agreementsUrl {
        border: 0.1em solid #b0bdcc;
        margin-bottom: 1.25em;
        margin-left: 1.25em;
        margin-right: 1.25em;
        padding: 0.3em;
      }
      #claNewAgreementsLabel {
        font-family: var(--font-family-bold);
      }
      #claNewAgreement {
        display: none;
      }
      #claNewAgreement.show {
        display: block;
      }
      .contributorAgreementButton {
        font-family: var(--font-family-bold);
      }
      .alreadySubmittedText {
        color: var(--error-text-color);
        margin: 0 2em;
        padding: .5em;
      }
      .alreadySubmittedText.hide,
      .hideAgreementsTextBox {
        display: none;
      }
      main {
        margin: 2em auto;
        max-width: 50em;
      }
    </style>
    <style include="gr-form-styles"></style>
    <main>
      <h1>New Contributor Agreement</h1>
      <h3>Select an agreement type:</h3>
      <template is="dom-repeat" items="[[_serverConfig.auth.contributor_agreements]]">
        <span class="contributorAgreementButton">
          <input id\$="claNewAgreementsInput[[item.name]]" name="claNewAgreementsRadio" type="radio" data-name\$="[[item.name]]" data-url\$="[[item.url]]" on-tap="_handleShowAgreement" disabled\$="[[_disableAggreements(item, _groups, _signedAgreements)]]">
          <label id="claNewAgreementsLabel">[[item.name]]</label>
        </span>
        <div class\$="alreadySubmittedText [[_hideAggreements(item, _groups, _signedAgreements)]]">
          Agreement already submitted.
        </div>
        <div class="agreementsUrl">
          [[item.description]]
        </div>
      </template>
      <div id="claNewAgreement" class\$="[[_computeShowAgreementsClass(_showAgreements)]]">
        <h3 class="smallHeading">Review the agreement:</h3>
        <div id="agreementsUrl" class="agreementsUrl">
          <a href\$="[[_agreementsUrl]]" target="blank" rel="noopener">
            Please review the agreement.</a>
        </div>
        <div class\$="agreementsTextBox [[_computeHideAgreementClass(_agreementName, _serverConfig.auth.contributor_agreements)]]">
          <h3 class="smallHeading">Complete the agreement:</h3>
          <input id="input-agreements" is="iron-input" bind-value="{{_agreementsText}}" placeholder="Enter 'I agree' here">
          <gr-button on-tap="_handleSaveAgreements" disabled="[[_disableAgreementsText(_agreementsText)]]">
            Submit
          </gr-button>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

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
    this.dispatchEvent(new CustomEvent('show-alert',
        {detail: {message}, bubbles: true}));
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
      if (!config.hasOwnProperty(key)) { continue; }
      for (const prop in config[key]) {
        if (!config[key].hasOwnProperty(prop)) { continue; }
        if (name === config[key].name &&
            !config[key].auto_verify_group) {
          return 'hideAgreementsTextBox';
        }
      }
    }
  }
});
