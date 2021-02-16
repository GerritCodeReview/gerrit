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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/gr-form-styles';
import '../../../styles/gr-subpage-styles';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../gr-create-change-dialog/gr-create-change-dialog';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-commands_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {
  BranchName,
  ConfigInfo,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrCreateChangeDialog} from '../gr-create-change-dialog/gr-create-change-dialog';
import {
  fireAlert,
  firePageError,
  fireTitleChange,
} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';

const GC_MESSAGE = 'Garbage collection completed successfully.';
const CONFIG_BRANCH = 'refs/meta/config' as BranchName;
const CONFIG_PATH = 'project.config';
const EDIT_CONFIG_SUBJECT = 'Edit Repo Config';
const INITIAL_PATCHSET = 1 as PatchSetNum;
const CREATE_CHANGE_FAILED_MESSAGE = 'Failed to create change.';
const CREATE_CHANGE_SUCCEEDED_MESSAGE = 'Navigating to change';

export interface GrRepoCommands {
  $: {
    createChangeOverlay: GrOverlay;
    createNewChangeModal: GrCreateChangeDialog;
  };
}

@customElement('gr-repo-commands')
export class GrRepoCommands extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  // This is a required property. Without `repo` being set the component is not
  // useful. Thus using !.
  @property({type: String})
  repo!: RepoName;

  @property({type: Boolean})
  _loading = true;

  @property({type: Object})
  _repoConfig?: ConfigInfo;

  @property({type: Boolean})
  _canCreate = false;

  @property({type: Boolean})
  _creatingChange = false;

  @property({type: Boolean})
  _editingConfig = false;

  @property({type: Boolean})
  _runningGC = false;

  private restApiService = appContext.restApiService;

  /** @override */
  attached() {
    super.attached();
    this._loadRepo();

    fireTitleChange(this, 'Repo Commands');
  }

  _loadRepo() {
    const errFn: ErrorCallback = response => {
      // Do not process the error, if the component is not attached to the DOM
      // anymore, which at least in tests can happen.
      if (!this.isConnected) return;
      firePageError(response);
    };

    this.restApiService.getProjectConfig(this.repo, errFn).then(config => {
      if (!config) return;
      // Do not process the response, if the component is not attached to the
      // DOM anymore, which at least in tests can happen.
      if (!this.isConnected) return;
      this._repoConfig = config;
      this._loading = false;
    });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _isLoading() {
    return this._loading;
  }

  _handleRunningGC() {
    if (!this.repo) return;
    this._runningGC = true;
    return this.restApiService
      .runRepoGC(this.repo)
      .then(response => {
        if (response?.status === 200) {
          fireAlert(this, GC_MESSAGE);
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
    this.$.createNewChangeModal.handleCreateChange().finally(() => {
      this._creatingChange = false;
    });
    this._handleCloseCreateChange();
  }

  _handleCloseCreateChange() {
    this.$.createChangeOverlay.close();
  }

  /**
   * Returns a Promise for testing.
   */
  _handleEditRepoConfig() {
    this._editingConfig = true;
    return this.restApiService
      .createChange(
        this.repo,
        CONFIG_BRANCH,
        EDIT_CONFIG_SUBJECT,
        undefined,
        false,
        true
      )
      .then(change => {
        const message = change
          ? CREATE_CHANGE_SUCCEEDED_MESSAGE
          : CREATE_CHANGE_FAILED_MESSAGE;
        fireAlert(this, message);
        if (!change) {
          return;
        }

        GerritNav.navigateToRelativeUrl(
          GerritNav.getEditUrlForDiff(change, CONFIG_PATH, INITIAL_PATCHSET)
        );
      })
      .finally(() => {
        this._editingConfig = false;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-commands': GrRepoCommands;
  }
}
