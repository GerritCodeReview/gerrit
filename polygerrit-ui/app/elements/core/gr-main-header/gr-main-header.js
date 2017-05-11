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

  const ADMIN_LINKS = [
    {
      url: '/admin/groups',
      name: 'Groups',
    },
    {
      url: '/admin/create-group',
      name: 'Create Group',
      capability: 'createGroup',
    },
    {
      url: '/admin/projects',
      name: 'Projects',
      viewableToAll: true,
    },
    {
      url: '/admin/create-project',
      name: 'Create Project',
      capability: 'createProject',
    },
    {
      url: '/admin/plugins',
      name: 'Plugins',
      capability: 'viewPlugins',
    },
    {
      url: '/admin/people',
      name: 'People',
      capability: 'modifyAccount',
    },
  ];

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
      Gerrit.BaseUrlBehavior,
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

    _computeLinks(defaultLinks, userLinks, adminLinks, docBaseUrl) {
      const links = defaultLinks.slice();
      if (userLinks && userLinks.length > 0) {
        links.push({
          title: 'Your',
          links: userLinks,
        });
      }
      if (!adminLinks || !adminLinks.length) {
        adminLinks = ADMIN_LINKS.filter(link => link.viewableToAll);
      }
      const docLinks = this._getDocLinks(docBaseUrl, DOCUMENTATION_LINKS);
      if (docLinks.length) {
        links.push({
          title: 'Documentation',
          links: docLinks,
        });
      }
      links.push({
        title: 'More',
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
      return this.$.restAPI.getAccount().then(account => {
        this._account = account;
        this.$.accountContainer.classList.toggle('loggedIn', account != null);
        this.$.accountContainer.classList.toggle('loggedOut', account == null);
      });
    },

    _loadConfig() {
      this.$.restAPI.getConfig().then(config => {
        if (config && config.gerrit && config.gerrit.doc_url) {
          this._docBaseUrl = config.gerrit.doc_url;
        }
        if (!this._docBaseUrl) {
          return this._probeDocLink('/Documentation/index.html');
        }
      });
    },

    _probeDocLink(path) {
      return this.$.restAPI.probePath(this.getBaseUrl() + path).then(ok => {
        if (ok) {
          this._docBaseUrl = this.getBaseUrl() + '/Documentation';
        } else {
          this._docBaseUrl = null;
        }
      });
    },

    _accountLoaded(account) {
      if (!account) { return; }

      this.$.restAPI.getPreferences().then(prefs => {
        this._userLinks =
            prefs.my.map(this._fixMyMenuItem).filter(this._isSupportedLink);
      });
      this._loadAccountCapabilities();
    },

    _loadAccountCapabilities() {
      const params =
          ['createProject', 'createGroup', 'viewPlugins', 'modifyAccount'];
      return this.$.restAPI.getAccountCapabilities(params)
          .then(capabilities => {
            this._adminLinks = ADMIN_LINKS.filter(link => {
              return !link.capability ||
              capabilities.hasOwnProperty(link.capability);
            });
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
