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
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
      gr-autocomplete {
        border: none;
        --gr-autocomplete: {
          border: 1px solid var(--border-color);
          border-radius: 2px;
          font-size: var(--font-size-normal);
          height: 2em;
          padding: 0 .15em;
          width: 20em;
        }
      }
    </style>

    <div class="gr-form-styles">
      <div id="form">
        <section>
          <span class="title">Repository name</span>
          <input is="iron-input" id="repoNameInput" autocomplete="on" bind-value="{{_repoConfig.name}}">
        </section>
        <section>
          <span class="title">Rights inherit from</span>
          <span class="value">
            <gr-autocomplete id="rightsInheritFromInput" text="{{_repoConfig.parent}}" query="[[_query]]" placeholder="Optional, defaults to 'All-Projects'">
            </gr-autocomplete>
          </span>
        </section>
        <section>
          <span class="title">Create initial empty commit</span>
          <span class="value">
            <gr-select id="initalCommit" bind-value="{{_repoConfig.create_empty_commit}}">
              <select>
                <option value="false">False</option>
                <option value="true">True</option>
              </select>
            </gr-select>
          </span>
        </section>
        <section>
          <span class="title">Only serve as parent for other repositories</span>
          <span class="value">
            <gr-select id="parentRepo" is="gr-select" bind-value="{{_repoConfig.permissions_only}}">
              <select>
                <option value="false">False</option>
                <option value="true">True</option>
              </select>
            </gr-select>
          </span>
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-create-repo-dialog',

  properties: {
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

    _query: {
      type: Function,
      value() {
        return this._getRepoSuggestions.bind(this);
      },
    },
  },

  observers: [
    '_updateRepoName(_repoConfig.name)',
  ],

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  _computeRepoUrl(repoName) {
    return this.getBaseUrl() + '/admin/repos/' +
        this.encodeURL(repoName, true);
  },

  _updateRepoName(name) {
    this.hasNewRepoName = !!name;
  },

  handleCreateRepo() {
    return this.$.restAPI.createRepo(this._repoConfig)
        .then(repoRegistered => {
          if (repoRegistered.status === 201) {
            this._repoCreated = true;
            page.show(this._computeRepoUrl(this._repoConfig.name));
          }
        });
  },

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
});
