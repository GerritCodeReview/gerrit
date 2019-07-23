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

  const reposPerPage = 6;

  Polymer({
    is: 'gr-create-checks-dialog',
    _legacyUndefinedCheck: true,

    properties: {
      params: Object,
      _name: String,
      _scheme: String,
      _id: String,
      _uuid: {
        type: String,
        value: ""
      },
      _repository: String,
      _description: String,
      query: {
        type: Function,
        value() {
          return this._getRepoSuggestions.bind(this);
        }
      },
      repos: {
        type: Array,
        value: []
      }
    },

    observers: [
      '_updateUUID(_scheme, _id)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _updateUUID(_scheme, _id) {
      this._uuid = _scheme + ":" + _id;
    },

    _handleCreateChecker() {
      console.log("create checker");
      this.$.restAPI.createChecker({
        "name" : this._name,
        "description" : this._description,
        "uuid" : this._uuid,
        "repository": this.repos[0].name 
      }).then(
        res => {
          console.log(res);
        }
      )
    },

    _makeSuggestion(repo) {
      return {
        name: repo.name,
        value: repo
      }
    },

    _getRepoSuggestions(filter) {
      console.log(input);
      return this.$.restAPI.getRepos(filter, reposPerPage).then(
        (repos) => {
          console.log(repos);
          return repos.map
            (
              (repo) => {
                return this._makeSuggestion(repo)
              }
            )
        }
      )
    },

    _handleInputCommit(e) {
      console.log(e);
      this.repos.push(e.detail.value);
    },

  });
})();
