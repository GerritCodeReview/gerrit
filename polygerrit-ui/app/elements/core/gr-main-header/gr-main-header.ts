/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Subscription} from 'rxjs';
import {map, distinctUntilChanged} from 'rxjs/operators';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-icon/gr-icon';
import '../gr-account-dropdown/gr-account-dropdown';
import '../gr-smart-search/gr-smart-search';
import {getBaseUrl} from '../../../utils/url-util';
import {getAdminLinks, NavLink} from '../../../models/views/admin';
import {
  AccountDetailInfo,
  DropdownLink,
  RequireProperties,
  ServerInfo,
  TopMenuEntryInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {AuthType} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

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

declare global {
  interface HTMLElementTagNameMap {
    'gr-main-header': GrMainHeader;
  }
  interface HTMLElementEventMap {
    'mobile-search': CustomEvent<{}>;
  }
}

@customElement('gr-main-header')
export class GrMainHeader extends LitElement {
  @property({type: String})
  searchQuery = '';

  @property({type: Boolean, reflect: true})
  loggedIn?: boolean;

  @property({type: Boolean, reflect: true})
  loading?: boolean;

  @property({type: String})
  loginUrl = '/login';

  @property({type: Boolean})
  mobileSearchHidden = false;

  // private but used in test
  @state() account?: AccountDetailInfo;

  @state() private adminLinks: NavLink[] = [];

  @state() private docBaseUrl: string | null = null;

  @state() private userLinks: MainHeaderLink[] = [];

  @state() private topMenus?: TopMenuEntryInfo[] = [];

  // private but used in test
  @state() registerText = 'Sign up';

  // Empty string means that the register <div> will be hidden.
  // private but used in test
  @state() registerURL = '';

  // private but used in test
  @state() feedbackURL = '';

  @state() private serverConfig?: ServerInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private subscriptions: Subscription[] = [];

  override connectedCallback() {
    super.connectedCallback();
    this.loadAccount();

    this.subscriptions.push(
      this.getUserModel()
        .preferences$.pipe(
          map(preferences => preferences?.my ?? []),
          distinctUntilChanged()
        )
        .subscribe(items => {
          this.userLinks = items.map(this.createHeaderLink);
        })
    );
    this.subscriptions.push(
      this.getConfigModel().serverConfig$.subscribe(config => {
        if (!config) return;
        this.serverConfig = config;
        this.retrieveFeedbackURL(config);
        this.retrieveRegisterURL(config);
        this.restApiService.getDocsBaseUrl(config).then(docBaseUrl => {
          this.docBaseUrl = docBaseUrl;
        });
      })
    );
  }

  override disconnectedCallback() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
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
          --icon-width: var(--header-icon-width, var(--header-icon-size, 0));
          --icon-height: var(--header-icon-height, var(--header-icon-size, 0));
          background-image: var(--header-icon);
          background-size: var(--icon-width) var(--icon-height);
          background-repeat: no-repeat;
          content: '';
          display: inline-block;
          height: var(--icon-height);
          /* If size or height are set, then use 'spacing-m', 0px otherwise. */
          margin-right: clamp(0px, var(--icon-height), var(--spacing-m));
          vertical-align: text-bottom;
          width: var(--icon-width);
        }
        .titleText::after {
          content: var(--header-title-content);
          white-space: nowrap;
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
        :host([loggedIn]) .loginButton,
        :host([loggedIn]) .registerButton {
          display: none;
        }
        :host([loggedIn]) .settingsButton,
        :host([loggedIn]) gr-account-dropdown {
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
        gr-icon,
        gr-dropdown,
        gr-account-dropdown {
          --gr-button-text-color: var(--header-text-color);
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
      `,
    ];
  }

  override render() {
    return html`
  <nav>
    <a href=${`//${window.location.host}${getBaseUrl()}/`} class="bigTitle">
      <gr-endpoint-decorator name="header-title">
        <span class="titleText"></span>
      </gr-endpoint-decorator>
    </a>
    <ul class="links">
      ${this.computeLinks(
        this.userLinks,
        this.adminLinks,
        this.topMenus,
        this.docBaseUrl
      ).map(linkGroup => this.renderLinkGroup(linkGroup))}
    </ul>
    <div class="rightItems">
      <gr-endpoint-decorator
        class="hideOnMobile"
        name="header-small-banner"
      ></gr-endpoint-decorator>
      <gr-smart-search
        id="search"
        label="Search for changes"
        .searchQuery=${this.searchQuery}
        .serverConfig=${this.serverConfig}
      ></gr-smart-search>
      <gr-endpoint-decorator
        class="hideOnMobile"
        name="header-top-right"
      ></gr-endpoint-decorator>
      <gr-endpoint-decorator class="feedbackButton" name="header-feedback">
        ${this.renderFeedback()}
      </gr-endpoint-decorator>
      </div>
      ${this.renderAccount()}
    </div>
  </nav>
    `;
  }

  private renderLinkGroup(linkGroup: MainHeaderLinkGroup) {
    return html`
      <li class=${linkGroup.class ?? ''}>
        <gr-dropdown
          link
          down-arrow
          .items=${linkGroup.links}
          horizontal-align="left"
        >
          <span class="linksTitle" id=${linkGroup.title}>
            ${linkGroup.title}
          </span>
        </gr-dropdown>
      </li>
    `;
  }

  private renderFeedback() {
    if (!this.feedbackURL) return;

    return html`
      <a
        href=${this.feedbackURL}
        title="File a bug"
        aria-label="File a bug"
        target="_blank"
        role="button"
      >
        <gr-icon icon="bug_report" filled></gr-icon>
      </a>
    `;
  }

  private renderAccount() {
    return html`
      <div class="accountContainer" id="accountContainer">
        <div>
          <gr-icon
            id="mobileSearch"
            icon="search"
            @click=${(e: Event) => {
              this.onMobileSearchTap(e);
            }}
            role="button"
            aria-label=${this.mobileSearchHidden
              ? 'Show Searchbar'
              : 'Hide Searchbar'}
          ></gr-icon>
        </div>
        ${this.renderRegister()}
        <a class="loginButton" href=${this.loginUrl}>Sign in</a>
        <a
          class="settingsButton"
          href="${getBaseUrl()}/settings/"
          title="Settings"
          aria-label="Settings"
          role="button"
        >
          <gr-icon icon="settings" filled></gr-icon>
        </a>
        ${this.renderAccountDropdown()}
      </div>
    `;
  }

  private renderRegister() {
    if (!this.registerURL) return;

    return html`
      <div class="registerDiv">
        <a class="registerButton" href=${this.registerURL}>
          ${this.registerText}
        </a>
      </div>
    `;
  }

  private renderAccountDropdown() {
    if (!this.account) return;

    return html`
      <gr-account-dropdown .account=${this.account}></gr-account-dropdown>
    `;
  }

  override firstUpdated(changedProperties: PropertyValues) {
    super.firstUpdated(changedProperties);
    if (!this.getAttribute('role')) this.setAttribute('role', 'banner');
  }

  reload() {
    this.loadAccount();
  }

  // private but used in test
  computeLinks(
    userLinks?: MainHeaderLink[],
    adminLinks?: NavLink[],
    topMenus?: TopMenuEntryInfo[],
    docBaseUrl?: string | null,
    // defaultLinks parameter is used in tests only
    defaultLinks = DEFAULT_LINKS
  ) {
    if (
      userLinks === undefined ||
      adminLinks === undefined ||
      topMenus === undefined ||
      docBaseUrl === undefined
    ) {
      return [];
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
    const docLinks = this.getDocLinks(docBaseUrl, DOCUMENTATION_LINKS);
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
      const items = m.items.map(this.createHeaderLink).filter(
        link =>
          // Ignore GWT project links
          !link.url.includes('${projectName}')
      );
      if (m.name in topMenuLinks) {
        items.forEach(link => {
          topMenuLinks[m.name].push(link);
        });
      } else if (items.length > 0) {
        links.push({
          title: m.name,
          links: (topMenuLinks[m.name] = items),
        });
      }
    }
    return links;
  }

  // private but used in test
  getDocLinks(docBaseUrl: string | null, docLinks: MainHeaderLink[]) {
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

  // private but used in test
  loadAccount() {
    this.loading = true;

    return Promise.all([
      this.restApiService.getAccount(),
      this.restApiService.getTopMenus(),
      this.getPluginLoader().awaitPluginsLoaded(),
    ]).then(result => {
      const account = result[0];
      this.account = account;
      this.loggedIn = !!account;
      this.loading = false;
      this.topMenus = result[1];

      return getAdminLinks(
        account,
        () =>
          this.restApiService.getAccountCapabilities().then(capabilities => {
            if (!capabilities) {
              throw new Error('getAccountCapabilities returns undefined');
            }
            return capabilities;
          }),
        () => this.getPluginLoader().jsApiService.getAdminMenuLinks()
      ).then(res => {
        this.adminLinks = res.links;
      });
    });
  }

  // private but used in test
  retrieveFeedbackURL(config: ServerInfo) {
    if (config.gerrit?.report_bug_url) {
      this.feedbackURL = config.gerrit.report_bug_url;
    }
  }

  // private but used in test
  retrieveRegisterURL(config: ServerInfo) {
    if (AUTH_TYPES_WITH_REGISTER_URL.has(config.auth.auth_type)) {
      this.registerURL = config.auth.register_url ?? '';
      if (config.auth.register_text) {
        this.registerText = config.auth.register_text;
      }
    }
  }

  // private but used in test
  createHeaderLink(linkObj: TopMenuItemInfo): MainHeaderLink {
    // Delete target property due to complications of
    // https://issues.gerritcodereview.com/issues/40006107
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

  private onMobileSearchTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'mobile-search', {});
  }
}
