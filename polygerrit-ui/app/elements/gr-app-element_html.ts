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
      background-color: var(--background-color-tertiary);
      display: flex;
      flex-direction: column;
      min-height: 100%;
    }
    gr-main-header,
    footer {
      color: var(--primary-text-color);
    }
    gr-main-header {
      background: var(
        --header-background,
        var(--header-background-color, #eee)
      );
      padding: var(--header-padding);
      border-bottom: var(--header-border-bottom);
      border-image: var(--header-border-image);
      border-right: 0;
      border-left: 0;
      border-top: 0;
      box-shadow: var(--header-box-shadow);
      /* Make sure the header is above the main content, to preserve box-shadow
         visibility. We need 2 here instead of 1, because dropdowns in the
         header should be shown on top of the sticky diff header, which has a
         z-index of 1. */
      z-index: 2;
    }
    footer {
      background: var(
        --footer-background,
        var(--footer-background-color, #eee)
      );
      border-top: var(--footer-border-top);
      display: flex;
      justify-content: space-between;
      padding: var(--spacing-m) var(--spacing-l);
      z-index: 100;
    }
    main {
      flex: 1;
      padding-bottom: var(--spacing-xxl);
      position: relative;
    }
    .errorView {
      align-items: center;
      display: none;
      flex-direction: column;
      justify-content: center;
      position: absolute;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
    }
    .errorView.show {
      display: flex;
    }
    .errorEmoji {
      font-size: 2.6rem;
    }
    .errorText,
    .errorMoreInfo {
      margin-top: var(--spacing-m);
    }
    .errorText {
      font-family: var(--header-font-family);
      font-size: var(--font-size-h3);
      font-weight: var(--font-weight-h3);
      line-height: var(--line-height-h3);
    }
    .errorMoreInfo {
      color: var(--deemphasized-text-color);
    }
    .feedback {
      color: var(--error-text-color);
    }
  </style>
  <gr-endpoint-decorator name="banner"></gr-endpoint-decorator>
  <gr-main-header
    id="mainHeader"
    search-query="{{params.query}}"
    on-mobile-search="_mobileSearchToggle"
    on-show-keyboard-shortcuts="handleShowKeyboardShortcuts"
    mobile-search-hidden="[[!mobileSearch]]"
    login-url="[[_loginUrl]]"
    aria-hidden="[[_footerHeaderAriaHidden]]"
  >
  </gr-main-header>
  <main aria-hidden="[[_mainAriaHidden]]">
    <template is="dom-if" if="[[mobileSearch]]">
      <gr-smart-search
        id="search"
        label="Search for changes"
        search-query="{{params.query}}"
        hidden="[[!mobileSearch]]"
      >
      </gr-smart-search>
    </template>
    <template is="dom-if" if="[[_showChangeListView]]" restamp="true">
      <gr-change-list-view
        params="[[params]]"
        account="[[_account]]"
        view-state="{{_viewState.changeListView}}"
      ></gr-change-list-view>
    </template>
    <template is="dom-if" if="[[_showDashboardView]]" restamp="true">
      <gr-dashboard-view
        account="[[_account]]"
        params="[[params]]"
        view-state="{{_viewState.dashboardView}}"
      ></gr-dashboard-view>
    </template>
    <!-- Note that the change view does not have restamp="true" set, because we
         want to re-use it as long as the change number does not change. -->
    <template id="dom-if-change-view" is="dom-if" if="[[_showChangeView]]">
      <gr-change-view
        params="[[params]]"
        view-state="{{_viewState.changeView}}"
        back-page="[[_lastSearchPage]]"
      ></gr-change-view>
    </template>
    <template is="dom-if" if="[[_showEditorView]]" restamp="true">
      <gr-editor-view params="[[params]]"></gr-editor-view>
    </template>
    <!-- Note that the diff view does not have restamp="true" set, because we
         want to re-use it as long as the change number does not change. -->
    <template id="dom-if-diff-view" is="dom-if" if="[[_showDiffView]]">
      <gr-diff-view
        params="[[params]]"
        change-view-state="{{_viewState.changeView}}"
      ></gr-diff-view>
    </template>
    <template is="dom-if" if="[[_showSettingsView]]" restamp="true">
      <gr-settings-view
        params="[[params]]"
        on-account-detail-update="_handleAccountDetailUpdate"
      >
      </gr-settings-view>
    </template>
    <template is="dom-if" if="[[_showAdminView]]" restamp="true">
      <gr-admin-view path="[[_path]]" params="[[params]]"></gr-admin-view>
    </template>
    <template is="dom-if" if="[[_showPluginScreen]]" restamp="true">
      <gr-endpoint-decorator name="[[_pluginScreenName]]">
        <gr-endpoint-param
          name="token"
          value="[[params.screen]]"
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
    </template>
    <template is="dom-if" if="[[_showCLAView]]" restamp="true">
      <gr-cla-view></gr-cla-view>
    </template>
    <template is="dom-if" if="[[_showDocumentationSearch]]" restamp="true">
      <gr-documentation-search params="[[params]]"> </gr-documentation-search>
    </template>
    <div id="errorView" class="errorView">
      <div class="errorEmoji">[[_lastError.emoji]]</div>
      <div class="errorText">[[_lastError.text]]</div>
      <div class="errorMoreInfo">[[_lastError.moreInfo]]</div>
    </div>
  </main>
  <footer r="contentinfo" aria-hidden="[[_footerHeaderAriaHidden]]">
    <div>
      Powered by
      <a href="https://www.gerritcodereview.com/" rel="noopener" target="_blank"
        >Gerrit Code Review</a
      >
      ([[_version]])
      <gr-endpoint-decorator name="footer-left"></gr-endpoint-decorator>
    </div>
    <div>
      <template is="dom-if" if="[[_feedbackUrl]]">
        <a
          class="feedback"
          href$="[[_feedbackUrl]]"
          rel="noopener"
          target="_blank"
          >Report bug</a
        >
        |
      </template>
      Press “?” for keyboard shortcuts
      <gr-endpoint-decorator name="footer-right"></gr-endpoint-decorator>
    </div>
  </footer>
  <template is="dom-if" if="[[loadKeyboardShortcutsDialog]]">
    <gr-overlay
      id="keyboardShortcuts"
      with-backdrop=""
      on-iron-overlay-canceled="onOverlayCanceled"
    >
      <gr-keyboard-shortcuts-dialog
        on-close="_handleKeyboardShortcutDialogClose"
      ></gr-keyboard-shortcuts-dialog>
    </gr-overlay>
  </template>
  <template is="dom-if" if="[[loadRegistrationDialog]]">
    <gr-overlay id="registrationOverlay" with-backdrop="">
      <gr-registration-dialog
        id="registrationDialog"
        settings-url="[[_settingsUrl]]"
        on-account-detail-update="_handleAccountDetailUpdate"
        on-close="_handleRegistrationDialogClose"
      >
      </gr-registration-dialog>
    </gr-overlay>
  </template>
  <gr-endpoint-decorator name="plugin-overlay"></gr-endpoint-decorator>
  <gr-error-manager
    id="errorManager"
    login-url="[[_loginUrl]]"
  ></gr-error-manager>
  <gr-router id="router"></gr-router>
  <gr-plugin-host id="plugins" config="[[_serverConfig]]"> </gr-plugin-host>
  <gr-external-style
    id="externalStyleForAll"
    name="app-theme"
  ></gr-external-style>
  <gr-external-style
    id="externalStyleForTheme"
    name="[[getThemeEndpoint()]]"
  ></gr-external-style>
`;
