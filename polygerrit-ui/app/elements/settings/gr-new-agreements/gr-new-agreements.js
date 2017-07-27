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
      _serverConfig: Object,
      _agreementsUrl: Object,
      _hideCla: String,
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
    },

    _getAgreementsUrl(url) {
      if (!url) {
        return '';
      }
      this.$.restAPI.getAgreementsFromHtml('/' + url)
          .then(url => {
            window.document.getElementById('agreementsUrl').innerHTML = url;
            return window;
          });
    },

    _generateRandomNumber() {
      return new Date().getTime();
    },

    _handleAgreements() {
      this._hideCla = 'block';
    },
  });
})();
