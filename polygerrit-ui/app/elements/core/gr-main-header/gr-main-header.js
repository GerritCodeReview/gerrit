/**
@license
Copyright (C) 2016 The Android Open Source Project

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

import '../../../behaviors/docs-url-behavior/docs-url-behavior.js';
import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-admin-nav-behavior/gr-admin-nav-behavior.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../shared/gr-dropdown/gr-dropdown.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-account-dropdown/gr-account-dropdown.js';
import '../gr-smart-search/gr-smart-search.js';

const DEFAULT_LINKS = [{
  title: 'Changes',
  links: [
    {
      url: '/q/status:open',
      name: 'Open',
    },
    {
      url: '/q/status:merged',
      name: 'Merged',
    },
    {
      url: '/q/status:abandoned',
      name: 'Abandoned',
    },
  ],
}];

const DOCUMENTATION_LINKS = [
  {
    url: '/index.html',
    name: 'Table of Contents',
  },
  {
    url: '/user-search.html',
    name: 'Searching',
  },
  {
    url: '/user-upload.html',
    name: 'Uploading',
  },
  {
    url: '/access-control.html',
    name: 'Access Control',
  },
  {
    url: '/rest-api.html',
    name: 'REST API',
  },
  {
    url: '/intro-project-owner.html',
    name: 'Project Owner Guide',
  },
];

Polymer({
  _template: Polymer.html`
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
        font-size: 1.75rem;
        text-decoration: none;
      }
      .bigTitle:hover {
        text-decoration: underline;
      }
      /* TODO (viktard): Clean-up after chromium-style migrates to component. */
      .titleText::before {
        background-image: var(--header-icon);
        background-size: var(--header-icon-size) var(--header-icon-size);
        background-repeat: no-repeat;
        content: "";
        display: inline-block;
        height: var(--header-icon-size);
        vertical-align: text-bottom;
        width: var(--header-icon-size);
      }
      .titleText::after {
        content: var(--header-title-content);
      }
      ul {
        list-style: none;
        padding-left: 1em;
      }
      .links > li {
        cursor: default;
        display: inline-block;
        padding: 0;
        position: relative;
      }
      .linksTitle {
        color: var(--header-text-color);
        display: inline-block;
        font-family: var(--font-family-bold);
        position: relative;
        text-transform: uppercase;
      }
      .linksTitle:hover {
        opacity: .75;
      }
      .rightItems {
        align-items: center;
        display: flex;
        flex: 1;
        justify-content: flex-end;
      }
      .rightItems gr-endpoint-decorator:not(:empty) {
        margin-left: 1em;
      }
      gr-smart-search {
        flex-grow: 1;
        margin-left: .5em;
        max-width: 500px;
      }
      gr-dropdown,
      .browse {
        padding: .6em .5em;
      }
      gr-dropdown {
        --gr-dropdown-item: {
          color: var(--primary-text-color);
        }
      }
      .settingsButton {
        margin-left: .5em;
      }
      .browse {
        color: var(--header-text-color);
        /* Same as gr-button */
        margin: 5px 4px;
        text-decoration: none;
      }
      .settingsButton,
      gr-account-dropdown {
        display: none;
      }
      :host([loading]) .accountContainer,
      :host([logged-in]) .loginButton {
        display: none;
      }
      :host([logged-in]) .settingsButton,
      :host([logged-in]) gr-account-dropdown {
        display: inline;
      }
      iron-icon {
        color: var(--header-text-color);
      }
      .accountContainer {
        align-items: center;
        display: flex;
        margin: 0 -.5em 0 .5em;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .loginButton {
        color: var(--header-text-color);
        padding: .5em 1em;
      }
      .dropdown-trigger {
        text-decoration: none;
      }
      .dropdown-content {
        background-color: var(--view-background-color);
        box-shadow: 0 1px 5px rgba(0, 0, 0, .3);
      }
      @media screen and (max-width: 50em) {
        .bigTitle {
          font-size: var(--font-size-large);
          font-family: var(--font-family-bold);
        }
        gr-smart-search,
        .browse,
        .rightItems .hideOnMobile,
        .links > li.hideOnMobile {
          display: none;
        }
        .accountContainer {
          margin-left: .5em !important;
        }
        gr-dropdown {
          padding: .5em 0 .5em .5em;
        }
      }
    </style>
    <nav>
      <a href\$="[[_computeRelativeURL('/')]]" class="bigTitle">
        <gr-endpoint-decorator name="header-title">
          <span class="titleText"></span>
        </gr-endpoint-decorator>
      </a>
      <ul class="links">
        <template is="dom-repeat" items="[[_links]]" as="linkGroup">
          <li class\$="[[linkGroup.class]]">
          <gr-dropdown link="" down-arrow="" items="[[linkGroup.links]]" horizontal-align="left">
            <span class="linksTitle" id="[[linkGroup.title]]">
              [[linkGroup.title]]
            </span>
          </gr-dropdown>
          </li>
        </template>
      </ul>
      <div class="rightItems">
        <gr-smart-search id="search" search-query="{{searchQuery}}"></gr-smart-search>
        <gr-endpoint-decorator class="hideOnMobile" name="header-browse-source"></gr-endpoint-decorator>
        <div class="accountContainer" id="accountContainer">
          <a class="loginButton" href\$="[[_loginURL]]">Sign in</a>
          <a class="settingsButton" href\$="[[_generateSettingsLink()]]" title="Settings">
            <iron-icon icon="gr-icons:settings"></iron-icon>
          </a>
          <gr-account-dropdown account="[[_account]]"></gr-account-dropdown>
        </div>
      </div>
    </nav>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-main-header',

  hostAttributes: {
    role: 'banner',
  },

  properties: {
    searchQuery: {
      type: String,
      notify: true,
    },
    loggedIn: {
      type: Boolean,
      reflectToAttribute: true,
    },
    loading: {
      type: Boolean,
      reflectToAttribute: true,
    },

    /** @type {?Object} */
    _account: Object,
    _adminLinks: {
      type: Array,
      value() { return []; },
    },
    _defaultLinks: {
      type: Array,
      value() {
        return DEFAULT_LINKS;
      },
    },
    _docBaseUrl: {
      type: String,
      value: null,
    },
    _links: {
      type: Array,
      computed: '_computeLinks(_defaultLinks, _userLinks, _adminLinks, ' +
          '_docBaseUrl)',
    },
    _loginURL: {
      type: String,
      value: '/login',
    },
    _userLinks: {
      type: Array,
      value() { return []; },
    },
  },

  behaviors: [
    Gerrit.AdminNavBehavior,
    Gerrit.BaseUrlBehavior,
    Gerrit.DocsUrlBehavior,
  ],

  observers: [
    '_accountLoaded(_account)',
  ],

  attached() {
    this._loadAccount();
    this._loadConfig();
    this.listen(window, 'location-change', '_handleLocationChange');
  },

  detached() {
    this.unlisten(window, 'location-change', '_handleLocationChange');
  },

  reload() {
    this._loadAccount();
  },

  _handleLocationChange(e) {
    const baseUrl = this.getBaseUrl();
    if (baseUrl) {
      // Strip the canonical path from the path since needing canonical in
      // the path is uneeded and breaks the url.
      this._loginURL = baseUrl + '/login/' + encodeURIComponent(
          '/' + window.location.pathname.substring(baseUrl.length) +
          window.location.search +
          window.location.hash);
    } else {
      this._loginURL = '/login/' + encodeURIComponent(
          window.location.pathname +
          window.location.search +
          window.location.hash);
    }
  },

  _computeRelativeURL(path) {
    return '//' + window.location.host + this.getBaseUrl() + path;
  },

  _computeLinks(defaultLinks, userLinks, adminLinks, docBaseUrl) {
    const links = defaultLinks.slice();
    if (userLinks && userLinks.length > 0) {
      links.push({
        title: 'Your',
        links: userLinks,
      });
    }
    const docLinks = this._getDocLinks(docBaseUrl, DOCUMENTATION_LINKS);
    if (docLinks.length) {
      links.push({
        title: 'Documentation',
        links: docLinks,
        class: 'hideOnMobile',
      });
    }
    links.push({
      title: 'Browse',
      links: adminLinks,
    });
    return links;
  },

  _getDocLinks(docBaseUrl, docLinks) {
    if (!docBaseUrl || !docLinks) {
      return [];
    }
    return docLinks.map(link => {
      let url = docBaseUrl;
      if (url && url[url.length - 1] === '/') {
        url = url.substring(0, url.length - 1);
      }
      return {
        url: url + link.url,
        name: link.name,
        target: '_blank',
      };
    });
  },

  _loadAccount() {
    this.loading = true;
    const promises = [
      this.$.restAPI.getAccount(),
      Gerrit.awaitPluginsLoaded(),
    ];

    return Promise.all(promises).then(result => {
      const account = result[0];
      this._account = account;
      this.loggedIn = !!account;
      this.loading = false;

      return this.getAdminLinks(account,
          this.$.restAPI.getAccountCapabilities.bind(this.$.restAPI),
          this.$.jsAPI.getAdminMenuLinks.bind(this.$.jsAPI))
          .then(res => {
            this._adminLinks = res.links;
          });
    });
  },

  _loadConfig() {
    this.$.restAPI.getConfig()
        .then(config => this.getDocsBaseUrl(config, this.$.restAPI))
        .then(docBaseUrl => { this._docBaseUrl = docBaseUrl; });
  },

  _accountLoaded(account) {
    if (!account) { return; }

    this.$.restAPI.getPreferences().then(prefs => {
      this._userLinks =
          prefs.my.map(this._fixMyMenuItem).filter(this._isSupportedLink);
    });
  },

  _fixMyMenuItem(linkObj) {
    // Normalize all urls to PolyGerrit style.
    if (linkObj.url.startsWith('#')) {
      linkObj.url = linkObj.url.slice(1);
    }

    // Delete target property due to complications of
    // https://bugs.chromium.org/p/gerrit/issues/detail?id=5888
    //
    // The server tries to guess whether URL is a view within the UI.
    // If not, it sets target='_blank' on the menu item. The server
    // makes assumptions that work for the GWT UI, but not PolyGerrit,
    // so we'll just disable it altogether for now.
    delete linkObj.target;

    // Becasue the "my menu" links may be arbitrary URLs, we don't know
    // whether they correspond to any client routes. Mark all such links as
    // external.
    linkObj.external = true;

    return linkObj;
  },

  _isSupportedLink(linkObj) {
    // Groups are not yet supported.
    return !linkObj.url.startsWith('/groups');
  },

  _generateSettingsLink() {
    return this.getBaseUrl() + '/settings/';
  }
});
