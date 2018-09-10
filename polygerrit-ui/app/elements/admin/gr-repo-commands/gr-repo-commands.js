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

import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-create-change-dialog/gr-create-change-dialog.js';
import '../gr-repo-command/gr-repo-command.js';

const GC_MESSAGE = 'Garbage collection completed successfully.';

const CONFIG_BRANCH = 'refs/meta/config';
const CONFIG_PATH = 'project.config';
const EDIT_CONFIG_SUBJECT = 'Edit Repo Config';
const INITIAL_PATCHSET = 1;
const CREATE_CHANGE_FAILED_MESSAGE = 'Failed to create change.';
const CREATE_CHANGE_SUCCEEDED_MESSAGE = 'Navigating to change';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-subpage-styles"></style>
    <style include="gr-form-styles"></style>
    <main class="gr-form-styles read-only">
      <h1 id="Title">Repository Commands</h1>
      <div id="loading" class\$="[[_computeLoadingClass(_loading)]]">Loading...</div>
      <div id="loadedContent" class\$="[[_computeLoadingClass(_loading)]]">
        <h2 id="options">Command</h2>
        <div id="form">
          <gr-repo-command title="Create change" on-command-tap="_createNewChange">
          </gr-repo-command>
          <gr-repo-command id="editRepoConfig" title="Edit repo config" on-command-tap="_handleEditRepoConfig">
          </gr-repo-command>
          <gr-repo-command title="[[_repoConfig.actions.gc.label]]" tooltip="[[_repoConfig.actions.gc.title]]" hidden\$="[[!_repoConfig.actions.gc.enabled]]" on-command-tap="_handleRunningGC">
          </gr-repo-command>
          <gr-endpoint-decorator name="repo-command">
            <gr-endpoint-param name="config" value="[[_repoConfig]]">
            </gr-endpoint-param>
            <gr-endpoint-param name="repoName" value="[[repo]]">
            </gr-endpoint-param>
          </gr-endpoint-decorator>
        </div>
      </div>
    </main>
    <gr-overlay id="createChangeOverlay" with-backdrop="">
      <gr-dialog id="createChangeDialog" confirm-label="Create" disabled="[[!_canCreate]]" on-confirm="_handleCreateChange" on-cancel="_handleCloseCreateChange">
        <div class="header" slot="header">
          Create Change
        </div>
        <div class="main" slot="main">
          <gr-create-change-dialog id="createNewChangeModal" can-create="{{_canCreate}}" repo-name="[[repo]]"></gr-create-change-dialog>
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-repo-commands',

  properties: {
    params: Object,
    repo: String,
    _loading: {
      type: Boolean,
      value: true,
    },
    /** @type {?} */
    _repoConfig: Object,
    _canCreate: Boolean,
  },

  attached() {
    this._loadRepo();

    this.fire('title-change', {title: 'Repo Commands'});
  },

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
  },

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  _isLoading() {
    return this._loading || this._loading === undefined;
  },

  _handleRunningGC() {
    return this.$.restAPI.runRepoGC(this.repo).then(response => {
      if (response.status === 200) {
        this.dispatchEvent(new CustomEvent('show-alert',
            {detail: {message: GC_MESSAGE}, bubbles: true}));
      }
    });
  },

  _createNewChange() {
    this.$.createChangeOverlay.open();
  },

  _handleCreateChange() {
    this.$.createNewChangeModal.handleCreateChange();
    this._handleCloseCreateChange();
  },

  _handleCloseCreateChange() {
    this.$.createChangeOverlay.close();
  },

  _handleEditRepoConfig() {
    return this.$.restAPI.createChange(this.repo, CONFIG_BRANCH,
        EDIT_CONFIG_SUBJECT, undefined, false, true).then(change => {
          const message = change ?
              CREATE_CHANGE_SUCCEEDED_MESSAGE :
              CREATE_CHANGE_FAILED_MESSAGE;
          this.dispatchEvent(new CustomEvent('show-alert',
              {detail: {message}, bubbles: true}));
          if (!change) { return; }

          Gerrit.Nav.navigateToRelativeUrl(Gerrit.Nav.getEditUrlForDiff(
              change, CONFIG_PATH, INITIAL_PATCHSET));
        });
  }
});
