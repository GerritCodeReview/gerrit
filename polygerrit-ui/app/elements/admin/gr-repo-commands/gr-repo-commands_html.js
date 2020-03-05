/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-subpage-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
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
`;
