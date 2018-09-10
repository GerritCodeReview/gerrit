/**
@license
Copyright (C) 2017 The Android Open Source Project

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

import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      #agreements .nameColumn {
        min-width: 15em;
        width: auto;
      }
      #agreements .descriptionColumn {
        width: auto;
      }
    </style>
    <style include="gr-form-styles"></style>
    <div class="gr-form-styles">
      <table id="agreements">
        <thead>
          <tr>
            <th class="nameColumn">Name</th>
            <th class="descriptionColumn">Description</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_agreements]]">
            <tr>
              <td class="nameColumn">
                <a href\$="[[getUrlBase(item.url)]]" rel="external">
                  [[item.name]]
                </a>
              </td>
              <td class="descriptionColumn">[[item.description]]</td>
            </tr>
          </template>
        </tbody>
      </table>
      <a href\$="[[getUrl()]]">New Contributor Agreement</a>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-agreements-list',

  properties: {
    _agreements: Array,
  },

  behaviors: [
    Gerrit.BaseUrlBehavior,
  ],

  attached() {
    this.loadData();
  },

  loadData() {
    return this.$.restAPI.getAccountAgreements().then(agreements => {
      this._agreements = agreements;
    });
  },

  getUrl() {
    return this.getBaseUrl() + '/settings/new-agreement';
  },

  getUrlBase(item) {
    return this.getBaseUrl() + '/' + item;
  }
});
