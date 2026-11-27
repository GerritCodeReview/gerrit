/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-page-nav/gr-page-nav';
import '@material/web/menu/menu';
import '@material/web/menu/menu-item';
import '@material/web/tabs/primary-tab';
import {MdTabs} from '@material/web/tabs/tabs';
import '../gr-admin-group-list/gr-admin-group-list';
import '../gr-group/gr-group';
import '../gr-group-audit-log/gr-group-audit-log';
import '../gr-group-members/gr-group-members';
import '../gr-plugin-list/gr-plugin-list';
import '../gr-repo/gr-repo';
import '../gr-repo-access/gr-repo-access';
import '../gr-repo-commands/gr-repo-commands';
import '../gr-repo-dashboards/gr-repo-dashboards';
import '../gr-repo-detail-list/gr-repo-detail-list';
import '../gr-repo-submit-requirements/gr-repo-submit-requirements';
import '../gr-repo-labels/gr-repo-labels';
import '../gr-repo-list/gr-repo-list';
import '../gr-server-info/gr-server-info';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {GroupId, GroupName, RepoName} from '../../../types/common';
import {GroupNameChangedDetail} from '../gr-group/gr-group';
import {getAppContext} from '../../../services/app-context';
import {
  GerritView,
  routerModelToken,
} from '../../../services/router/router-model';
import {materialStyles} from '../../../styles/gr-material-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  css,
  html,
  LitElement,
  nothing,
  PropertyValues,
  TemplateResult,
} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {ValueChangedEvent} from '../../../types/events';
import {
  AdminChildView,
  AdminNavLinksOption,
  adminViewModelToken,
  AdminViewState,
  getAdminLinks,
  NavLink,
  SubsectionInterface,
} from '../../../models/views/admin';
import {
  GroupDetailView,
  groupViewModelToken,
  GroupViewState,
} from '../../../models/views/group';
import {
  RepoDetailView,
  repoViewModelToken,
  RepoViewState,
} from '../../../models/views/repo';
import {createSearchUrl} from '../../../models/views/search';
import {computeMainCodeBrowserWeblink} from '../../../utils/weblink-util';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

export interface AdminSubsectionLink {
  text: string;
  value: string;
  view: GerritView;
  url?: string;
  detailType?: GroupDetailView | RepoDetailView;
  parent?: GroupId | RepoName;
}

@customElement('gr-admin-view')
export class GrAdminView extends LitElement {
  private static adminLayoutStyles?: CSSStyleSheet;

  @state() view?: GerritView;

  @state() adminViewState?: AdminViewState;

  @state() groupViewState?: GroupViewState;

  @state() repoViewState?: RepoViewState;

  @state() canDeleteCapability = false;

  @state() private breadcrumbParentName?: string;

  @state() private adminMenuOpen = false;

  @state() private canScrollLeft = false;

  @state() private canScrollRight = false;

  @state() private repoBrowseUrl?: string;

  // private but used in test
  @state() repoName?: RepoName;

  // private but used in test
  @state() groupId?: GroupId;

  // private but used in test
  @state() groupIsInternal?: boolean;

  // private but used in test
  @state() groupName?: GroupName;

  // private but used in test
  @state() subsectionLinks?: AdminSubsectionLink[];

  // private but used in test
  @state() filteredLinks?: NavLink[];

  private reloading = false;

  private tabViewport?: HTMLElement;

  private tabStrip?: HTMLElement;

  private tabViewportWidth = 0;

  private readonly tabResizeObserver = new ResizeObserver(() => {
    const width = this.tabViewport?.clientWidth ?? 0;

    if (width && !this.tabViewportWidth) {
      void this.scrollActiveTabIntoView();
    }

    this.tabViewportWidth = width;
    this.updateTabScrollState();
  });

  private readonly restApiService = getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getAdminViewModel = resolve(this, adminViewModelToken);

  private readonly getGroupViewModel = resolve(this, groupViewModelToken);

  private readonly getRepoViewModel = resolve(this, repoViewModelToken);

