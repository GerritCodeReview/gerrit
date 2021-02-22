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
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-icons/gr-icons';
import '../gr-account-dropdown/gr-account-dropdown';
import '../gr-smart-search/gr-smart-search';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-main-header_html';
import {getBaseUrl, getDocsBaseUrl} from '../../../utils/url-util';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {getAdminLinks, NavLink} from '../../../utils/admin-nav-util';
import {customElement, property, observe} from '@polymer/decorators';
import {
  AccountDetailInfo,
  RequireProperties,
  ServerInfo,
  TopMenuEntryInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {AuthType} from '../../../constants/constants';
import {DropdownLink} from '../../shared/gr-dropdown/gr-dropdown';
import {appContext} from '../../../services/app-context';

type MainHeaderLink = RequireProperties<DropdownLink, 'url' | 'name'>;

interface MainHeaderLinkGroup {
  title: string;
  links: MainHeaderLink[];
  class?: string;
}

const DEFAULT_LINKS: MainHeaderLinkGroup[] = [
  {
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
  },
];

const DOCUMENTATION_LINKS: MainHeaderLink[] = [
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
const AUTH_TYPES_WITH_REGISTER_URL: Set<AuthType> = new Set([
  AuthType.LDAP,
  AuthType.LDAP_BIND,
  AuthType.CUSTOM_EXTENSION,
]);

@customElement('gr-main-header')
export class GrMainHeader extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, notify: true})
  searchQuery?: string;

  @property({type: Boolean, reflectToAttribute: true})
  loggedIn?: boolean;

  @property({type: Boolean, reflectToAttribute: true})
  loading?: boolean;

  @property({type: Object})
  _account?: AccountDetailInfo;

  @property({type: Array})
  _adminLinks: NavLink[] = [];

  @property({type: String})
  _docBaseUrl: string | null = null;

  @property({
    type: Array,
    computed: '_computeLinks(_userLinks, _adminLinks, _topMenus, _docBaseUrl)',
  })
  _links?: MainHeaderLinkGroup[];

  @property({type: String})
  loginUrl = '/login';

  @property({type: Array})
  _userLinks: MainHeaderLink[] = [];

  @property({type: Array})
  _topMenus?: TopMenuEntryInfo[] = [];

  @property({type: String})
  _registerText = 'Sign up';

  // Empty string means that the register <div> will be hidden.
  @property({type: String})
  _registerURL = '';

  @property({type: String})
  _feedbackURL = '';

  @property({type: Boolean})
  mobileSearchHidden = false;

  private readonly restApiService = appContext.restApiService;

  private readonly jsAPI = appContext.jsApiService;

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

  _computeRelativeURL(path: string) {
    return '//' + window.location.host + getBaseUrl() + path;
  }

  _computeLinks(
    userLinks?: TopMenuItemInfo[],
    adminLinks?: NavLink[],
    topMenus?: TopMenuEntryInfo[],
    docBaseUrl?: string | null,
    // defaultLinks parameter is used in tests only
    defaultLinks = DEFAULT_LINKS
  ) {
    // Polymer 2: check for undefined
    if (
      userLinks === undefined ||
      adminLinks === undefined ||
      topMenus === undefined ||
      docBaseUrl === undefined
    ) {
      return undefined;
    }

    const links: MainHeaderLinkGroup[] = defaultLinks.map(menu => {
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
    const topMenuLinks: {[name: string]: MainHeaderLink[]} = {};
    links.forEach(link => {
      topMenuLinks[link.title] = link.links;
    });
    for (const m of topMenus) {
      const items = m.items.map(this._createHeaderLink).filter(
        link =>
          // Ignore GWT project links
          !link.url.includes('${projectName}')
      );
      if (m.name in topMenuLinks) {
        items.forEach(link => {
          topMenuLinks[m.name].push(link);
        });
      } else {
        links.push({
          title: m.name,
          links: topMenuLinks[m.name] = items,
        });
      }
    }
    return links;
  }

  _getDocLinks(docBaseUrl: string | null, docLinks: MainHeaderLink[]) {
    if (!docBaseUrl) {
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

    return Promise.all([
      this.restApiService.getAccount(),
      this.restApiService.getTopMenus(),
      getPluginLoader().awaitPluginsLoaded(),
    ]).then(result => {
      const account = result[0];
      this._account = account;
      this.loggedIn = !!account;
      this.loading = false;
      this._topMenus = result[1];

      return getAdminLinks(
        account,
        () =>
          this.restApiService.getAccountCapabilities().then(capabilities => {
            if (!capabilities) {
              throw new Error('getAccountCapabilities returns undefined');
            }
            return capabilities;
          }),
        () => this.jsAPI.getAdminMenuLinks()
      ).then(res => {
        this._adminLinks = res.links;
      });
    });
  }

  _loadConfig() {
    this.restApiService
      .getConfig()
      .then(config => {
        if (!config) {
          throw new Error('getConfig returned undefined');
        }
        this._retrieveFeedbackURL(config);
        this._retrieveRegisterURL(config);
        return getDocsBaseUrl(config, this.restApiService);
      })
      .then(docBaseUrl => {
        this._docBaseUrl = docBaseUrl;
      });
  }

  @observe('_account')
  _accountLoaded(account?: AccountDetailInfo) {
    if (!account) {
      return;
    }

    this.restApiService.getPreferences().then(prefs => {
      this._userLinks =
        prefs && prefs.my ? prefs.my.map(this._createHeaderLink) : [];
    });
  }

  _retrieveFeedbackURL(config: ServerInfo) {
    if (config.gerrit?.report_bug_url) {
      this._feedbackURL = config.gerrit.report_bug_url;
    }
  }

  _retrieveRegisterURL(config: ServerInfo) {
    if (AUTH_TYPES_WITH_REGISTER_URL.has(config.auth.auth_type)) {
      this._registerURL = config.auth.register_url ?? '';
      if (config.auth.register_text) {
        this._registerText = config.auth.register_text;
      }
    }
  }

  _computeRegisterHidden(registerURL: string) {
    return !registerURL;
  }

  _createHeaderLink(linkObj: TopMenuItemInfo): MainHeaderLink {
    // Delete target property due to complications of
    // https://bugs.chromium.org/p/gerrit/issues/detail?id=5888
    //
    // The server tries to guess whether URL is a view within the UI.
    // If not, it sets target='_blank' on the menu item. The server
    // makes assumptions that work for the GWT UI, but not PolyGerrit,
    // so we'll just disable it altogether for now.
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const {target, ...headerLink} = {...linkObj};

    // Normalize all urls to PolyGerrit style.
    if (headerLink.url.startsWith('#')) {
      headerLink.url = linkObj.url.slice(1);
    }

    return headerLink;
  }

  _generateSettingsLink() {
    return getBaseUrl() + '/settings/';
  }

  _onMobileSearchTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('mobile-search', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _computeLinkGroupClass(linkGroup: MainHeaderLinkGroup) {
    return linkGroup.class ?? '';
  }

  _computeShowHideAriaLabel(mobileSearchHidden: boolean) {
    if (mobileSearchHidden) {
      return 'Show Searchbar';
    } else {
      return 'Hide Searchbar';
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-main-header': GrMainHeader;
  }
}
