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
import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';

import '../../../../@polymer/iron-input/iron-input.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input:not([type="checkbox"]),
      gr-autocomplete,
      iron-autogrow-textarea {
        width: 100%;
      }
      .value {
        width: 32em;
      }
      section {
        align-items: center;
        display: flex;
      }
      #description {
        align-items: initial;
      }
      gr-autocomplete {
        --gr-autocomplete: {
          padding: 0 .15em;
        }
      }
      .hideBranch {
        display: none;
      }
    </style>
    <div class="gr-form-styles">
      <div id="form">
        <section class\$="[[_computeBranchClass(baseChange)]]">
          <span class="title">Select branch for new change</span>
          <span class="value">
            <gr-autocomplete id="branchInput" text="{{branch}}" query="[[_query]]" placeholder="Destination branch">
            </gr-autocomplete>
          </span>
        </section>
        <section class\$="[[_computeBranchClass(baseChange)]]">
          <span class="title">Provide base commit sha1 for change</span>
          <span class="value">
            <input is="iron-input" id="baseCommitInput" maxlength="40" placeholder="(optional)" bind-value="{{baseCommit}}">
          </span>
        </section>
        <section>
          <span class="title">Enter topic for new change</span>
          <span class="value">
            <input is="iron-input" id="tagNameInput" maxlength="1024" placeholder="(optional)" bind-value="{{topic}}">
          </span>
        </section>
        <section id="description">
          <span class="title">Description</span>
          <span class="value">
            <iron-autogrow-textarea id="messageInput" class="message" autocomplete="on" rows="4" max-rows="15" bind-value="{{subject}}" placeholder="Insert the description of the change.">
            </iron-autogrow-textarea>
          </span>
        </section>
        <section>
          <label class="title" for="privateChangeCheckBox">Private change</label>
          <span class="value">
            <input type="checkbox" id="privateChangeCheckBox" checked\$="[[_formatBooleanString(privateByDefault)]]">
          </span>
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-create-change-dialog',

  properties: {
    repoName: String,
    branch: String,
    /** @type {?} */
    _repoConfig: Object,
    subject: String,
    topic: String,
    _query: {
      type: Function,
      value() {
        return this._getRepoBranchesSuggestions.bind(this);
      },
    },
    baseChange: String,
    baseCommit: String,
    privateByDefault: String,
    canCreate: {
      type: Boolean,
      notify: true,
      value: false,
    },
  },

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  attached() {
    if (!this.repoName) { return; }
    this.$.restAPI.getProjectConfig(this.repoName).then(config => {
      this.privateByDefault = config.private_by_default;
    });
  },

  observers: [
    '_allowCreate(branch, subject)',
  ],

  _computeBranchClass(baseChange) {
    return baseChange ? 'hideBranch' : '';
  },

  _allowCreate(branch, subject) {
    this.canCreate = !!branch && !!subject;
  },

  handleCreateChange() {
    const isPrivate = this.$.privateChangeCheckBox.checked;
    const isWip = true;
    return this.$.restAPI.createChange(this.repoName, this.branch,
        this.subject, this.topic, isPrivate, isWip, this.baseChange,
        this.baseCommit || null)
        .then(changeCreated => {
          if (!changeCreated) { return; }
          Gerrit.Nav.navigateToChange(changeCreated);
        });
  },

  _getRepoBranchesSuggestions(input) {
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.$.restAPI.getRepoBranches(
        input, this.repoName, SUGGESTIONS_LIMIT).then(response => {
          const branches = [];
          let branch;
          for (const key in response) {
            if (!response.hasOwnProperty(key)) { continue; }
            if (response[key].ref.startsWith('refs/heads/')) {
              branch = response[key].ref.substring('refs/heads/'.length);
            } else {
              branch = response[key].ref;
            }
            branches.push({
              name: branch,
            });
          }
          return branches;
        });
  },

  _formatBooleanString(config) {
    if (config && config.configured_value === 'TRUE') {
      return true;
    } else if (config && config.configured_value === 'FALSE') {
      return false;
    } else if (config && config.configured_value === 'INHERIT') {
      if (config && config.inherited_value) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }
});
