/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
    is: 'gr-repo-notifications',

    properties: {
      repo: {
        type: String,
        observer: '_repoChanged',
      },
      _loading: {
        type: Boolean,
        value: true,
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],

    _repoChanged(repo) {
      this._loading = true;
      if (!repo) { return Promise.resolve(); }

      const errFn = response => {
        this.fire('page-error', {response});
      };

      return this.$.restAPI.getRepoNotifications(this.repo, errFn).then(res => {
        if (!res) { return Promise.resolve(); }
        this._loading = false;
        console.log(res);
        Polymer.dom.flush();
      });
    },

  });
})();
