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

  const GC_MESSAGE = 'Garbage collection completed successfully.';

  const CONFIG_BRANCH = 'refs/meta/config';
  const CONFIG_PATH = 'project.config';
  const EDIT_CONFIG_SUBJECT = 'Edit Repo Config';
  const INITIAL_PATCHSET = 1;
  const CREATE_CHANGE_FAILED_MESSAGE = 'Failed to create change.';
  const CREATE_CHANGE_SUCCEEDED_MESSAGE = 'Navigating to change';

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrRepoCommands extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-repo-commands'; }

    static get properties() {
      return {
        params: Object,
        repo: String,
        _loading: {
          type: Boolean,
          value: true,
        },
        /** @type {?} */
        _repoConfig: Object,
        _canCreate: Boolean,
      };
    }

    attached() {
      super.attached();
      this._loadRepo();

      this.fire('title-change', {title: 'Repo Commands'});
    }

    _loadRepo() {
      if (!this.repo) { return Promise.resolve(); }

      const errFn = response => {
        this.fire('page-error', {response});
      };

      return this.$.restAPI.getProjectConfig(this.repo, errFn)
          .then(config => {
            if (!config) { return Promise.resolve(); }

            this._repoConfig = config;
            this._loading = false;
          });
    }

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    }

    _isLoading() {
      return this._loading || this._loading === undefined;
    }

    _handleRunningGC() {
      return this.$.restAPI.runRepoGC(this.repo).then(response => {
        if (response.status === 200) {
          this.dispatchEvent(new CustomEvent(
              'show-alert',
              {detail: {message: GC_MESSAGE}, bubbles: true, composed: true}));
        }
      });
    }

    _createNewChange() {
      this.$.createChangeOverlay.open();
    }

    _handleCreateChange() {
      this.$.createNewChangeModal.handleCreateChange();
      this._handleCloseCreateChange();
    }

    _handleCloseCreateChange() {
      this.$.createChangeOverlay.close();
    }

    _handleEditRepoConfig() {
      return this.$.restAPI.createChange(this.repo, CONFIG_BRANCH,
          EDIT_CONFIG_SUBJECT, undefined, false, true).then(change => {
        const message = change ?
          CREATE_CHANGE_SUCCEEDED_MESSAGE :
          CREATE_CHANGE_FAILED_MESSAGE;
        this.dispatchEvent(new CustomEvent(
            'show-alert',
            {detail: {message}, bubbles: true, composed: true}));
        if (!change) { return; }

        Gerrit.Nav.navigateToRelativeUrl(Gerrit.Nav.getEditUrlForDiff(
            change, CONFIG_PATH, INITIAL_PATCHSET));
      });
    }
  }

  customElements.define(GrRepoCommands.is, GrRepoCommands);
})();
