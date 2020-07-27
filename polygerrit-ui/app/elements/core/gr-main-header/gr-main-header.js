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
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../shared/gr-dropdown/gr-dropdown.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-account-dropdown/gr-account-dropdown.js';
import '../gr-smart-search/gr-smart-search.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-main-header_html.js';
import {getBaseUrl, getDocsBaseUrl} from '../../../utils/url-util.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {getAdminLinks} from '../../../utils/admin-nav-util.js';

const DEFAULT_LINKS = [{
  title: 'Changes',
  links: [
    {
      url: '/q/status:open+-is:wip',
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

/**
 * @extends PolymerElement
 */
class GrMainHeader extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-main-header'; }

  static get properties() {
    return {
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
      loginUrl: {
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
    };
  }

  static get observers() {
    return [
      '_accountLoaded(_account)',
    ];
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'banner');
  }

  /** @override */
  attached() {
    super.attached();
    this._loadAccount();
    this._loadConfig();
  }

  /** @override */
  detached() {
    super.detached();
  }

  reload() {
    this._loadAccount();
  }

  _computeRelativeURL(path) {
    return '//' + window.location.host + getBaseUrl() + path;
  }

  _computeLinks(defaultLinks, userLinks, adminLinks, topMenus, docBaseUrl) {
    // Polymer 2: check for undefined
    if ([
      defaultLinks,
      userLinks,
      adminLinks,
      topMenus,
      docBaseUrl,
    ].includes(undefined)) {
      return undefined;
    }

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
      const items = m.items.map(this._fixCustomMenuItem).filter(link =>
        // Ignore GWT project links
        !link.url.includes('${projectName}')
      );
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
  }

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
  }

  _loadAccount() {
    this.loading = true;
    const promises = [
      this.$.restAPI.getAccount(),
      this.$.restAPI.getTopMenus(),
      pluginLoader.awaitPluginsLoaded(),
    ];

    return Promise.all(promises).then(result => {
      const account = result[0];
      this._account = account;
      this.loggedIn = !!account;
      this.loading = false;
      this._topMenus = result[1];

      return getAdminLinks(account,
          this.$.restAPI.getAccountCapabilities.bind(this.$.restAPI),
          this.$.jsAPI.getAdminMenuLinks.bind(this.$.jsAPI))
          .then(res => {
            this._adminLinks = res.links;
          });
    });
  }

  _loadConfig() {
    this.$.restAPI.getConfig()
        .then(config => {
          this._retrieveRegisterURL(config);
          return getDocsBaseUrl(config, this.$.restAPI);
        })
        .then(docBaseUrl => { this._docBaseUrl = docBaseUrl; });
  }

  _accountLoaded(account) {
    if (!account) { return; }

    this.$.restAPI.getPreferences().then(prefs => {
      this._userLinks = prefs && prefs.my ?
        prefs.my.map(this._fixCustomMenuItem) : [];
    });
  }

  _retrieveRegisterURL(config) {
    if (AUTH_TYPES_WITH_REGISTER_URL.has(config.auth.auth_type)) {
      this._registerURL = config.auth.register_url;
      if (config.auth.register_text) {
        this._registerText = config.auth.register_text;
      }
    }
  }

  _computeIsInvisible(registerURL) {
    return registerURL ? '' : 'invisible';
  }

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

    return linkObj;
  }

  _generateSettingsLink() {
    return getBaseUrl() + '/settings/';
  }

  _onMobileSearchTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('mobile-search', {
      composed: true, bubbles: true,
    }));
  }

  _computeLinkGroupClass(linkGroup) {
    if (linkGroup && linkGroup.class) {
      return linkGroup.class;
    }

    return '';
  }
}

customElements.define(GrMainHeader.is, GrMainHeader);
