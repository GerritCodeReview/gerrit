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
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../styles/dashboard-header-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="dashboard-header-styles"></style>
    <div class="info">
      <h1 class\$="name">
        [[repo]]
        <hr>
      </h1>
      <div>
        <span>Detail:</span> <a href\$="[[_repoUrl]]">Repo settings</a>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-repo-header',

  properties: {
    /** @type {?String} */
    repo: {
      type: String,
      observer: '_repoChanged',
    },
    /** @type {String|null} */
    _repoUrl: String,
  },

  _repoChanged(repoName) {
    if (!repoName) {
      this._repoUrl = null;
      return;
    }
    this._repoUrl = Gerrit.Nav.getUrlForRepo(repoName);
  }
});
