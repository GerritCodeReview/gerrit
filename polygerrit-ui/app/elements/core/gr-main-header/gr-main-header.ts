/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../shared/gr-dropdown/gr-dropdown';
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
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {subscribe} from '../../lit/subscription-controller';
import {ifDefined} from 'lit/directives/if-defined.js';
import '@material/web/divider/divider';
import '@material/web/list/list';
import '@material/web/list/list-item';
import '@material/web/labs/item/item';
import '@material/web/icon/icon';
import '@material/web/iconbutton/icon-button';

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

const REL_NOOPENER = 'noopener';
const REL_EXTERNAL = 'external';

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
          display: block;
          flex: 1 1 auto;
          flex-grow: 0;
          justify-content: center;
          overflow: hidden;
          max-width: 30%;
          text-decoration: none;
        }
        .bigTitle:hover {
          text-decoration: underline;
        }
        .mobileTitleWrapper {
          position: absolute;
          left: 100px;
          right: 100px;
          overflow: hidden;
          display: flex;
          justify-content: center;
        }
        .titleText {
          display: flex;
          align-items: center;
          min-width: 0;
          overflow: hidden;
        }
        .titleText::before {
          --icon-width: var(--header-icon-width, var(--header-icon-size, 0));
          --icon-height: var(--header-icon-height, var(--header-icon-size, 0));
          background-image: var(--header-icon);
          background-size: var(--icon-width) var(--icon-height);
          background-repeat: no-repeat;
          content: '';
          /* Any direct child of a flex element implicitly has 'display: block', but let's make that explicit here. */
          display: inline-block;
          width: var(--mobile-icon-width, var(--icon-width));
          height: var(--mobile-icon-height, var(--icon-height));
          /* If size or height are set, then use 'spacing-m', 0px otherwise. */
          margin-right: clamp(
            0px,
            var(--mobile-icon-height, var(--icon-height)),
            var(--spacing-m)
          );
          flex-shrink: 0;
        }
        .titleText::after {
          /* The height will be determined by the line-height of the .bigTitle element. */
          content: var(--header-title-content);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          flex: 1;
          min-width: 0;
        }

        .linksTitle {
          display: inline-block;
          font-weight: var(--font-weight-medium);
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
          display: inline-flex;
          vertical-align: middle;
          position: relative;
          top: 0px;
          right: 0px;
          margin-right: 0;
          margin-left: auto;
          min-height: 50px;
        }
        .rightItems gr-endpoint-decorator:not(:empty),
        .mobileRightItems gr-endpoint-decorator:not(:empty) {
          margin-left: var(--spacing-s);
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
        .browse {
          color: var(--header-text-color);
          /* Same as gr-button */
          margin: 5px 4px;
          text-decoration: none;
        }
        .invisible,
        gr-account-dropdown,
        .settingsButton {
          display: none;
        }
        :host([loading]) .accountContainer,
        :host([loggedIn]) .loginButton,
        :host([loggedIn]) .registerButton,
        :host([loggedIn]) .moreMenu {
          display: none;
        }
        :host([loggedIn]) .settingsButton {
          display: flex;
        }
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
        gr-dropdown,
        gr-account-dropdown {
          --gr-button-text-color: var(--header-text-color);
          color: var(--header-text-color);
        }
        .hamburger-open {
          --gr-button-text-color: var(--primary-text-color);
          color: var(--primary-text-color);
        }
        md-icon-button {
          --md-sys-color-on-surface-variant: var(--header-text-color);
        }
        md-icon[filled] {
          font-variation-settings: 'FILL' 1;
        }
        nav.hideOnMobile,
        nav .nav-header {
          border-bottom: var(--header-border-bottom);
          border-image: var(--header-border-image);
          box-shadow: var(--header-box-shadow);
          padding: var(--header-padding);
        }
        @media screen and (max-width: 50em) {
          .bigTitle {
            font-family: var(--header-font-family);
            font-size: 20px;
            font-weight: var(--font-weight-h3);
            line-height: var(--line-height-h3);
            min-width: 0;
            max-width: unset;
          }
          .titleText::before {
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
            width: var(--mobile-icon-width, var(--icon-width));
            height: var(--mobile-icon-height, var(--icon-height));
          }
          .titleText::after {
            /* The height will be determined by the line-height of the .bigTitle element. */
            content: var(
              --header-mobile-title-content,
              var(--header-title-content)
            );
          }
          gr-smart-search,
          .browse,
          .rightItems .hideOnMobile,
          .links > li.hideOnMobile {
            display: none;
          }
          .accountContainer {
            margin-left: var(--spacing-m) !important;
          }
          gr-dropdown {
            padding: 0 var(--spacing-m);
          }
          .nav-sidebar {
            background: var(--table-header-background-color);
            width: 200px;
            height: 100%;
            display: block;
            position: absolute;
            left: -200px;
            transition: left 0.25s ease;
            overflow-y: auto;
            overflow-x: hidden;
            box-shadow: 0 2px 5px 0 rgba(0, 0, 0, 0.26);
            z-index: 2;
            padding-bottom: 56px;
          }
          .nav-sidebar.visible {
            left: 0px;
            transition: left 0.25s ease;
            width: 50%;
            z-index: 200;
            box-shadow: var(--header-box-shadow);
          }
          .nav-header {
            display: flex;
            align-items: center;
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
          .modelBackground {
            background: rgba(0, 0, 0, 0.5);
            position: absolute;
            height: 100%;
            overflow: none;
            z-index: 199;
            width: 100%;
          }
          .hideOnDesktop {
            display: block;
          }
          nav.hideOnMobile {
            display: none;
          }
          md-list {
            --md-list-container-color: transparent;
            display: block;
            margin-inline: 12px;
            min-width: unset;
            --md-sys-typescale-body-large-size: inherit;
            --md-sys-color-on-surface: var(--primary-text-color);
          }
          md-list-item {
            font-size: 16px;
            --md-list-item-label-text-font: inherit;
          }
          md-list a md-list-item::part(focus-ring) {
            --md-focus-ring-shape: 28px;
          }
          md-list a md-list-item {
            margin-block: 12px;
            display: block;
            --md-focus-ring-shape: 28px;
            border-radius: 28px;
          }
          md-list md-item [slot='headline'] {
            /* shadow root slot has overflow:hidden, it's cutting some text off */
            padding-block: 2px;
          }
          md-list md-item:first-of-type {
            padding-block: 0;
          }
          md-list md-item {
            font-size: 24px;
            padding-block-end: 0;
          }
          md-list md-item + a md-list-item {
            margin-block-start: 0;
          }
          .itemAction:link,
          .itemAction:visited {
            text-decoration: none;
          }
          @media (forced-colors: active) {
            md-list a md-list-item[selected] {
              border: 4px double CanvasText;
            }
            md-list a md-list-item {
              border-radius: 28px;
              border: 1px solid CanvasText;
            }
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
          ${this.computeLinks(
            this.userLinks,
            this.adminLinks,
            this.topMenus
          ).map(linkGroup => this.renderLinkGroup(linkGroup))}
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
          <!--
          Always render the fallback feedback button, but hide it with CSS if feedbackURL is empty.
          We do this instead of using Lit's conditional rendering (e.g., ?hidden or if) inside
          <gr-endpoint-decorator>, because the plugin system may replace or remove this content
          outside of Lit's control. If Lit tries to update a node that was removed by the plugin
          system, it will throw an error. By always rendering the node and only hiding it, we
          avoid this issue and ensure plugin compatibility.
          -->
          <gr-endpoint-decorator class="feedbackButton" name="header-feedback">
            <md-icon-button
              touch-target="none"
              href=${this.feedbackURL}
              title="File a bug"
              aria-label="File a bug"
              target="_blank"
              ?hidden=${!this.feedbackURL}
              @click=${(e: Event) => {
                const path = e.composedPath();
                const iconButton = path.find(
                  (el): el is HTMLElement =>
                    el instanceof HTMLElement &&
                    el.tagName.toLowerCase() === 'md-icon-button'
                );
                const anchor = iconButton?.shadowRoot?.querySelector('a');
                anchor?.setAttribute('rel', 'noopener noreferrer');
              }}
            >
              <md-icon filled>bug_report</md-icon>
            </md-icon-button>
          </gr-endpoint-decorator>
        </div>
        ${this.renderAccount()}
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
        target: '_blank',
      });
    }

    const linkGroups = this.computeLinks(
      this.userLinks,
      this.adminLinks,
      this.topMenus
    );

    return html`
      <nav class="hideOnDesktop">
        <div class="nav-header">
          <md-icon-button
            touch-target="none"
            aria-label="${!this.hamburgerClose ? 'Open' : 'Close'} hamburger"
            @click=${() => {
              this.handleSidebar();
            }}
          >
            <md-icon filled
              >${!this.hamburgerClose ? 'menu' : 'menu_open'}</md-icon
            >
          </md-icon-button>
          <div class="mobileTitleWrapper">
            <a
              href=${`//${window.location.host}${getBaseUrl()}/`}
              class="bigTitle"
              @click=${() => {
                if (this.hamburgerClose) {
                  this.handleSidebar();
                }
              }}
            >
              <gr-endpoint-decorator name="header-mobile-title">
                <div class="titleText"></div>
              </gr-endpoint-decorator>
            </a>
          </div>
          <div class="mobileRightItems">
            <md-icon-button
              touch-target="none"
              title="Search"
              aria-label=${this.mobileSearchHidden
                ? 'Show Searchbar'
                : 'Hide Searchbar'}
              @click=${(e: Event) => {
                this.onMobileSearchTap(e);

                if (this.hamburgerClose) {
                  this.handleSidebar();
                }
              }}
            >
              <md-icon filled>search</md-icon>
            </md-icon-button>
            <gr-dropdown
              class="moreMenu"
              link=""
              .items=${moreMenu}
              .horizontalAlign=${'right'}
              .verticalOffset=${40}
              @click=${() => {
                if (this.hamburgerClose) {
                  this.handleSidebar();
                }
              }}
            >
              <span class="linksTitle">
                <md-icon filled>more_horiz</md-icon>
              </span>
            </gr-dropdown>
            ${this.renderAccountDropdown(true)}
          </div>
        </div>
        <div class="nav-sidebar">
          <md-list aria-label="menu links">
            ${linkGroups.map((linkGroup, index) =>
              this.renderLinkGroupMobile(linkGroup, index, linkGroups.length)
            )}
          </md-list>
        </div>
      </nav>
      ${this.hamburgerClose
        ? html`<div
            class="modelBackground"
            @click=${() => this.handleSidebar()}
          ></div>`
        : nothing}
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

  private renderLinkGroupMobile(
    linkGroup: MainHeaderLinkGroup,
    groupsIndex: number,
    totalGroups: number
  ) {
    return html`
      <md-item><div slot="headline">${linkGroup.title}</div></md-item>
      ${linkGroup.links.map(link => this.renderLinkMobile(link))}
      ${groupsIndex < totalGroups - 1
        ? html`<md-divider role="separator" tabindex="-1"></md-divider>`
        : nothing}
    `;
  }

  private renderLinkMobile(link: DropdownLink) {
    return html`
      <a
        class="itemAction"
        href=${ifDefined(this.computeLinkURL(link))}
        ?download=${!!link.download}
        rel=${ifDefined(this.computeLinkRel(link) ?? undefined)}
        target=${ifDefined(link.target ?? undefined)}
        tabindex="-1"
      >
        <md-list-item type="button" @click=${() => this.handleSidebar()}>
          ${link.name}
        </md-list-item></a
      >
    `;
  }

  private renderAccount() {
    return html`
      <div class="accountContainer" id="accountContainer">
        ${this.renderRegister()}
        <gr-endpoint-decorator name="auth-link">
          <a class="loginButton" href=${this.loginUrl}>${this.loginText}</a>
        </gr-endpoint-decorator>
        <md-icon-button
          class="settingsButton"
          touch-target="none"
          href="${getBaseUrl()}/settings/"
          title="Settings"
          aria-label="Settings"
        >
          <md-icon filled>settings</md-icon>
        </md-icon-button>
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
        @click=${() => {
          if (this.hamburgerClose) {
            this.handleSidebar();
          }
        }}
      ></gr-account-dropdown>
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
    const headerLink = {...linkObj};

    // Normalize all GWT style URLs to PolyGerrit style.
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

  /**
   * Build a URL for the given host and path. The base URL will be only added,
   * if it is not already included in the path.
   *
   * TODO: Move to util handler to remove duplication.
   * @return The scheme-relative URL.
   */
  private computeURLHelper(host: string, path: string) {
    const base = path.startsWith(getBaseUrl()) ? '' : getBaseUrl();
    return '//' + host + base + path;
  }

  /**
   * Build a scheme-relative URL for the current host. Will include the base
   * URL if one is present. Note: the URL will be scheme-relative but absolute
   * with regard to the host.
   *
   * TODO: Move to util handler to remove duplication.
   * @param path The path for the URL.
   * @return The scheme-relative URL.
   */
  private computeRelativeURL(path: string) {
    const host = window.location.host;
    return this.computeURLHelper(host, path);
  }

  /**
   * Compute the URL for a link object.
   *
   * Private but used in tests.
   *
   * TODO: Move to util handler to remove duplication.
   */
  private computeLinkURL(link: DropdownLink) {
    if (typeof link.url === 'undefined') {
      return undefined;
    }
    if (link.target || !link.url.startsWith('/')) {
      return link.url;
    }
    return this.computeRelativeURL(link.url);
  }

  /**
   * Compute the value for the rel attribute of an anchor for the given link
   * object. If the link has a target value, then the rel must be "noopener"
   * for security reasons.
   * Private but used in tests.
   *
   * TODO: Move to util handler to remove duplication.
   */
  private computeLinkRel(link: DropdownLink) {
    // Note: noopener takes precedence over external.
    if (link.target) {
      return REL_NOOPENER;
    }
    if (link.external) {
      return REL_EXTERNAL;
    }
    return null;
  }

  private handleSidebar() {
    this.navSidebar?.classList.toggle('visible');
    if (!this.modelBackground) {
      if (document.getElementsByTagName('html')) {
        document.getElementsByTagName('html')[0].style.overflow = 'hidden';
      }
    } else {
      if (document.getElementsByTagName('html')) {
        document.getElementsByTagName('html')[0].style.overflow = '';
      }
    }
    this.hamburgerClose = !this.hamburgerClose;
  }
}
