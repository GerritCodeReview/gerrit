// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  Polymer({
    is: 'gr-main-header',

    hostAttributes: {
      role: 'banner',
    },

    properties: {
      prefs: Object,
      searchQuery: {
        type: String,
        notify: true,
      },

      _account: Object,
      _adminLinks: {
        type: Array,
        value() { return []; },
      },
      _defaultLinks: Array,
      _docBaseUrl: {
        type: String,
        value: null,
      },
      _documentationLinks: Array,
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
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.DocsUrlBehavior,
      Gerrit.LocalisationBehavior,
    ],

    observers: [
      '_accountLoaded(_account)',
    ],

    attached() {
      this._loadPreference();
      this._loadLinks();
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
      if (this.getBaseUrl()) {
        // Strip the canonical path from the path since needing canonical in
        // the path is uneeded and breaks the url.
        this._loginURL = this.getBaseUrl() + '/login/' + encodeURIComponent(
            '/' + window.location.pathname.substring(this.getBaseUrl().length) +
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
      const docLinks = this._getDocLinks(docBaseUrl, this._documentationLinks);
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

    _loadPreference() {
      return this.$.restAPI.getPreferences().then(prefs => {
        return this.prefs = prefs;
      });
    },

    _loadLinks() {
      return this.$.restAPI.getPreferences().then(prefs => {
        const pref = prefs ? prefs.language : 'EN_US';
        this._documentationLinks = [
          {
            url: '/index.html',
            name: this._computeLocalize('tableOfContents', pref),
          },
          {
            url: '/user-search.html',
            name: this._computeLocalize('searching', pref),
          },
          {
            url: '/user-upload.html',
            name: this._computeLocalize('uploading', pref),
          },
          {
            url: '/access-control.html',
            name: this._computeLocalize('accessControl', pref),
          },
          {
            url: '/rest-api.html',
            name: this._computeLocalize('restApi', pref),
          },
          {
            url: '/intro-project-owner.html',
            name: this._computeLocalize('projectOwnerGuide', pref),
          },
        ];

        this._defaultLinks = [{
          title: this._computeLocalize('changes', pref),
          links: [
            {
              url: '/q/status:open',
              name: this._computeLocalize('open', pref),
            },
            {
              url: '/q/status:merged',
              name: this._computeLocalize('merged', pref),
            },
            {
              url: '/q/status:abandoned',
              name: this._computeLocalize('abandoned', pref),
            },
          ],
        }];
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
      return linkObj;
    },

    _isSupportedLink(linkObj) {
      // Groups are not yet supported.
      return !linkObj.url.startsWith('/groups');
    },
  });
})();
