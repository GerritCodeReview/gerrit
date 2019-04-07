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

  // Set of authentication methods that can provide custom registration page.
  const AUTH_TYPES_WITH_REGISTER_URL = new Set([
    'LDAP',
    'LDAP_BIND',
    'CUSTOM_EXTENSION',
  ]);

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
            '_topMenus, _docBaseUrl)',
      },
      _loginURL: {
        type: String,
        value: '/login',
      },
      _userLinks: {
        type: Array,
        value() { return []; },
      },
      _topMenus: {
        type: Array,
        value() { return []; },
      },
      _registerText: {
        type: String,
        value: 'Sign up',
      },
      _registerURL: {
        type: String,
        value: null,
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

    toggleDrawer() {
      this.$.drawer.toggle();
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

    _computeLinks(defaultLinks, userLinks, adminLinks, topMenus, docBaseUrl) {
      const links = defaultLinks.map(menu => {
        return {
          title: menu.title,
          links: menu.links.slice(),
        };
      });
      if (userLinks && userLinks.length > 0) {
        links.push({
          title: 'Your',
          links: userLinks.slice(),
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
        links: adminLinks.slice(),
      });
      const topMenuLinks = [];
      links.forEach(link => { topMenuLinks[link.title] = link.links; });
      for (const m of topMenus) {
        const items = m.items.map(this._fixCustomMenuItem);
        if (m.name in topMenuLinks) {
          items.forEach(link => { topMenuLinks[m.name].push(link); });
        } else {
          links.push({
            title: m.name,
            links: topMenuLinks[m.name] = items,
          });
        }
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
      this.loading = true;
      const promises = [
        this.$.restAPI.getAccount(),
        this.$.restAPI.getTopMenus(),
        Gerrit.awaitPluginsLoaded(),
      ];

      return Promise.all(promises).then(result => {
        const account = result[0];
        this._account = account;
        this.loggedIn = !!account;
        this.loading = false;
        this._topMenus = result[1];

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
          .then(config => {
            this._retrieveRegisterURL(config);
            return this.getDocsBaseUrl(config, this.$.restAPI);
          })
          .then(docBaseUrl => { this._docBaseUrl = docBaseUrl; });
    },

    _accountLoaded(account) {
      if (!account) { return; }

      this.$.restAPI.getPreferences().then(prefs => {
        this._userLinks =
            prefs.my.map(this._fixCustomMenuItem).filter(this._isSupportedLink);
      });
    },

    _retrieveRegisterURL(config) {
      if (AUTH_TYPES_WITH_REGISTER_URL.has(config.auth.auth_type)) {
        this._registerURL = config.auth.register_url;
        if (config.auth.register_text) {
          this._registerText = config.auth.register_text;
        }
      }
    },

    _computeIsInvisible(registerURL) {
      return registerURL ? '' : 'invisible';
    },

    _fixCustomMenuItem(linkObj) {
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

      // Because the user provided links may be arbitrary URLs, we don't know
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
    },

    /**
     * Build a URL for the given host and path. If there is a base URL, it will
     * be included between the host and the path.
     * @param {!string} host
     * @param {!string} path
     * @return {!string} The scheme-relative URL.
     */
    _computeURLHelper(host, path) {
      return '//' + host + this.getBaseUrl() + path;
    },

    /**
     * Build a scheme-relative URL for the current host. Will include the base
     * URL if one is present. Note: the URL will be scheme-relative but absolute
     * with regard to the host.
     * @param {!string} path The path for the URL.
     * @return {!string} The scheme-relative URL.
     */
    _computeRelativeURL(path) {
      const host = window.location.host;
      return this._computeURLHelper(host, path);
    },

    /**
     * Compute the URL for a link object.
     * @param {!Object} link The object describing the link.
     * @return {!string} The URL.
     */
    _computeLinkURL(link) {
      if (typeof link.url === 'undefined') {
        return '';
      }
      if (link.target || !link.url.startsWith('/')) {
        return link.url;
      }
      return this._computeRelativeURL(link.url);
    },

    /**
     * Compute the value for the rel attribute of an anchor for the given link
     * object. If the link has a target value, then the rel must be "noopener"
     * for security reasons.
     * @param {!Object} link The object describing the link.
     * @return {?string} The rel value for the link.
     */
    _computeLinkRel(link) {
      // Note: noopener takes precedence over external.
      if (link.target) { return REL_NOOPENER; }
      if (link.external) { return REL_EXTERNAL; }
      return null;
    },

    /**
     * Handle a click on an item of the dropdown.
     * @param {!Event} e
     */
    _handleItemTap(e) {
      const id = e.target.getAttribute('data-id');
      const item = this.items.find(item => item.id === id);
      if (id && !this.disabledIds.includes(id)) {
        if (item) {
          this.dispatchEvent(new CustomEvent('tap-item', {detail: item}));
        }
        this.dispatchEvent(new CustomEvent('tap-item-' + id));
      }
    },

    /**
     * If a dropdown item is shown as a button, get the class for the button.
     * @param {string} id
     * @param {!Object} disabledIdsRecord The change record for the disabled IDs
     *     list.
     * @return {!string} The class for the item button.
     */
    _computeDisabledClass(id, disabledIdsRecord) {
      return disabledIdsRecord.base.includes(id) ? 'disabled' : '';
    },
  });
})();
