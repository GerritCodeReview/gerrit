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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-create-change-dialog/gr-create-change-dialog.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-repo-commands_html.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';

const GC_MESSAGE = 'Garbage collection completed successfully.';

const CONFIG_BRANCH = 'refs/meta/config';
const CONFIG_PATH = 'project.config';
const EDIT_CONFIG_SUBJECT = 'Edit Repo Config';
const INITIAL_PATCHSET = 1;
const CREATE_CHANGE_FAILED_MESSAGE = 'Failed to create change.';
const CREATE_CHANGE_SUCCEEDED_MESSAGE = 'Navigating to change';

/**
 * @extends PolymerElement
 */
class GrRepoCommands extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

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
      // states
      _creatingChange: Boolean,
      _editingConfig: Boolean,
      _runningGC: Boolean,
    };
  }

  /** @override */
  attached() {
    super.attached();
    this._loadRepo();

    this.dispatchEvent(new CustomEvent('title-change', {
      detail: {title: 'Repo Commands'},
      composed: true, bubbles: true,
    }));
  }

  _loadRepo() {
    if (!this.repo) { return Promise.resolve(); }

    const errFn = response => {
      this.dispatchEvent(new CustomEvent('page-error', {
        detail: {response},
        composed: true, bubbles: true,
      }));
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
    this._runningGC = true;
    return this.$.restAPI.runRepoGC(this.repo).then(response => {
      if (response.status === 200) {
        this.dispatchEvent(new CustomEvent(
            'show-alert',
            {detail: {message: GC_MESSAGE}, bubbles: true, composed: true}));
      }
    })
        .finally(() => {
          this._runningGC = false;
        });
  }

  _createNewChange() {
    this.$.createChangeOverlay.open();
  }

  _handleCreateChange() {
    this._creatingChange = true;
    this.$.createNewChangeModal.handleCreateChange()
        .finally(() => {
          this._creatingChange = false;
        });
    this._handleCloseCreateChange();
  }

  _handleCloseCreateChange() {
    this.$.createChangeOverlay.close();
  }

  _handleEditRepoConfig() {
    this._editingConfig = true;
    return this.$.restAPI.createChange(this.repo, CONFIG_BRANCH,
        EDIT_CONFIG_SUBJECT, undefined, false, true).then(change => {
      const message = change ?
        CREATE_CHANGE_SUCCEEDED_MESSAGE :
        CREATE_CHANGE_FAILED_MESSAGE;
      this.dispatchEvent(new CustomEvent('show-alert',
          {detail: {message}, bubbles: true, composed: true}));
      if (!change) { return; }

      GerritNav.navigateToRelativeUrl(GerritNav.getEditUrlForDiff(
          change, CONFIG_PATH, INITIAL_PATCHSET));
    })
        .finally(() => {
          this._editingConfig = false;
        });
  }
}

customElements.define(GrRepoCommands.is, GrRepoCommands);
