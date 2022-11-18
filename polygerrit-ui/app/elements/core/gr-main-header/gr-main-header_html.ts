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
    nav {
      align-items: center;
      display: flex;
    }
    .bigTitle {
      color: var(--header-text-color);
      font-size: var(--header-title-font-size);
      text-decoration: none;
    }
    .bigTitle:hover {
      text-decoration: underline;
    }
    .titleText::before {
      background-image: var(--header-icon);
      background-size: var(--header-icon-size) var(--header-icon-size);
      background-repeat: no-repeat;
      content: '';
      display: inline-block;
      height: var(--header-icon-size);
      margin-right: calc(var(--header-icon-size) / 4);
      vertical-align: text-bottom;
      width: var(--header-icon-size);
    }
    .titleText::after {
      content: var(--header-title-content);
    }
    ul {
      list-style: none;
      padding-left: var(--spacing-l);
    }
    .links > li {
      cursor: default;
      display: inline-block;
      padding: 0;
      position: relative;
    }
    .linksTitle {
      display: inline-block;
      font-weight: var(--font-weight-bold);
      position: relative;
      text-transform: uppercase;
    }
    .linksTitle:hover {
      opacity: 0.75;
    }
    .rightItems {
      align-items: center;
      display: flex;
      flex: 1;
      justify-content: flex-end;
    }
    .rightItems gr-endpoint-decorator:not(:empty) {
      margin-left: var(--spacing-l);
    }
    gr-smart-search {
      flex-grow: 1;
      margin: 0 var(--spacing-m);
      max-width: 500px;
      min-width: 150px;
    }
    gr-dropdown,
    .browse {
      padding: var(--spacing-m);
    }
    gr-dropdown {
      --gr-dropdown-item-color: var(--primary-text-color);
    }
    .settingsButton {
      margin-left: var(--spacing-m);
    }
    .feedbackButton {
      margin-left: var(--spacing-s);
    }
    .browse {
      color: var(--header-text-color);
      /* Same as gr-button */
      margin: 5px 4px;
      text-decoration: none;
    }
    .invisible,
    .settingsButton,
    gr-account-dropdown {
      display: none;
    }
    :host([loading]) .accountContainer,
    :host([logged-in]) .loginButton,
    :host([logged-in]) .registerButton {
      display: none;
    }
    :host([logged-in]) .settingsButton,
    :host([logged-in]) gr-account-dropdown {
      display: inline;
    }
    .accountContainer {
      align-items: center;
      display: flex;
      margin: 0 calc(0 - var(--spacing-m)) 0 var(--spacing-m);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .loginButton,
    .registerButton {
      padding: var(--spacing-m) var(--spacing-l);
    }
    .dropdown-trigger {
      text-decoration: none;
    }
    .dropdown-content {
      background-color: var(--view-background-color);
      box-shadow: var(--elevation-level-2);
    }
    /*
       * We are not using :host to do this, because :host has a lowest css priority
       * compared to others. This means that using :host to do this would break styles.
       */
    .linksTitle,
    .bigTitle,
    .loginButton,
    .registerButton,
    iron-icon,
    gr-account-dropdown {
      color: var(--header-text-color);
    }
    #mobileSearch {
      display: none;
    }
    @media screen and (max-width: 50em) {
      .bigTitle {
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
      }
      gr-smart-search,
      .browse,
      .rightItems .hideOnMobile,
      .links > li.hideOnMobile {
        display: none;
      }
      #mobileSearch {
        display: inline-flex;
      }
      .accountContainer {
        margin-left: var(--spacing-m) !important;
      }
      gr-dropdown {
        padding: var(--spacing-m) 0 var(--spacing-m) var(--spacing-m);
      }
    }
  </style>
  <nav>
    <a href$="[[_computeRelativeURL('/')]]" class="bigTitle">
      <gr-endpoint-decorator name="header-title">
        <span class="titleText"></span>
      </gr-endpoint-decorator>
    </a>
    <ul class="links">
      <template is="dom-repeat" items="[[_links]]" as="linkGroup">
        <li class$="[[_computeLinkGroupClass(linkGroup)]]">
          <gr-dropdown
            link=""
            down-arrow=""
            items="[[linkGroup.links]]"
            horizontal-align="left"
          >
            <span class="linksTitle" id="[[linkGroup.title]]">
              [[linkGroup.title]]
            </span>
          </gr-dropdown>
        </li>
      </template>
    </ul>
    <div class="rightItems">
      <gr-endpoint-decorator
        class="hideOnMobile"
        name="header-small-banner"
      ></gr-endpoint-decorator>
      <gr-smart-search
        id="search"
        label="Search for changes"
        search-query="{{searchQuery}}"
      ></gr-smart-search>
      <gr-endpoint-decorator
        class="hideOnMobile"
        name="header-browse-source"
      ></gr-endpoint-decorator>
      <gr-endpoint-decorator class="feedbackButton" name="header-feedback">
        <template is="dom-if" if="[[_feedbackURL]]">
          <a
            href$="[[_feedbackURL]]"
            title="File a bug"
            aria-label="File a bug"
            target="_blank"
            role="button"
          >
            <iron-icon icon="gr-icons:bug"></iron-icon>
          </a>
        </template>
      </gr-endpoint-decorator>
      </div>
      <div class="accountContainer" id="accountContainer">
        <div>
          <iron-icon
            id="mobileSearch"
            icon="gr-icons:search"
            on-click="_onMobileSearchTap"
            role="button"
            aria-label="[[_computeShowHideAriaLabel(mobileSearchHidden)]]"
          ></iron-icon>
        </div>
        <div
          class="registerDiv"
          hidden="[[_computeRegisterHidden(_registerURL)]]"
        >
          <a class="registerButton" href$="[[_registerURL]]">
            [[_registerText]]
          </a>
        </div>
        <a class="loginButton" href$="[[loginUrl]]">Sign in</a>
        <a
          class="settingsButton"
          href$="[[_generateSettingsLink()]]"
          title="Settings"
          aria-label="Settings"
          role="button"
        >
          <iron-icon icon="gr-icons:settings"></iron-icon>
        </a>
        <template is="dom-if" if="[[_account]]">
          <gr-account-dropdown account="[[_account]]"></gr-account-dropdown>
        </template>
      </div>
    </div>
  </nav>
`;
