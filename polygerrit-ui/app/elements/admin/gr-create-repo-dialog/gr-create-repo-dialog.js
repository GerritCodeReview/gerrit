/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  /**
    * @appliesMixin Gerrit.BaseUrlMixin
    * @appliesMixin Gerrit.URLEncodingMixin
    */
  class GrCreateRepoDialog extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-create-repo-dialog'; }

    static get properties() {
      return {
        params: Object,
        hasNewRepoName: {
          type: Boolean,
          notify: true,
          value: false,
        },

        /** @type {?} */
        _repoConfig: {
          type: Object,
          value: () => {
          // Set default values for dropdowns.
            return {
              create_empty_commit: true,
              permissions_only: false,
            };
          },
        },
        _repoCreated: {
          type: Boolean,
          value: false,
        },
        _repoOwner: String,
        _repoOwnerId: {
          type: String,
          observer: '_repoOwnerIdUpdate',
        },

        _query: {
          type: Function,
          value() {
            return this._getRepoSuggestions.bind(this);
          },
        },
        _queryGroups: {
          type: Function,
          value() {
            return this._getGroupSuggestions.bind(this);
          },
        },
      };
    }

    static get observers() {
      return [
        '_updateRepoName(_repoConfig.name)',
      ];
    }

    _computeRepoUrl(repoName) {
      return this.getBaseUrl() + '/admin/repos/' +
          this.encodeURL(repoName, true);
    }

    _updateRepoName(name) {
      this.hasNewRepoName = !!name;
    }

    _repoOwnerIdUpdate(id) {
      if (id) {
        this.set('_repoConfig.owners', [id]);
      } else {
        this.set('_repoConfig.owners', undefined);
      }
    }

    handleCreateRepo() {
      return this.$.restAPI.createRepo(this._repoConfig)
          .then(repoRegistered => {
            if (repoRegistered.status === 201) {
              this._repoCreated = true;
              page.show(this._computeRepoUrl(this._repoConfig.name));
            }
          });
    }

    _getRepoSuggestions(input) {
      return this.$.restAPI.getSuggestedProjects(input)
          .then(response => {
            const repos = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              repos.push({
                name: key,
                value: response[key],
              });
            }
            return repos;
          });
    }

    _getGroupSuggestions(input) {
      return this.$.restAPI.getSuggestedGroups(input)
          .then(response => {
            const groups = [];
            for (const key in response) {
              if (!response.hasOwnProperty(key)) { continue; }
              groups.push({
                name: key,
                value: decodeURIComponent(response[key].id),
              });
            }
            return groups;
          });
    }
  }

  customElements.define(GrCreateRepoDialog.is, GrCreateRepoDialog);
})();
