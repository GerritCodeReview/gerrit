/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-icon/gr-icon';
import '../gr-account-dropdown/gr-account-dropdown';
import '../gr-smart-search/gr-smart-search';
import {getBaseUrl, getDocUrl} from '../../../utils/url-util';
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
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {subscribe} from '../../lit/subscription-controller';

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

// visible for testing
export function getDocLinks(docBaseUrl: string, docLinks: MainHeaderLink[]) {
  if (!docBaseUrl) return [];
  return docLinks.map(link => {
    return {
      url: getDocUrl(docBaseUrl, link.url),
      name: link.name,
      target: '_blank',
    };
  });
}

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
  @property({type: Boolean, reflect: true})
  loggedIn?: boolean;

  @property({type: Boolean, reflect: true})
  loading?: boolean;

  @state() loginUrl = '';

  @state() loginText = '';

  @property({type: Boolean})
  mobileSearchHidden = false;

  // private but used in test
  @state() account?: AccountDetailInfo;

  @state() private adminLinks: NavLink[] = [];

  @state() private docsBaseUrl = '';

  @state() private userLinks: MainHeaderLink[] = [];

  @state() private topMenus?: TopMenuEntryInfo[] = [];

  // private but used in test
  @state() registerText = 'Sign up';

  // Empty string means that the register <div> will be hidden.
  // private but used in test
  @state() registerURL = '';

  // private but used in test
  @state() feedbackURL = '';

  @state() hamburgerClose? = false;

  @query('.nav-sidebar') navSidebar?: HTMLDivElement;

  @query('.modelBackground') modelBackground?: HTMLDivElement;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().myMenuItems$,
      items => (this.userLinks = items.map(this.createHeaderLink))
    );
    subscribe(
      this,
      () => this.getConfigModel().loginUrl$,
      loginUrl => (this.loginUrl = loginUrl)
    );
    subscribe(
      this,
      () => this.getConfigModel().loginText$,
      loginText => (this.loginText = loginText)
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        if (!config) return;
        this.retrieveFeedbackURL(config);
        this.retrieveRegisterURL(config);
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadAccount();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        .hideOnDesktop {
          display: none;
        }

        nav.hideOnMobile {
          align-items: center;
          display: flex;
        }
        nav.hideOnMobile ul {
          list-style: none;
          padding-left: var(--spacing-l);
        }
        nav.hideOnMobile .links > li {
          cursor: default;
          display: inline-block;
          padding: 0;
          position: relative;
        }

        .mobileTitle {
          display: none;
        }

        .bigTitle {
          color: var(--header-text-color);
          font-size: var(--header-title-font-size);
          line-height: calc(var(--header-title-font-size) * 1.2);
          text-decoration: none;
        }
        .bigTitle:hover {
          text-decoration: underline;
        }
        .titleText,
        .mobileTitleText {
          /* Vertical alignment of icons and text with just block/inline display is too troublesome. */
          display: flex;
          align-items: center;
        }
        .titleText::before {
          --icon-width: var(--header-icon-width, var(--header-icon-size, 0));
          --icon-height: var(--header-icon-height, var(--header-icon-size, 0));
          background-image: var(--header-icon);
          background-size: var(--icon-width) var(--icon-height);
          background-repeat: no-repeat;
          content: '';
          /* Any direct child of a flex element implicitly has 'display: block', but let's make that explicit here. */
          display: block;
          width: var(--icon-width);
          height: var(--icon-height);
          /* If size or height are set, then use 'spacing-m', 0px otherwise. */
          margin-right: clamp(0px, var(--icon-height), var(--spacing-m));
        }
        .titleText::after {
          /* The height will be determined by the line-height of the .bigTitle element. */
          content: var(--header-title-content);
          white-space: nowrap;
        }

        .mobileTitleText::before {
          --icon-width: var(
            --header-icon-width,
            var(--header-mobile-icon-size, var(--header-icon-size, 0))
          );
          --icon-height: var(
            --header-icon-height,
            var(--header-mobile-icon-size, var(--header-icon-size, 0))
          );
          background-image: var(--header-mobile-icon, var(--header-icon));
          background-size: var(--mobile-icon-width, var(--icon-width))
            var(--mobile-icon-height, var(--icon-height));
          background-repeat: no-repeat;
          content: '';
          /* Any direct child of a flex element implicitly has 'display: block', but let's make that explicit here. */
          display: block;
          width: var(--mobile-icon-width, var(--icon-width));
          height: var(--mobile-icon-height, var(--icon-height));
          /* If size or height are set, then use 'spacing-m', 0px otherwise. */
          margin-right: clamp(
            0px,
            var(--mobile-icon-height, var(--icon-height)),
            var(--spacing-m)
          );
        }
        .mobileTitleText::after {
          /* The height will be determined by the line-height of the .bigTitle element. */
          content: var(
            --header-mobile-title-content,
            var(--header-title-content)
          );
          white-space: nowrap;
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
        .mobileRightItems {
          align-items: center;
          justify-content: flex-end;

          display: inline-block;
          vertical-align: middle;
          cursor: pointer;
          position: relative;
          top: 0px;
          right: 0px;
          margin-right: 0;
          margin-left: auto;
          min-height: 50px;
          padding-top: 12px;
        }

        .rightItems gr-endpoint-decorator:not(:empty),
        .mobileRightItems gr-endpoint-decorator:not(:empty) {
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
        :host([loggedIn]) .registerButton,
        :host([loggedIn]) .moreMenu {
          display: none;
        }
        :host([loggedIn]) .settingsButton,
        :host([loggedIn]) gr-account-dropdown {
          display: inline;
        }
        :host:not([loggedIn]) .moreMenu {
          display: inline;
        }
        .accountContainer {
          flex: 0 0 auto;
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
          .nav-sidebar {
            background: #fff;
            width: 200px;
            height: 100%;
            display: block;
            position: fixed;
            left: -200px;
            top: 0px;
            transition: left 0.25s ease;
            margin: 0;
            border: 0;
            overflow-y: auto;
            overflow-x: hidden;
            height: 100%;
            margin-bottom: 15px 0;
            box-shadow: 0 2px 5px 0 rgba(0, 0, 0, 0.26);
            border-radius: 3px;
            z-index: 2;
          }
          .nav-sidebar.visible {
            left: 0px;
            transition: left 0.25s ease;
            width: 80%;
            z-index: 200;
          }
          .mobileTitle {
            position: relative;
            display: flex;
            top: 10px;
            font-size: 20px;
            left: 100px;
            right: 100px;
            text-align: center;
            text-overflow: ellipsis;
            overflow: hidden;
            width: 50%;
          }

          .nav-header {
            display: flex;
          }

          .hamburger {
            display: inline-block;
            vertical-align: middle;
            height: 50px;
            cursor: pointer;
            margin: 0;
            position: absolute;
            top: 0;
            left: 0;
            padding: 12px;
            z-index: 200;
          }

          .nav-sidebar ul {
            list-style-type: none;
            margin: 0;
            padding: 0;
            display: block;
            padding-top: 50px;
          }

          .nav-sidebar li {
            list-style-type: none;
            margin: 0;
            padding: 0;
            display: inline-block;
            position: relative;
            font-size: 14;
            color: #def1f0;
            display: block;
          }
          .cover {
            background: rgba(0, 0, 0, 0.5);
            position: fixed;
            top: 0;
            bottom: 0;
            left: 0;
            right: 0;
            overflow: none;
            z-index: 199;
          }
          .hideOnDesktop {
            display: block;
          }
          nav.hideOnMobile {
            display: none;
          }

          .links li:not(:last-child) {
            border-bottom: 1px solid rgba(0, 0, 0, 0.1);
          }
          .links li {
            margin: 0 10px;
            position: relative;
          }
          nav.hideOnDesktop ul li gr-dropdown {
            width: 100%;
          }

          nav.hideOnDesktop .mobileRightItems gr-dropdown {
            padding: 0 var(--spacing-m);
            --gr-button-text-color: var(--header-text-color);
            --gr-dropdown-item-color: var(--primary-text-color);
          }
        }
      `,
    ];
  }

  override render() {
    return html` ${this.renderDesktop()} ${this.renderMobile()} `;
  }

  private renderDesktop() {
    return html`
    <nav class="hideOnMobile">
    <a href=${`//${window.location.host}${getBaseUrl()}/`} class="bigTitle">
      <gr-endpoint-decorator name="header-title">
        <div class="titleText"></div>
      </gr-endpoint-decorator>
    </a>
    <ul class="links">
      ${this.computeLinks(this.userLinks, this.adminLinks, this.topMenus).map(
        linkGroup => this.renderLinkGroup(linkGroup, 'left')
      )}
    </ul>
    <div class="rightItems">
      <gr-endpoint-decorator
        class="hideOnMobile"
        name="header-small-banner"
      ></gr-endpoint-decorator>
      <gr-smart-search id="search"></gr-smart-search>
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

  private renderMobile() {
    const moreMenu: MainHeaderLink[] = [
      {
        name: this.registerText,
        url: this.registerURL,
      },
      {
        name: this.loginText,
        url: this.loginUrl,
      },
    ];
    if (!this.registerURL) {
      moreMenu.shift();
    }
    if (this.feedbackURL) {
      moreMenu.push({
        name: 'Feedback',
        url: this.feedbackURL,
        external: true,
      });
    }

    return html`
      <nav class="hideOnDesktop">
        <div class="nav-sidebar">
          <ul class="links">
            ${this.computeLinks(
              this.userLinks,
              this.adminLinks,
              this.topMenus,
              true
            ).map(linkGroup => this.renderLinkGroup(linkGroup, 'center'))}
          </ul>
        </div>
        <div class="nav-header">
          <a
            class="hamburger"
            href=""
            title="Hamburger"
            aria-label="${!this.hamburgerClose ? 'Open' : 'Close'} hamburger"
            role="button"
            @click=${() => {
              this.handleHamburger();
            }}
          >
            ${!this.hamburgerClose
              ? html`<gr-icon icon="menu" filled></gr-icon>`
              : html`<gr-icon icon="menu_open" filled></gr-icon>`}
          </a>
          <a
            href=${`//${window.location.host}${getBaseUrl()}/`}
            class="mobileTitle bigTitle"
          >
            <gr-endpoint-decorator name="header-mobile-title">
              <div class="mobileTitleText"></div>
            </gr-endpoint-decorator>
          </a>
          <div class="mobileRightItems">
            <a
              class="searchButton"
              href=""
              title="Search"
              @click=${(e: Event) => {
                this.onMobileSearchTap(e);
              }}
              role="button"
              aria-label=${this.mobileSearchHidden
                ? 'Show Searchbar'
                : 'Hide Searchbar'}
            >
              <gr-icon icon="search" filled></gr-icon>
            </a>
            <gr-dropdown
              class="moreMenu"
              link=""
              .items=${moreMenu}
              horizontal-align="center"
            >
              <span class="linksTitle">
                <gr-icon icon="more_horiz" filled></gr-icon>
              </span>
            </gr-dropdown>
            ${this.renderAccountDropdown(true)}
          </div>
        </div>
      </nav>
      <div
        class="modelBackground"
        @click=${this.handleCloseHamburger}
        @scroll=${this.handleStopScroll}
      ></div>
    `;
  }

  private renderLinkGroup(
    linkGroup: MainHeaderLinkGroup,
    horizontalAlign: string
  ) {
    return html`
      <li class=${linkGroup.class ?? ''}>
        <gr-dropdown
          link
          down-arrow
          .items=${linkGroup.links}
          horizontal-align=${horizontalAlign}
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
        rel="noopener noreferrer"
        role="button"
      >
        <gr-icon icon="bug_report" filled></gr-icon>Feedback
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
        <gr-endpoint-decorator name="auth-link">
          <a class="loginButton" href=${this.loginUrl}>${this.loginText}</a>
        </gr-endpoint-decorator>
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

  private renderAccountDropdown(showOnMobile?: boolean) {
    if (!this.account) return;

    return html`
      <gr-account-dropdown
        .account=${this.account}
        ?showMobile=${showOnMobile}
      ></gr-account-dropdown>
    `;
  }

  override firstUpdated(changedProperties: PropertyValues) {
    super.firstUpdated(changedProperties);
    if (!this.getAttribute('role')) this.setAttribute('role', 'banner');
  }

  handleHamburger() {
    if (!this.navSidebar?.classList.contains('visible')) {
      this.navSidebar?.classList.add('visible');
    } else {
      this.navSidebar?.classList.remove('visible');
    }
    if (!this.modelBackground?.classList.contains('cover')) {
      this.modelBackground?.classList.add('cover');
      if (document.getElementsByTagName('html')) {
        document.getElementsByTagName('html')[0].style.overflow = 'hidden';
      }
    } else {
      this.modelBackground?.classList.remove('cover');
      if (document.getElementsByTagName('html')) {
        document.getElementsByTagName('html')[0].style.overflow = '';
      }
    }

    if (!this.hamburgerClose) {
      this.hamburgerClose = true;
    } else {
      this.hamburgerClose = false;
    }
  }

  handleCloseHamburger() {
    if (this.modelBackground?.classList.contains('cover')) {
      this.modelBackground?.classList.remove('cover');
      this.navSidebar?.classList.remove('visible');
      if (document.getElementsByTagName('html')) {
        document.getElementsByTagName('html')[0].style.overflow = '';
      }
      this.hamburgerClose = false;
    }
  }

  handleStopScroll(e: Event) {
    e.preventDefault();
  }

  reload() {
    this.loadAccount();
  }

  // private but used in test
  computeLinks(
    userLinks?: MainHeaderLink[],
    adminLinks?: NavLink[],
    topMenus?: TopMenuEntryInfo[],
    isMobile?: boolean,
    // defaultLinks parameter is used in tests only
    defaultLinks = DEFAULT_LINKS
  ) {
    if (
      userLinks === undefined ||
      adminLinks === undefined ||
      topMenus === undefined
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
    const docLinks = getDocLinks(this.docsBaseUrl, DOCUMENTATION_LINKS);
    if (docLinks.length) {
      links.push({
        title: 'Documentation',
        links: docLinks,
        class: isMobile ? undefined : 'hideOnMobile',
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
