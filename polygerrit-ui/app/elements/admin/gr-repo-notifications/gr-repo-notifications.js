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
      _editing: {
        type: Boolean,
        value: false,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _notifications: Array,
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _computeMainClass(editing) {
      return editing ? 'editing' : '';
    },

    _repoChanged(repo) {
      this._loading = true;
      return this._reload(repo);
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    _reload(repo) {
      if (!repo) {
        return Promise.resolve();
      }

      const errFn = response => {
        this.fire('page-error', {response});
      };

      return this.$.restAPI.notifications
          .getRepoNotifications(this.repo, errFn)
          .then(res => {
            this._loading = false;
            if (!res) {
              this._notifications = [];
              return Promise.resolve();
            }
            this._notifications = this._getNotifications(res.notify_configs);
          });
    },

    _getNotifications(notifyConfig) {
      return [{name: 'abc', filter: 'sadfsadfsdafsf', header: 'BCC', emails: ['abc@def'], groups: ['group1', 'group2']}  ];
    },

    _handleCreateNotification() {
      this.push('_notifications', {name: 'abc', filter: 'asdfsafsfd', header: 'BCC', emails: ['abc@def'], groups: ['group1', 'group2']});
      Polymer.dom.flush();
    },
  });
})();
