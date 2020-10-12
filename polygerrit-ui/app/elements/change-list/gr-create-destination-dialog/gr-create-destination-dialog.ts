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

import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-repo-branch-picker/gr-repo-branch-picker';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-create-destination-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {RepoName, BranchName} from '../../../types/common';

export interface CreateDestinationConfirmDetail {
  repo?: RepoName;
  branch?: BranchName;
}

/**
 * Fired when a destination has been picked. Event details contain the repo
 * name and the branch name.
 *
 * @event confirm
 */
export interface GrCreateDestinationDialog {
  $: {
    createOverlay: GrOverlay;
  };
}

@customElement('gr-create-destination-dialog')
export class GrCreateDestinationDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  _repo?: RepoName;

  @property({type: String})
  _branch?: BranchName;

  @property({
    type: Boolean,
    computed: '_computeRepoAndBranchSelected(_repo, _branch)',
  })
  _repoAndBranchSelected = false;

  open() {
    this._repo = '' as RepoName;
    this._branch = '' as BranchName;
    this.$.createOverlay.open();
  }

  _handleClose() {
    this.$.createOverlay.close();
  }

  _pickerConfirm(e: Event) {
    this.$.createOverlay.close();
    const detail: CreateDestinationConfirmDetail = {
      repo: this._repo,
      branch: this._branch,
    };
    // e is a 'confirm' event from gr-dialog. We want to fire a more detailed
    // 'confirm' event here, so let's stop propagation of the bare event.
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {detail, bubbles: false}));
  }

  _computeRepoAndBranchSelected(repo?: RepoName, branch?: BranchName) {
    return !!(repo && branch);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-destination-dialog': GrCreateDestinationDialog;
  }
}