  private readonly getRouterModel = resolve(this, routerModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();

    this.addEventListener('reload', () => window.location.reload());

    subscribe(
      this,
      () => this.getAdminViewModel().state$,
      state => {
        this.adminViewState = state;
        if (this.needsReload()) this.reload();
      }
    );

    subscribe(
      this,
      () => this.getGroupViewModel().state$,
      state => {
        this.groupViewState = state;
        if (this.needsReload()) this.reload();
      }
    );

    subscribe(
      this,
      () => this.getRepoViewModel().state$,
      state => {
        this.repoViewState = state;
        if (this.needsReload()) this.reload();
      }
    );

    subscribe(
      this,
      () => this.getRouterModel().routerView$,
      view => {
        this.view = view;
        if (this.needsReload()) this.reload();
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.reload();
    this.requestUpdate();
  }

  override disconnectedCallback() {
    this.tabResizeObserver.disconnect();
    this.tabViewport = undefined;
    this.tabStrip = undefined;
    this.tabViewportWidth = 0;

    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      menuPageStyles,
      pageNavStyles,
      css`
        /* Theme */

        :host {
          --admin-sidebar-width: 248px;
          --admin-nav-accent: var(--link-color);
          --admin-header-surface: var(--background-color-primary);

          --admin-content-surface: color-mix(
            in srgb,
            var(--admin-header-surface) 97%,
            var(--primary-text-color)
          );

          --admin-nav-selected: color-mix(
            in srgb,
            var(--admin-header-surface) 83%,
            var(--admin-nav-accent)
          );

          --admin-nav-divider: color-mix(
            in srgb,
            var(--border-color) 85%,
            var(--admin-header-surface)
          );

          background-color: var(--admin-content-surface);
        }

        /* Breadcrumb */

        .mainHeader {
          align-items: center;
          box-sizing: border-box;
          display: flex;
          min-width: 0;
        }

        .breadcrumbSeparator {
          color: var(--deemphasized-text-color);
          flex-shrink: 0;
        }

        .breadcrumbName {
          color: var(--primary-text-color);
          font-weight: var(--font-weight-normal, 400);
          min-width: 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        /* Administration menu */

        .adminMenuWrapper {
          display: inline-flex;
          flex: 0 0 auto;
          position: relative;
        }

        .adminMenuTrigger {
          --button-background-color: transparent;
          --gr-button-text-color: var(--admin-nav-accent);
        }

        .adminMenuTrigger[aria-expanded='true'] {
          --button-background-color: var(--admin-nav-selected);
        }

        .adminMenuTrigger span {
          font-weight: var(--font-weight-medium);
          white-space: nowrap;
        }

        .adminMenuTrigger gr-icon {
          --gr-icon-size: 16px;

          color: inherit;
          margin-inline-start: var(--spacing-xs);
        }

        .adminMenu {
          --md-menu-container-color: var(--admin-header-surface);
          --md-menu-container-shape: 10px;
          --md-menu-item-label-text-color: var(--primary-text-color);
          --md-menu-item-selected-container-color: var(--admin-nav-selected);
          --md-menu-item-selected-label-text-color: var(--admin-nav-accent);
          --md-menu-item-one-line-container-height: 40px;
          --md-menu-item-leading-space: 12px;
          --md-menu-item-trailing-space: 12px;

          min-width: 180px;
        }

        .adminMenu md-menu-item {
          border-radius: 6px;
          margin-inline: var(--spacing-xs);
          overflow: hidden;
        }

        .adminMenu md-menu-item[selected] {
          --md-menu-item-label-text-weight: var(--font-weight-medium);
        }

        .adminMenu .externalLink {
          --gr-icon-size: 16px;

          color: var(--deemphasized-text-color);
        }

        /* Keyboard focus */

        .navStyles a:focus-visible,
        .mobileTabs a:focus-visible,
        gr-button:focus-visible {
          outline: 2px solid var(--admin-nav-accent);
          outline-offset: -2px;
        }

        /* Repository heading */

        .mobileRepoHeading,
        .mobileTabBar,
        .repoDescription {
          display: none;
        }

        /*
         * Keep the repository title and actions on the same flex line.
         * Long repository names wrap within their available space
         * without moving the action buttons.
         */
        .mobileRepoHeading {
          align-items: flex-start;
          box-sizing: border-box;
          flex-wrap: nowrap;
          justify-content: space-between;
          min-width: 0;
        }

        .repoTitle {
          align-items: start;
          display: grid;
          flex: 1 1 0;
          grid-template-columns: auto minmax(0, 1fr);
          min-width: 0;
        }

        /*
         * Align the folder icon with the first line of the repository
         * name, rather than the centre of a multi-line heading.
         */
        .repoTitle > gr-icon {
          align-self: start;
          color: var(--primary-text-color);
          grid-column: 1;
          grid-row: 1;
          margin-block-start: 4px;
        }

        .repoTitle h1,
        .repoDescription {
          grid-column: 2;
          margin: 0;
          min-width: 0;
          overflow-wrap: anywhere;
        }

        .repoTitle h1 {
          color: var(--primary-text-color);
          font-weight: var(--font-weight-bold, 700);
          grid-row: 1;
        }

        .repoDescription {
          color: var(--deemphasized-text-color);
          font-size: 14px;
          font-weight: var(--font-weight-normal, 400);
          grid-row: 2;
          line-height: 20px;
        }

        /* Repository actions */

        .repoActions {
          align-items: center;
          display: flex;
          flex: 0 0 auto;
        }

        .repoAction {
          --button-background-color: var(--admin-header-surface);
          --gr-button-text-color: var(--admin-nav-accent);

          border: 1px solid var(--border-color);
          border-radius: 6px;
          box-sizing: border-box;
          font-weight: var(--font-weight-medium);
          line-height: 20px;
          white-space: nowrap;
        }

        .repoAction[primary] {
          border-color: var(--primary-button-background-color);
        }

        .repoAction gr-icon {
          --gr-icon-size: 16px;

          color: inherit;
          flex-shrink: 0;
        }

        .browseLabel {
          margin-inline-end: var(--spacing-s);
        }

        /* External navigation links */

        .navStyles .externalLink,
        .adminTabs .externalLink {
          --gr-icon-size: 14px;

          color: inherit;
          flex-shrink: 0;
        }

        /* Main content */

        .main.detailMain {
          box-sizing: border-box;
          min-width: 0;
        }

        gr-repo {
          display: block;
          min-width: 0;
        }

        /* Desktop */

        @media screen and (width > 53em) {
          :host {
            --admin-repo-max-width: 1120px;
            --admin-desktop-inset: 24px;

            --admin-content-offset: max(
              var(--admin-desktop-inset),
              calc(
                (
                    100% - var(--admin-sidebar-width) -
                      var(--admin-repo-max-width)
                  ) / 2
              )
            );

            --admin-nav-background: color-mix(
              in srgb,
              var(--admin-header-surface) 95%,
              var(--primary-text-color)
            );

            --admin-nav-selected: color-mix(
              in srgb,
              var(--admin-header-surface) 81%,
              var(--admin-nav-accent)
            );

            --admin-nav-hover: color-mix(
              in srgb,
              var(--admin-nav-background) 94%,
              var(--primary-text-color)
            );

            --admin-nav-divider: color-mix(
              in srgb,
              var(--border-color) 94%,
              var(--primary-text-color)
            );

            --admin-sidebar-divider: color-mix(
              in srgb,
              var(--admin-nav-background) 60%,
              var(--border-color)
            );

            --admin-command-surface: color-mix(
              in srgb,
              var(--admin-header-surface) 96%,
              var(--primary-text-color)
            );

            box-sizing: border-box;
            display: flow-root;
            flex: 1 1 auto;
            min-width: 0;
            padding-block-end: var(--spacing-xxl);
          }

          /*
           * Administration-only layout overrides.
           * getAdminLayoutStyles() extracts these rules for the
           * application and sidebar shadow roots.
           */
          :host(gr-app-element) main:has(> gr-admin-view) {
            display: flex;
            flex-direction: column;
            padding-bottom: 0;
          }

          :host(gr-page-nav.navStyles) nav {
            background-color: var(--admin-nav-background);
            border: none;
            border-inline-end: 1px solid var(--admin-sidebar-divider);
            box-sizing: border-box;
            width: var(--admin-sidebar-width);
          }

          :host(gr-page-nav.navStyles) nav.pinned {
            height: calc(100vh - var(--main-header-height));
            overflow-y: auto;
          }

          /* Sidebar */

          gr-page-nav.navStyles {
            --table-header-background-color: var(--admin-nav-background);

            background-color: var(--admin-nav-background);
            border-inline-end: none;
          }

          .navStyles ul {
            list-style: none;
            padding: var(--spacing-l) var(--spacing-m);
          }

          /*
           * Preserve specificity when overriding pageNavStyles.
           */
          .navStyles li,
          .navStyles li.sectionTitle,
          .navStyles li.sectionTitle:not(:first-child),
          .navStyles li.subsectionTitle,
          .navStyles li.subsectionItem,
          .navStyles li.selected {
            background-color: transparent;
            border: none;
            border-radius: 8px;
            box-sizing: border-box;
            margin: 2px 0;
            padding: 0;
            white-space: normal;
          }

          .navStyles li a,
          .navStyles li a.title,
          .navStyles li.subsectionTitle > span {
            align-items: center;
            background-color: transparent;
            border-radius: 8px;
            box-sizing: border-box;
            color: var(--primary-text-color);
            display: flex;
            gap: var(--spacing-s);
            line-height: 20px;
            margin: 0;
            min-height: 36px;
            overflow: hidden;
            overflow-wrap: anywhere;
            padding: 8px 16px;
            text-decoration: none;
            text-overflow: ellipsis;
            white-space: normal;
            width: 100%;
          }

          /* Main navigation destinations */

          .navStyles li.sectionTitle > a {
            font-weight: var(--font-weight-medium, 500);
            min-height: 44px;
            padding-inline: 16px 12px;
          }

          .navLabel {
            flex: 1 1 auto;
            min-width: 0;
          }

          /* Repository and group names */

          .navStyles li.subsectionTitle {
            margin-inline-start: 8px;
          }

          .navStyles li.subsectionTitle a,
          .navStyles li.subsectionTitle > span {
            font-weight: var(--font-weight-medium, 500);
            padding-inline-start: 24px;
          }

          .navStyles li.subsectionTitle > span {
            font-size: 14px;
          }

          /* Nested navigation destinations */

          .navStyles li.subsectionItem {
            margin-inline-start: 24px;
          }

          .navStyles li.subsectionItem > a {
            min-height: 38px;
            padding-inline: 20px 12px;
          }

          /* Navigation interaction states */

          .navStyles li a:hover {
            background-color: var(--admin-nav-hover);
            color: var(--primary-text-color);
            text-decoration: none;
          }

          .navStyles li.selected > a {
            background-color: var(--admin-nav-selected);
            color: var(--admin-nav-accent);
            font-weight: var(--font-weight-medium);
            position: relative;
          }

          .navStyles li.selected > a::before {
            background-color: var(--admin-nav-accent);
            border-radius: 4px 0 0 4px;
            content: '';
            inset-block: 0;
            inset-inline-start: 0;
            position: absolute;
            width: 3px;
          }

          .navStyles li.selected > a:hover {
            background-color: color-mix(
              in srgb,
              var(--admin-nav-selected) 94%,
              var(--admin-nav-accent)
            );
          }

          /* Sidebar dividers and external links */

          .navStyles li.sectionDivider {
            border-top: 1px solid var(--admin-sidebar-divider);
            border-radius: 0;
            margin: 12px var(--spacing-s);
          }

          .navStyles li.sectionDivider:last-child {
            display: none;
          }

          .navStyles .externalLink {
            margin-inline-start: var(--spacing-xs);
            vertical-align: middle;
          }

          .navStyles li.sectionTitle .externalLink {
            margin-inline-start: auto;
          }

          /* Main content */

          .mainHeader {
            display: none;
          }

          .repoGeneralHeading,
          .main.detailMain.breadcrumbs {
            margin-inline-start: calc(
              var(--admin-sidebar-width) + var(--admin-content-offset)
            );
            margin-inline-end: var(--admin-content-offset);
            max-width: var(--admin-repo-max-width);
          }

          .main.detailMain.breadcrumbs {
            background-color: transparent;
            margin-block: 0;
            padding: 0;
            width: auto;
          }

          .main.detailMain.breadcrumbs:not(:has(> gr-repo)) {
            padding-block-start: var(--admin-desktop-inset);
          }

          .main.table {
            margin-left: 0;
            margin-inline-start: var(--admin-sidebar-width);
          }

          gr-repo {
            --view-background-color: var(--admin-header-surface);
            --shell-command-background-color: var(--admin-command-surface);
          }

          /* Repository heading and actions */

          .repoGeneralHeading {
            display: flex;
            gap: var(--spacing-l);
            padding: 28px 0 24px;
          }

          .repoTitle,
          .repoActions {
            column-gap: var(--spacing-m);
          }

          .repoTitle {
            row-gap: 4px;
          }

          .repoTitle > gr-icon {
            --gr-icon-size: 26px;
          }

          .repoTitle h1 {
            font-size: 26px;
            line-height: 34px;
          }

          .repoDescription {
            display: block;
          }

          .repoAction {
            --gr-button-padding: 7px 15px;

            font-size: 14px;
          }
        }

        /* Mobile */

        @media only screen and (max-width: 53em) {
          gr-page-nav.navStyles {
            display: none;
          }

          /* Breadcrumb */

          .mainHeader {
            background-color: var(--admin-header-surface);
            gap: 6px;
            margin: 0;
            min-height: 32px;
            padding: 8px 16px 0;
            width: 100%;
          }

          .breadcrumbName,
          .breadcrumbSeparator,
          .adminMenuTrigger {
            font-size: 12px;
          }

          .breadcrumbName,
          .adminMenuTrigger {
            line-height: 20px;
          }

          .adminMenuTrigger {
            --gr-button-padding: 4px 0;
          }

          /* Repository heading and actions */

          .mobileRepoHeading {
            background-color: var(--admin-header-surface);
            display: flex;
            gap: 12px;
            padding: 8px 16px 16px;
            width: 100%;
          }

          .repoTitle {
            column-gap: 10px;
          }

          .repoTitle > gr-icon {
            --gr-icon-size: 22px;

            margin-block-start: 5px;
          }

          .repoTitle h1 {
            font-size: 24px;
            line-height: 32px;
          }

          .repoActions {
            gap: 8px;
          }

          .repoAction {
            --gr-button-padding: 7px 11px;

            font-size: 12px;
          }

          /* Main content */

          .main.detailMain,
          .main.table {
            background-color: var(--admin-content-surface);
          }

          .main.detailMain.breadcrumbs {
            margin: 0;
            max-width: none;
            padding: 12px;
          }

          gr-repo {
            --view-background-color: var(--admin-content-surface);
            --shell-command-background-color: var(--admin-content-surface);
          }

          /* Scrollable tab bar */

          .mobileTabBar {
            align-items: stretch;
            background-color: var(--admin-header-surface);
            border-top: 1px solid var(--admin-nav-divider);
            border-bottom: 1px solid var(--border-color);
            box-shadow: 0 2px 4px
              color-mix(in srgb, var(--primary-text-color) 8%, transparent);
            box-sizing: border-box;
            display: grid;
            grid-template-columns: 24px minmax(0, 1fr) 24px;
            min-width: 0;
            position: relative;
            width: 100%;
          }

          .mobileTabBar:dir(rtl) {
            direction: ltr;
          }

          .mobileTabBar:dir(rtl) .tabViewport {
            direction: rtl;
          }

          .tabViewport {
            min-width: 0;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scrollbar-width: none;
          }

          .tabViewport::-webkit-scrollbar {
            display: none;
          }

          .tabScrollButton {
            --button-background-color: transparent;
            --gr-button-text-color: var(--admin-nav-accent);
            --gr-button-padding: 12px 2px;

            align-items: center;
            display: flex;
            justify-content: center;
            line-height: 20px;
            min-width: 0;
          }

          .tabScrollButton gr-icon {
            --gr-icon-size: 20px;

            color: inherit;
          }

          .mobileTabs {
            box-sizing: border-box;
            max-width: none;
            min-width: max-content;
            width: max-content;
          }

          /* Material Web tabs */

          md-tabs.mobileTabs {
            --md-primary-tab-container-color: transparent;
            --md-primary-tab-container-height: 44px;
            --md-primary-tab-container-shape: 0;

            --md-primary-tab-label-text-font: var(--font-family);
            --md-primary-tab-label-text-size: 13px;
            --md-primary-tab-label-text-line-height: 20px;
            --md-primary-tab-label-text-weight: var(--font-weight-medium);

            --md-primary-tab-label-text-color: var(--primary-text-color);
            --md-primary-tab-hover-label-text-color: var(--primary-text-color);
            --md-primary-tab-focus-label-text-color: var(--primary-text-color);
            --md-primary-tab-pressed-label-text-color: var(
              --primary-text-color
            );

            --md-primary-tab-active-label-text-color: var(--admin-nav-accent);
            --md-primary-tab-active-hover-label-text-color: var(
              --admin-nav-accent
            );
            --md-primary-tab-active-focus-label-text-color: var(
              --admin-nav-accent
            );
            --md-primary-tab-active-pressed-label-text-color: var(
              --admin-nav-accent
            );

            --md-primary-tab-active-indicator-color: var(--admin-nav-accent);
            --md-primary-tab-active-indicator-height: 3px;
            --md-primary-tab-active-indicator-shape: 3px 3px 0 0;

            --md-divider-color: transparent;

            display: flex;
            overflow: visible;
          }

          md-tabs.mobileTabs md-primary-tab {
            box-sizing: border-box;
            flex: 0 0 auto;
            margin: 0;
            min-width: max-content;
            white-space: nowrap;
            width: max-content;
          }

          .tabLabel {
            display: inline-block;
            flex-shrink: 0;
            padding-inline: 12px;
            white-space: nowrap;
          }

          /* Administration links */

          .adminTabs {
            align-items: stretch;
            display: flex;
          }

          .adminTabs a {
            align-items: center;
            box-sizing: border-box;
            color: var(--primary-text-color);
            display: flex;
            flex: 0 0 auto;
            font-size: 13px;
            font-weight: var(--font-weight-medium);
            gap: 6px;
            justify-content: center;
            min-height: 44px;
            padding: 0 12px;
            position: relative;
            text-decoration: none;
            white-space: nowrap;
          }

          .adminTabs a:hover {
            background-color: var(--hover-background-color);
          }

          .adminTabs a.active {
            color: var(--admin-nav-accent);
          }

          .adminTabs a.active::after {
            background-color: var(--admin-nav-accent);
            border-radius: 3px 3px 0 0;
            bottom: 0;
            content: '';
            height: 3px;
            inset-inline: 12px;
            position: absolute;
          }
        }

        /* Narrow mobile screens */

        @media only screen and (max-width: 30em) {
          .browseLabel {
            display: none;
          }

          .repoAction {
            --gr-button-padding: 7px 9px;
          }
        }
      `,
    ];
  }

  /**
   * Extract the administration-specific cross-component rules from
   * the main stylesheet, preserving their media query.
   *
   * Cache the result so extraction only occurs once.
   */
  private static getAdminLayoutStyles() {
    if (this.adminLayoutStyles) {
      return this.adminLayoutStyles;
    }

    const style = this.elementStyles[this.elementStyles.length - 1];
    const source = style instanceof CSSStyleSheet ? style : style?.styleSheet;

    if (!source) return undefined;

    const rules = Array.from(source.cssRules).flatMap(rule => {
      if (!(rule instanceof CSSMediaRule)) return [];

      const layoutRules = Array.from(rule.cssRules).filter(
        rule =>
          rule instanceof CSSStyleRule &&
          (rule.selectorText.startsWith(':host(gr-app-element)') ||
            rule.selectorText.startsWith(':host(gr-page-nav.navStyles)'))
      );

      if (!layoutRules.length) return [];

      return [
        `@media ${rule.conditionText} {
          ${layoutRules.map(rule => rule.cssText).join('\n')}
        }`,
      ];
    });

    if (!rules.length) return undefined;

    const stylesheet = new CSSStyleSheet();
    stylesheet.replaceSync(rules.join('\n'));

    this.adminLayoutStyles = stylesheet;
    return stylesheet;
  }

  override render() {
    if (!this.isAdminView()) return nothing;

    return html`
      ${this.renderSidebar()} ${this.renderSubsectionLinks()}
      ${this.renderMobileRepoHeading()} ${this.renderMobileTabs()}
      ${this.renderRepoList()} ${this.renderGroupList()}
      ${this.renderPluginList()} ${this.renderRepoMain()} ${this.renderGroup()}
      ${this.renderGroupMembers()} ${this.renderGroupAuditLog()}
      ${this.renderRepoDetailList()} ${this.renderRepoCommands()}
      ${this.renderRepoAccess()} ${this.renderRepoDashboards()}
      ${this.renderRepoSubmitRequirements()} ${this.renderRepoLabels()}
      ${this.renderServerInfo()}
    `;
  }

  /* Desktop navigation */

  private renderSidebar() {
    return html`
      <gr-page-nav class="navStyles">
        <ul class="sectionContent">
          ${this.filteredLinks?.map(item => this.renderAdminNav(item))}
        </ul>
      </gr-page-nav>
    `;
  }

  private renderAdminNav(item: NavLink) {
    const url = this.computeLinkURL(item);
    const selected =
      this.view === GerritView.ADMIN && !!this.computeSelectedClass(item.view);

    return html`
      <li class="sectionTitle ${selected ? 'selected' : ''}">
        <a
          class="title"
          href=${url}
          target=${ifDefined(item.target ?? undefined)}
          rel="noopener"
          aria-current=${ifDefined(selected ? 'page' : undefined)}
        >
          <span class="navLabel">${item.name}</span>
          ${this.renderExternalLinkIcon(url, item.target ?? undefined)}
        </a>
      </li>

      ${item.children?.map(child => this.renderAdminNavChild(child))}
      ${this.renderAdminNavSubsection(item)}
    `;
  }

  private renderAdminNavChild(child: SubsectionInterface) {
    const url = this.computeLinkURL(child);

    return html`
      <li class=${this.computeSelectedClass(child.view)}>
        <a href=${url} rel="noopener">
          ${child.name} ${this.renderExternalLinkIcon(url)}
        </a>
      </li>
    `;
  }

  private renderAdminNavSubsection(item: NavLink) {
    const subsection = item.subsection;
    if (!subsection) return nothing;

    const childSelected = subsection.children?.some(child =>
      this.computeSelectedClass(child.view, child.detailType)
    );

    const selected =
      !!subsection.url &&
      !childSelected &&
      !!this.computeSelectedClass(subsection.view, subsection.detailType);

    return html`
      <li class="subsectionTitle ${selected ? 'selected' : ''}">
        ${this.renderAdminNavSubsectionUrl(subsection, selected)}
      </li>

      ${subsection.children?.map(child =>
        this.renderAdminNavSubsectionChild(child)
      )}

      <li class="sectionDivider" role="presentation"></li>
    `;
  }

  private renderAdminNavSubsectionUrl(
    subsection: SubsectionInterface,
    selected: boolean
  ) {
    if (!subsection.url) {
      return html`<span>${subsection.name}</span>`;
    }

    return html`
      <a
        class="title"
        href=${this.computeLinkURL(subsection)}
        rel="noopener"
        aria-current=${ifDefined(selected ? 'page' : undefined)}
      >
        ${subsection.name}
      </a>
    `;
  }

  private renderAdminNavSubsectionChild(child: SubsectionInterface) {
    const url = this.computeLinkURL(child);
    const selected = !!this.computeSelectedClass(child.view, child.detailType);

    return html`
      <li class="subsectionItem ${selected ? 'selected' : ''}">
        <a href=${url} aria-current=${ifDefined(selected ? 'page' : undefined)}>
          ${child.name} ${this.renderExternalLinkIcon(url)}
        </a>
      </li>
    `;
  }

  /* Mobile breadcrumb */

  private renderSubsectionLinks() {
    if (!this.subsectionLinks?.length) return nothing;

    const parent = this.filteredLinks?.find(item => item.subsection);
    if (!parent) return nothing;

    return html`
      <nav class="mainHeader" aria-label="Breadcrumb">
        ${this.renderAdminMenu(parent)}

        <span class="breadcrumbSeparator" aria-hidden="true">/</span>

        <span class="breadcrumbName" title=${this.breadcrumbParentName ?? ''}>
          ${this.breadcrumbParentName}
        </span>
      </nav>
    `;
  }

  private renderAdminMenu(parent: NavLink) {
    return html`
      <div class="adminMenuWrapper">
        <gr-button
          id="adminMenuTrigger"
          class="adminMenuTrigger"
          flatten
          touch-target="none"
          aria-haspopup="menu"
          aria-controls="adminMenu"
          aria-expanded=${this.adminMenuOpen ? 'true' : 'false'}
          @click=${() => {
            this.adminMenuOpen = !this.adminMenuOpen;
          }}
        >
          <span>${parent.name}</span>
          <gr-icon icon="expand_more" aria-hidden="true"></gr-icon>
        </gr-button>

        <md-menu
          id="adminMenu"
          class="adminMenu"
          anchor="adminMenuTrigger"
          positioning="popover"
          aria-label="Administration"
          ?open=${this.adminMenuOpen}
          @closing=${() => {
            this.adminMenuOpen = false;
          }}
        >
          ${this.filteredLinks?.map(item => {
            const url = this.computeLinkURL(item);
            if (!url) return nothing;

            return html`
              <md-menu-item
                type="link"
                href=${url}
                target=${ifDefined(item.target ?? undefined)}
                ?selected=${item === parent}
              >
                <div slot="headline">${item.name}</div>

                ${this.renderExternalLinkIcon(
                  url,
                  item.target ?? undefined,
                  'end'
                )}
              </md-menu-item>
            `;
          })}
        </md-menu>
      </div>
    `;
  }

  /* Repository heading */

  private renderMobileRepoHeading() {
    if (this.view !== GerritView.REPO) return nothing;

    const repo = this.repoViewState?.repo;
    if (!repo) return nothing;

    const general = this.isRepoGeneralView();

    return html`
      <div class="mobileRepoHeading ${general ? 'repoGeneralHeading' : ''}">
        <div class="repoTitle">
          <gr-icon icon="folder" aria-hidden="true"></gr-icon>

          <h1>${repo}</h1>

          ${general
            ? html`
                <p class="repoDescription">
                  Basic repository settings, description and general options.
                </p>
              `
            : nothing}
        </div>

        <div class="repoActions">
          <gr-button
            class="repoAction"
            flatten
            touch-target="none"
            ?disabled=${!this.repoBrowseUrl}
            aria-label="Browse repository (opens in a new tab)"
            title="Browse repository"
            @click=${this.handleBrowseRepo}
          >
            <span class="browseLabel">Browse</span>
            <gr-icon icon="open_in_new" aria-hidden="true"></gr-icon>
          </gr-button>

          <gr-button
            class="repoAction"
            primary
            flatten
            touch-target="none"
            @click=${() => this.getNavigation().setUrl(createSearchUrl({repo}))}
          >
            View Changes
          </gr-button>
        </div>
      </div>
    `;
  }

  private handleBrowseRepo() {
    if (!this.repoBrowseUrl) return;

    window.open(this.repoBrowseUrl, '_blank', 'noopener,noreferrer');
  }

  private async loadRepoBrowseUrl(repo?: RepoName) {
    this.repoBrowseUrl = undefined;
    if (!repo) return;

    try {
      const loggedIn = await this.restApiService.getLoggedIn();
      if (!loggedIn || this.repoName !== repo) return;

      const [repoInfo, config] = await Promise.all([
        this.restApiService.getRepo(repo),
        this.restApiService.getConfig(),
      ]);

      if (this.repoName !== repo) return;

      this.repoBrowseUrl = computeMainCodeBrowserWeblink(
        repoInfo?.web_links ?? [],
        config
      )?.url;
    } catch {
      if (this.repoName === repo) {
        this.repoBrowseUrl = undefined;
      }
    }
  }

  /* Mobile navigation */

  private renderMobileTabs() {
    if (this.view === GerritView.ADMIN) {
      if (!this.filteredLinks?.length) return nothing;

      return this.renderTabBar(html`
        <nav class="mobileTabs adminTabs" aria-label="Administration">
          ${this.filteredLinks.map(item =>
            this.renderMobileTab(
              item.name,
              this.computeLinkURL(item),
              !!this.computeSelectedClass(item.view),
              item.target ?? undefined
            )
          )}
        </nav>
      `);
    }

    const links = this.subsectionLinks?.filter(link => link.url);
    if (!links?.length) return nothing;

    const activeIndex = links.findIndex(link =>
      this.selectedIsCurrentPage(link)
    );

    return this.renderTabBar(html`
      <md-tabs
        class="mobileTabs"
        aria-label="${this.breadcrumbParentName} sections"
        .activeTabIndex=${activeIndex}
        @change=${(e: Event) => this.handleMobileTabChange(e, links)}
      >
        ${links.map(
          (link, index) => html`
            <md-primary-tab
              class=${index === activeIndex ? 'currentTab' : ''}
              aria-controls="adminDetailPanel"
            >
              <span class="tabLabel">${link.text}</span>
            </md-primary-tab>
          `
        )}
      </md-tabs>
    `);
  }

  private renderTabBar(tabs: TemplateResult) {
    return html`
      <div class="mobileTabBar">
        <gr-button
          class="tabScrollButton"
          flatten
          touch-target="none"
          aria-label="Scroll tabs left"
          aria-controls="adminTabViewport"
          ?disabled=${!this.canScrollLeft}
          @click=${() => this.scrollTabs(-1)}
        >
          <gr-icon icon="chevron_left" aria-hidden="true"></gr-icon>
        </gr-button>

        <div
          id="adminTabViewport"
          class="tabViewport"
          @scroll=${this.updateTabScrollState}
          @focusin=${this.handleTabFocus}
        >
          ${tabs}
        </div>

        <gr-button
          class="tabScrollButton"
          flatten
          touch-target="none"
          aria-label="Scroll tabs right"
          aria-controls="adminTabViewport"
          ?disabled=${!this.canScrollRight}
          @click=${() => this.scrollTabs(1)}
        >
          <gr-icon icon="chevron_right" aria-hidden="true"></gr-icon>
        </gr-button>
      </div>
    `;
  }

  private handleMobileTabChange(e: Event, links: AdminSubsectionLink[]) {
    const tabs = e.currentTarget;
    if (!(tabs instanceof MdTabs)) return;

    const selected = links[tabs.activeTabIndex];

    if (!selected?.url || this.reloading) {
      tabs.activeTabIndex = links.findIndex(link =>
        this.selectedIsCurrentPage(link)
      );
      return;
    }

    if (!this.selectedIsCurrentPage(selected)) {
      this.handleSubsectionChange(selected.value);
    }
  }

  private renderMobileTab(
    name: string,
    url: string,
    selected: boolean,
    target?: string
  ) {
    if (!url) return nothing;

    return html`
      <a
        class=${selected ? 'active' : ''}
        href=${url}
        target=${ifDefined(target)}
        rel="noopener"
        aria-current=${ifDefined(selected ? 'page' : undefined)}
        aria-label=${ifDefined(
          target === '_blank' ? `${name} (opens in a new tab)` : undefined
        )}
      >
        ${name} ${this.renderExternalLinkIcon(url, target)}
      </a>
    `;
  }

  /* External links */

  private renderExternalLinkIcon(url: string, target?: string, slot?: string) {
    if (!this.isExternalLink(url, target)) return nothing;

    return html`
      <gr-icon
        class="externalLink"
        icon="open_in_new"
        slot=${ifDefined(slot)}
        aria-hidden="true"
      ></gr-icon>
    `;
  }

  private isExternalLink(url: string, target?: string) {
    if (!url) return false;
    if (target === '_blank') return true;

    try {
      return (
        new URL(url, window.location.href).origin !== window.location.origin
      );
    } catch {
      return false;
    }
  }

  /* Page selection */

  private isAdminPage(page: AdminChildView) {
    return (
      this.view === GerritView.ADMIN && this.adminViewState?.adminView === page
    );
  }

  private isRepoGeneralView() {
    return (
      this.view === GerritView.REPO &&
      this.getDetailView() === RepoDetailView.GENERAL
    );
  }

  private getDetailPanelLabel() {
    return (
      this.subsectionLinks?.find(link => this.selectedIsCurrentPage(link))
        ?.text ??
      this.breadcrumbParentName ??
      'Details'
    );
  }

  /* Page containers */

  private renderTablePage(content: TemplateResult) {
    return html`<div class="main table">${content}</div>`;
  }

  private renderDetailPanel(content: TemplateResult, table = false) {
    return html`
      <div
        class=${table
          ? 'main table breadcrumbs'
          : 'main breadcrumbs detailMain'}
        id="adminDetailPanel"
        role="tabpanel"
        aria-label=${this.getDetailPanelLabel()}
      >
        ${content}
      </div>
    `;
  }

  /* Administration pages */

  private renderRepoList() {
    if (!this.isAdminPage(AdminChildView.REPOS)) return nothing;

    return this.renderTablePage(html`
      <gr-repo-list class="table" .params=${this.adminViewState}></gr-repo-list>
    `);
  }

  private renderGroupList() {
    if (!this.isAdminPage(AdminChildView.GROUPS)) return nothing;

    return this.renderTablePage(html`
      <gr-admin-group-list
        class="table"
        .params=${this.adminViewState}
      ></gr-admin-group-list>
    `);
  }

  private renderPluginList() {
    if (!this.isAdminPage(AdminChildView.PLUGINS)) return nothing;

    return this.renderTablePage(html`
      <gr-plugin-list
        class="table"
        .params=${this.adminViewState}
      ></gr-plugin-list>
    `);
  }

  private renderServerInfo() {
    if (!this.isAdminPage(AdminChildView.SERVER_INFO)) return nothing;

    return this.renderTablePage(html`
      <gr-server-info class="table"></gr-server-info>
    `);
  }

  /* Repository pages */

  private renderRepoMain() {
    if (!this.isRepoGeneralView()) return nothing;

    return this.renderDetailPanel(html`
      <gr-repo
        .repo=${this.repoViewState?.repo}
        ?admin-heading=${!!this.repoViewState?.repo}
      ></gr-repo>
    `);
  }

  private renderRepoDetailList() {
    if (this.view !== GerritView.REPO) return nothing;

    const detail = this.repoViewState?.detail;

    if (detail !== RepoDetailView.BRANCHES && detail !== RepoDetailView.TAGS) {
      return nothing;
    }

    return this.renderDetailPanel(
      html`
        <gr-repo-detail-list
          class="table"
          .params=${this.repoViewState}
        ></gr-repo-detail-list>
      `,
      true
    );
  }

  private renderRepoCommands() {
    if (
      this.view !== GerritView.REPO ||
      this.repoViewState?.detail !== RepoDetailView.COMMANDS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(html`
      <gr-repo-commands
        .repo=${this.repoViewState.repo}
        .createEdit=${this.repoViewState.createEdit}
      ></gr-repo-commands>
    `);
  }

  private renderRepoAccess() {
    if (
      this.view !== GerritView.REPO ||
      this.repoViewState?.detail !== RepoDetailView.ACCESS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(html`
      <gr-repo-access .repo=${this.repoViewState.repo}></gr-repo-access>
    `);
  }

  private renderRepoDashboards() {
    if (
      this.view !== GerritView.REPO ||
      this.repoViewState?.detail !== RepoDetailView.DASHBOARDS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(
      html`
        <gr-repo-dashboards
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-dashboards>
      `,
      true
    );
  }

  private renderRepoSubmitRequirements() {
    if (
      this.view !== GerritView.REPO ||
      this.repoViewState?.detail !== RepoDetailView.SUBMIT_REQUIREMENTS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(
      html`
        <gr-repo-submit-requirements
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-submit-requirements>
      `,
      true
    );
  }

  private renderRepoLabels() {
    if (
      this.view !== GerritView.REPO ||
      this.repoViewState?.detail !== RepoDetailView.LABELS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(
      html`
        <gr-repo-labels
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-labels>
      `,
      true
    );
  }

  /* Group pages */

  private renderGroup() {
    if (
      this.view !== GerritView.GROUP ||
      this.groupViewState?.detail !== undefined
    ) {
      return nothing;
    }

    return this.renderDetailPanel(html`
      <gr-group
        .groupId=${this.groupViewState?.groupId}
        .canDelete=${this.canDeleteCapability}
        @name-changed=${this.updateGroupName}
      ></gr-group>
    `);
  }

  private renderGroupMembers() {
    if (
      this.view !== GerritView.GROUP ||
      this.groupViewState?.detail !== GroupDetailView.MEMBERS
    ) {
      return nothing;
    }

    return this.renderDetailPanel(html`
      <gr-group-members
        .groupId=${this.groupViewState.groupId}
      ></gr-group-members>
    `);
  }

  private renderGroupAuditLog() {
    if (
      this.view !== GerritView.GROUP ||
      this.groupViewState?.detail !== GroupDetailView.LOG
    ) {
      return nothing;
    }

    return this.renderDetailPanel(
      html`
        <gr-group-audit-log
          class="table"
          .groupId=${this.groupViewState.groupId}
        ></gr-group-audit-log>
      `,
      true
    );
  }

  /* Lifecycle */

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('groupId')) {
      void this.computeGroupName();
    }

    if (changedProperties.has('repoName')) {
      void this.loadRepoBrowseUrl(this.repoName);
    }
  }

  override updated(changedProperties: PropertyValues) {
    this.applyAdminStyles();

    const viewport =
      this.shadowRoot?.querySelector<HTMLElement>('.tabViewport') ?? undefined;

    const strip =
      viewport?.querySelector<HTMLElement>('.mobileTabs') ?? undefined;

    const viewportChanged =
      viewport !== this.tabViewport || strip !== this.tabStrip;

    if (viewportChanged) {
      this.tabResizeObserver.disconnect();

      this.tabViewport = viewport;
      this.tabStrip = strip;
      this.tabViewportWidth = 0;

      if (viewport) this.tabResizeObserver.observe(viewport);
      if (strip) this.tabResizeObserver.observe(strip);
    }

    if (
      viewportChanged ||
      changedProperties.has('view') ||
      changedProperties.has('adminViewState') ||
      changedProperties.has('repoViewState') ||
      changedProperties.has('groupViewState') ||
      changedProperties.has('filteredLinks') ||
      changedProperties.has('subsectionLinks')
    ) {
      void this.scrollActiveTabIntoView();
    }

    this.updateTabScrollState();
  }

  /**
   * Apply only the extracted administration layout rules to the
   * application and this administration view's sidebar.
   */
  private applyAdminStyles() {
    if (!this.isConnected) return;

    const stylesheet = GrAdminView.getAdminLayoutStyles();
    if (!stylesheet) return;

    const appRoot = this.getRootNode();

    const roots = [
      appRoot instanceof ShadowRoot &&
      appRoot.host.localName === 'gr-app-element'
        ? appRoot
        : undefined,
      this.shadowRoot?.querySelector('gr-page-nav.navStyles')?.shadowRoot,
    ];

    for (const root of roots) {
      if (root && !root.adoptedStyleSheets.includes(stylesheet)) {
        root.adoptedStyleSheets = [...root.adoptedStyleSheets, stylesheet];
      }
    }
  }

  /* Mobile tab scrolling */

  private updateTabScrollState() {
    const viewport = this.tabViewport;

    if (!viewport?.clientWidth) {
      this.canScrollLeft = false;
      this.canScrollRight = false;
      return;
    }

    const maxScroll = Math.max(0, viewport.scrollWidth - viewport.clientWidth);

    const rtl = getComputedStyle(viewport).direction === 'rtl';
    const position = rtl
      ? maxScroll + viewport.scrollLeft
      : viewport.scrollLeft;

    this.canScrollLeft = maxScroll > 1 && position > 1;
    this.canScrollRight = maxScroll > 1 && position < maxScroll - 1;
  }

  private scrollTabs(direction: -1 | 1) {
    const viewport = this.tabViewport;
    const strip = this.tabStrip;

    if (!viewport || !strip) return;

    const bounds = viewport.getBoundingClientRect();

    const tabs = Array.from(
      strip.querySelectorAll<HTMLElement>('md-primary-tab, a')
    )
      .map(tab => tab.getBoundingClientRect())
      .sort((a, b) => a.left - b.left);

    const next =
      direction === 1
        ? tabs.find(tab => tab.right > bounds.right + 1)
        : tabs.reverse().find(tab => tab.left < bounds.left - 1);

    const offset = next
      ? direction === 1
        ? next.right - bounds.right
        : next.left - bounds.left
      : direction * viewport.clientWidth;

    viewport.scrollBy({
      left: offset,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  }

  private async scrollActiveTabIntoView() {
    const viewport = this.tabViewport;
    const strip = this.tabStrip;

    if (!viewport || !strip) return;

    if (strip instanceof MdTabs) {
      await strip.updateComplete;
      await Promise.all(strip.tabs.map(tab => tab.updateComplete));
    }

    if (
      !this.isConnected ||
      viewport !== this.tabViewport ||
      strip !== this.tabStrip
    ) {
      return;
    }

    const selected = strip.querySelector<HTMLElement>('.currentTab, .active');

    if (selected) {
      this.scrollTabIntoView(selected);
    }

    this.updateTabScrollState();
  }

  private handleTabFocus(e: FocusEvent) {
    if (!(e.target instanceof HTMLElement)) return;

    const tab = e.target.closest<HTMLElement>('md-primary-tab, a');

    if (tab) {
      this.scrollTabIntoView(tab);
    }
  }

  private scrollTabIntoView(tab: HTMLElement) {
    const viewport = this.tabViewport;

    if (!viewport?.clientWidth) return;

    const bounds = viewport.getBoundingClientRect();
    const tabBounds = tab.getBoundingClientRect();

    if (tabBounds.left < bounds.left) {
      viewport.scrollBy({
        left: tabBounds.left - bounds.left,
      });
    } else if (tabBounds.right > bounds.right) {
      viewport.scrollBy({
        left: tabBounds.right - bounds.right,
      });
    }
  }

  /* Navigation loading */

  async reload() {
    try {
      this.reloading = true;
      this.adminMenuOpen = false;
      this.subsectionLinks = [];

      const [account] = await Promise.all([
        this.restApiService.getAccount(),
        this.getPluginLoader().awaitPluginsLoaded(),
      ]);

      if (account) {
        const capabilities = await this.restApiService.getAccountCapabilities([
          'deleteGroup',
        ]);

        this.canDeleteCapability = !!capabilities?.deleteGroup;
      }

      let options: AdminNavLinksOption | undefined;

      if (this.repoName) {
        options = {
          repoName: this.repoName,
        };
      } else if (this.groupId) {
        const isAdmin = await this.restApiService.getIsAdmin();

        const groupOwner = await this.restApiService.getIsGroupOwner(
          this.groupName
        );

        options = {
          groupId: this.groupId,
          groupName: this.groupName,
          groupIsInternal: this.groupIsInternal,
          isAdmin,
          groupOwner,
        };
      }

      const {links, expandedSection} = await getAdminLinks(
        account,
        async () => {
          const capabilities =
            await this.restApiService.getAccountCapabilities();

          if (!capabilities) {
            throw new Error('getAccountCapabilities returns undefined');
          }

          return capabilities;
        },
        () => this.getPluginLoader().jsApiService.getAdminMenuLinks(),
        options
      );

      this.filteredLinks = links;
      this.breadcrumbParentName = expandedSection?.name ?? '';

      if (!expandedSection) return;

      this.subsectionLinks = [
        expandedSection,
        ...(expandedSection.children ?? []),
      ].map(section => {
        return {
          text: section.detailType ? section.name : 'Home',
          value: section.view + (section.detailType ?? ''),
          view: section.view,
          url: section.url,
          detailType: section.detailType,
          parent: this.groupId ?? this.repoName,
        };
      });
    } finally {
      this.reloading = false;
    }
  }

  private getDetailView() {
    if (this.view === GerritView.REPO) {
      return this.repoViewState?.detail ?? RepoDetailView.GENERAL;
    }

    if (this.view === GerritView.GROUP) {
      return this.groupViewState?.detail;
    }

    return undefined;
  }

  // private but used in test
  selectedIsCurrentPage(selected: AdminSubsectionLink) {
    return (
      !!this.view &&
      selected.parent === (this.repoName ?? this.groupId) &&
      selected.view === this.view &&
      selected.detailType === this.getDetailView()
    );
  }

  // private but used in test
  handleSubsectionChange(e: ValueChangedEvent<string> | string) {
    if (this.reloading) return;

    const value = typeof e === 'string' ? e : e.detail.value;

    const selected = this.subsectionLinks?.find(
      section => section.value === value
    );

    if (
      !selected ||
      selected.url === undefined ||
      this.selectedIsCurrentPage(selected)
    ) {
      return;
    }

    this.getNavigation().setUrl(selected.url);
  }

  isAdminView(): boolean {
    return (
      this.view === GerritView.ADMIN ||
      this.view === GerritView.GROUP ||
      this.view === GerritView.REPO
    );
  }

  needsReload(): boolean {
    if (!this.isAdminView()) return false;

    let needsReload = false;

    const newRepoName =
      this.view === GerritView.REPO ? this.repoViewState?.repo : undefined;

    const newGroupId =
      this.view === GerritView.GROUP ? this.groupViewState?.groupId : undefined;

    if (newRepoName !== this.repoName) {
      this.repoName = newRepoName;
      needsReload = true;
    }

    if (newGroupId !== this.groupId) {
      this.groupId = newGroupId;
      needsReload = true;
    }

    return (
      needsReload ||
      (!!this.breadcrumbParentName && !newRepoName && !newGroupId)
    );
  }

  // private but used in test
  computeLinkURL(link?: NavLink | SubsectionInterface) {
    return link?.url || '';
  }

  private computeSelectedClass(
    itemView?: GerritView | AdminChildView,
    detailType?: GroupDetailView | RepoDetailView
  ) {
    if (!this.view || itemView === undefined) return '';

    if (this.view === GerritView.ADMIN) {
      return itemView === this.adminViewState?.adminView ? 'selected' : '';
    }

    return this.isAdminView() &&
      itemView === this.view &&
      detailType === this.getDetailView()
      ? 'selected'
      : '';
  }

  /* Group information */

  // private but used in test
  async computeGroupName() {
    if (!this.groupId) return;

    const group = await this.restApiService.getGroupConfig(this.groupId);
    if (!group?.name) return;

    this.groupName = group.name;
    this.groupIsInternal = INTERNAL_GROUP_REGEX.test(group.id);

    await this.reload();
  }

  private async updateGroupName(e: CustomEvent<GroupNameChangedDetail>) {
    this.groupName = e.detail.name;

    await this.reload();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-view': GrAdminView;
  }
}
