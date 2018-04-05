/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

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
    is: 'gr-main-header',

    hostAttributes: {
      role: 'banner',
    },

    properties: {
      searchQuery: {
        type: String,
        notify: true,
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
        computed: '_computeLinks(_defaultLinks, _userLinks, _docBaseUrl)',
      },
      _loginURL: {
        type: String,
        value: '/login',
      },
      _userLinks: {
        type: Array,
        value() { return []; },
      },
      _serverConfig: Object,
    },

    behaviors: [
      Gerrit.AnonymousNameBehavior,
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

    _computeLinks(defaultLinks, userLinks, docBaseUrl) {
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
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
        this.$.accountContainer.classList.toggle('loggedIn', account != null);
        this.$.accountContainer.classList.toggle('loggedOut', account == null);
      });
    },

    _loadConfig() {
      this.$.restAPI.getConfig()
          .then(config => {
            this._serverConfig = config;
            return this.getDocsBaseUrl(config, this.$.restAPI);
          })
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
  });
})();
