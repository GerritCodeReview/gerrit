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
      :host {
        display: block;
      }
      .loading {
        color: var(--deemphasized-text-color);
        padding: var(--spacing-l);
      }
      gr-change-list {
        width: 100%;
      }
      gr-user-header {
        border-bottom: 1px solid var(--border-color);
      }
      .banner {
        align-items: center;
        background-color: var(--comment-background-color);
        border-bottom: 1px solid var(--border-color);
        display: flex;
        justify-content: space-between;
        padding: var(--spacing-xs) var(--spacing-l);
      }
      .banner gr-button {
        --gr-button: {
          color: var(--primary-text-color);
        }
      }
      .hide {
        display: none;
      }
      #emptyOutgoing {
        display: block;
      }
      @media only screen and (max-width: 50em) {
        .loading {
          padding: 0 var(--spacing-l);
        }
      }
    </style>
    <div class\$="banner [[_computeBannerClass(_showDraftsBanner)]]">
      <div>
        You have draft comments on closed changes.
        <a href\$="[[_computeDraftsLink(_showDraftsBanner)]]" target="_blank">(view all)</a>
      </div>
      <div>
        <gr-button class="delete" link="" on-click="_handleOpenDeleteDialog">Delete All</gr-button>
      </div>
    </div>
    <div class="loading" hidden\$="[[!_loading]]">Loading...</div>
    <div hidden\$="[[_loading]]" hidden="">
      <gr-user-header user-id="[[params.user]]" class\$="[[_computeUserHeaderClass(params)]]"></gr-user-header>
      <gr-change-list show-star="" show-reviewed-state="" account="[[account]]" preferences="[[preferences]]" selected-index="{{viewState.selectedChangeIndex}}" sections="[[_results]]" on-toggle-star="_handleToggleStar" on-toggle-reviewed="_handleToggleReviewed">
        <div id="emptyOutgoing" slot="empty-outgoing">
          <template is="dom-if" if="[[_showNewUserHelp]]">
            <gr-create-change-help on-create-tap="createChangeTap"></gr-create-change-help>
          </template>
          <template is="dom-if" if="[[!_showNewUserHelp]]">
            No changes
          </template>
        </div>
      </gr-change-list>
    </div>
    <gr-overlay id="confirmDeleteOverlay" with-backdrop="">
      <gr-dialog id="confirmDeleteDialog" confirm-label="Delete" on-confirm="_handleConfirmDelete" on-cancel="_closeConfirmDeleteOverlay">
        <div class="header" slot="header">
          Delete comments
        </div>
        <div class="main" slot="main">
          Are you sure you want to delete all your draft comments in closed changes? This action
          cannot be undone.
        </div>
      </gr-dialog>
    </gr-overlay>
    <gr-create-destination-dialog id="destinationDialog" on-confirm="_handleDestinationConfirm"></gr-create-destination-dialog>
    <gr-create-commands-dialog id="commandsDialog"></gr-create-commands-dialog>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
