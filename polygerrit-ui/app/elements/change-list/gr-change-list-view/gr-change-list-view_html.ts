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
import {html} from '@polymer/polymer/lib/utils/html-tag';

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
    gr-user-header,
    gr-repo-header {
      border-bottom: 1px solid var(--border-color);
    }
    nav {
      align-items: center;
      display: flex;
      height: 3rem;
      justify-content: flex-end;
      margin-right: 20px;
    }
    nav,
    iron-icon {
      color: var(--deemphasized-text-color);
    }
    iron-icon {
      height: 1.85rem;
      margin-left: 16px;
      width: 1.85rem;
    }
    .hide {
      display: none;
    }
    @media only screen and (max-width: 50em) {
      .loading,
      .error {
        padding: 0 var(--spacing-l);
      }
    }
  </style>
  <div class="loading" hidden$="[[!_loading]]" hidden="">Loading...</div>
  <div hidden$="[[_loading]]" hidden="">
    <gr-repo-header
      repo="[[_repo]]"
      class$="[[_computeHeaderClass(_repo)]]"
    ></gr-repo-header>
    <gr-user-header
      user-id="[[_userId]]"
      showDashboardLink=""
      logged-in="[[_loggedIn]]"
      class$="[[_computeHeaderClass(_userId)]]"
    ></gr-user-header>
    <gr-change-list
      account="[[account]]"
      changes="{{_changes}}"
      preferences="[[preferences]]"
      selected-index="{{viewState.selectedChangeIndex}}"
      show-star="[[_loggedIn]]"
      on-toggle-star="_handleToggleStar"
      observer-target="[[_computeObserverTarget()]]"
    ></gr-change-list>
    <nav class$="[[_computeNavClass(_loading)]]">
      Page [[_computePage(_offset, _changesPerPage)]]
      <a
        id="prevArrow"
        href$="[[_computeNavLink(_query, _offset, -1, _changesPerPage)]]"
        class$="[[_computePrevArrowClass(_offset)]]"
      >
        <iron-icon icon="gr-icons:chevron-left" aria-label="Older"> </iron-icon>
      </a>
      <a
        id="nextArrow"
        href$="[[_computeNavLink(_query, _offset, 1, _changesPerPage)]]"
        class$="[[_computeNextArrowClass(_changes)]]"
      >
        <iron-icon icon="gr-icons:chevron-right" aria-label="Newer">
        </iron-icon>
      </a>
    </nav>
  </div>
`;
