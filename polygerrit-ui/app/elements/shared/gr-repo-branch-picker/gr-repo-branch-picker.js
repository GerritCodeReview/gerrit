/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../../scripts/bundled-polymer.js';

import '@polymer/iron-icon/iron-icon.js';
import '../../../styles/shared-styles.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../gr-icons/gr-icons.js';
import '../gr-labeled-autocomplete/gr-labeled-autocomplete.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-repo-branch-picker_html.js';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

/**
 * @appliesMixin Gerrit.URLEncodingMixin
 * @extends Polymer.Element
 */
class GrRepoBranchPicker extends mixinBehaviors( [
  Gerrit.URLEncodingBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-repo-branch-picker'; }

  static get properties() {
    return {
      repo: {
        type: String,
        notify: true,
        observer: '_repoChanged',
      },
      branch: {
        type: String,
        notify: true,
      },
      _branchDisabled: Boolean,
      _query: {
        type: Function,
        value() {
          return this._getRepoBranchesSuggestions.bind(this);
        },
      },
      _repoQuery: {
        type: Function,
        value() {
          return this._getRepoSuggestions.bind(this);
        },
      },
    };
  }

  /** @override */
  attached() {
    super.attached();
    if (this.repo) {
      this.$.repoInput.setText(this.repo);
    }
  }

  /** @override */
  ready() {
    super.ready();
    this._branchDisabled = !this.repo;
  }

  _getRepoBranchesSuggestions(input) {
    if (!this.repo) { return Promise.resolve([]); }
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.$.restAPI.getRepoBranches(input, this.repo, SUGGESTIONS_LIMIT)
        .then(this._branchResponseToSuggestions.bind(this));
  }

  _getRepoSuggestions(input) {
    return this.$.restAPI.getRepos(input, SUGGESTIONS_LIMIT)
        .then(this._repoResponseToSuggestions.bind(this));
  }

  _repoResponseToSuggestions(res) {
    return res.map(repo => {
      return {
        name: repo.name,
        value: this.singleDecodeURL(repo.id),
      };
    });
  }

  _branchResponseToSuggestions(res) {
    return Object.keys(res).map(key => {
      let branch = res[key].ref;
      if (branch.startsWith(REF_PREFIX)) {
        branch = branch.substring(REF_PREFIX.length);
      }
      return {name: branch, value: branch};
    });
  }

  _repoCommitted(e) {
    this.repo = e.detail.value;
  }

  _branchCommitted(e) {
    this.branch = e.detail.value;
  }

  _repoChanged() {
    this.$.branchInput.clear();
    this._branchDisabled = !this.repo;
  }
}

customElements.define(GrRepoBranchPicker.is, GrRepoBranchPicker);
