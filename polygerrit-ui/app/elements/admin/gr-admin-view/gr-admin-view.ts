/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {MdTabs} from '@material/web/tabs/tabs.js';
import '@material/web/tabs/primary-tab.js';
import '@material/web/tabs/secondary-tab.js';
import '../../shared/gr-icon/gr-icon';
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
import {
  AccountDetailInfo,
  GroupId,
  GroupName,
  RepoName,
} from '../../../types/common';
import {GroupNameChangedDetail} from '../gr-group/gr-group';
import {getAppContext} from '../../../services/app-context';
import {
  GerritView,
  routerModelToken,
} from '../../../services/router/router-model';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, state} from 'lit/decorators.js';
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
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

type TabType = 'primary' | 'secondary';
type ScrollDirection = 'left' | 'right';

interface TabScrollState {
  left: boolean;
  right: boolean;
}

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
  @state()
  view?: GerritView;

  @state()
  adminViewState?: AdminViewState;

  @state()
  groupViewState?: GroupViewState;

  @state()
  repoViewState?: RepoViewState;

  @state() canDeleteCapability = false;

  @state() private breadcrumbParentName?: string;

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

  @state()
  private primaryScroll: TabScrollState = {
    left: false,
    right: false,
  };

  @state()
  private secondaryScroll: TabScrollState = {
    left: false,
    right: false,
  };

  private readonly tabScrollObservers = new Map<
    TabType,
    IntersectionObserver
  >();

  private reloading = false;

  // private but used in the tests
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
  }

  override disconnectedCallback() {
    super.disconnectedCallback();

    for (const observer of this.tabScrollObservers.values()) {
      observer.disconnect();
    }

    this.tabScrollObservers.clear();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          background: var(--background-color-secondary);
        }
        .breadcrumb {
          align-items: center;
          background: var(--background-color-primary);
          color: var(--primary-text-color);
          display: flex;
          flex-wrap: wrap;
          font-size: 24px;
          gap: var(--spacing-s);
          line-height: 32px;
          padding: var(--spacing-l) var(--spacing-xxl);
        }
        .breadcrumb gr-icon {
          --gr-icon-size: 20px;
          color: var(--deemphasized-text-color);
        }
        .breadcrumbCurrent {
          font-weight: 600;
          overflow-wrap: anywhere;
        }
        .tabRow {
          align-items: stretch;
          border-bottom: 1px solid var(--border-color);
          box-sizing: border-box;
          display: flex;
          min-width: 0;
        }
        .tabRow md-tabs {
          flex: 0 1 auto;
          margin-inline-end: auto;
          min-width: 0;
          width: max-content;

          --md-sys-color-primary: var(--link-color);
          --md-sys-color-on-surface: var(--primary-text-color);
          --md-sys-color-on-surface-variant: var(--primary-text-color);

          --md-divider-color: transparent;

          --md-primary-tab-active-indicator-color: transparent;
          --md-primary-tab-active-label-text-color: var(--link-color);
          --md-primary-tab-active-hover-label-text-color: var(--link-color);
          --md-primary-tab-active-focus-label-text-color: var(--link-color);
          --md-primary-tab-active-pressed-label-text-color: var(--link-color);
          --md-primary-tab-label-text-color: var(--primary-text-color);
          --md-primary-tab-label-text-size: 14px;
          --md-primary-tab-label-text-weight: 500;

          --md-secondary-tab-active-indicator-color: var(--link-color);
          --md-secondary-tab-active-indicator-height: 3px;
          --md-secondary-tab-active-label-text-color: var(--link-color);
          --md-secondary-tab-active-hover-label-text-color: var(--link-color);
          --md-secondary-tab-active-focus-label-text-color: var(--link-color);
          --md-secondary-tab-active-pressed-label-text-color: var(--link-color);
          --md-secondary-tab-label-text-color: var(--primary-text-color);
          --md-secondary-tab-label-text-size: 14px;
          --md-secondary-tab-label-text-weight: 500;
        }
        .primaryTabs {
          background: var(--background-color-primary);
          --md-primary-tab-container-color: var(--background-color-primary);
          --md-primary-tab-container-height: 48px;
        }
        .secondaryTabs {
          background: var(--background-color-tertiary);
          --md-secondary-tab-container-color: var(--background-color-tertiary);
          --md-secondary-tab-container-height: 44px;
        }
        md-primary-tab,
        md-secondary-tab {
          flex: 0 0 auto;
          white-space: nowrap;
          padding-inline: var(--spacing-xxl);
        }
        md-primary-tab {
          position: relative;
        }
        md-primary-tab[active]::after {
          background: var(--link-color);
          content: '';
          height: 3px;
          inset: auto 0 0;
          pointer-events: none;
          position: absolute;
        }
        .externalLinkIcon {
          --gr-icon-size: 16px;
          color: inherit;
        }
        .moreTabs {
          align-items: center;
          background: inherit;
          border: 0;
          color: var(--link-color);
          cursor: pointer;
          display: flex;
          flex: 0 0 44px;
          justify-content: center;
          padding: 0;
        }
        .moreTabs:not(:disabled):hover {
          background-image: linear-gradient(
            var(--hover-background-color),
            var(--hover-background-color)
          );
        }
        .moreTabs:disabled {
          color: var(--disabled-foreground);
          cursor: default;
        }
        .moreTabs.previous:disabled {
          visibility: hidden;
        }
        .moreTabs gr-icon {
          --gr-icon-size: 20px;
          color: inherit;
        }
        .content {
          box-sizing: border-box;
          min-width: 0;
          padding: var(--spacing-xxl);
        }
        .main {
          box-sizing: border-box;
          margin: 0 auto;
          max-width: 50em;
          min-width: 0;
          width: 100%;
        }
        .main.table {
          max-width: none;
        }
        @media only screen and (max-width: 50em) {
          .breadcrumb {
            font-size: 20px;
            padding: var(--spacing-l);
          }
          .tabRow {
            position: relative;
            padding-inline-start: var(--spacing-l);
            --tab-scroll-button-width: 36px;
          }
          md-primary-tab,
          md-secondary-tab {
            padding-inline: var(--spacing-l);
          }
          md-primary-tab:not(:first-child),
          md-secondary-tab:not(:first-child) {
            scroll-margin-inline-start: calc(
              var(--tab-scroll-button-width) - var(--spacing-l)
            );
          }
          .content {
            padding: var(--spacing-l);
          }
          .moreTabs {
            flex-basis: var(--tab-scroll-button-width);
          }
          .moreTabs.previous {
            position: absolute;
            inset-block: 0;
            inset-inline-start: 0;
            width: var(--tab-scroll-button-width);
            z-index: 2;
          }
        }
      `,
    ];
  }

  override render() {
    if (!this.isAdminView()) return nothing;

    return html`
      ${this.renderBreadcrumb()} ${this.renderAdminTabs()}
      ${this.renderSubsectionLinks()}
      <div class="content">
        ${this.renderRepoList()} ${this.renderGroupList()}
        ${this.renderPluginList()} ${this.renderRepoMain()}
        ${this.renderGroup()} ${this.renderGroupMembers()}
        ${this.renderGroupAuditLog()} ${this.renderRepoDetailList()}
        ${this.renderRepoCommands()} ${this.renderRepoAccess()}
        ${this.renderRepoDashboards()} ${this.renderRepoSubmitRequirements()}
        ${this.renderRepoLabels()} ${this.renderServerInfo()}
      </div>
    `;
  }

  private renderBreadcrumb() {
    if (this.view === GerritView.ADMIN) {
      let title = this.filteredLinks?.find(
        item => item.view === this.adminViewState?.adminView
      )?.name;

      if (!title) {
        switch (this.adminViewState?.adminView) {
          case AdminChildView.REPOS:
            title = 'Repositories';
            break;
          case AdminChildView.GROUPS:
            title = 'Groups';
            break;
          case AdminChildView.PLUGINS:
            title = 'Plugins';
            break;
          case AdminChildView.SERVER_INFO:
            title = 'Server Info';
            break;
          default:
            title = 'Admin';
        }
      }

      return html`
        <div class="breadcrumb">
          <span class="breadcrumbCurrent">${title}</span>
        </div>
      `;
    }

    const name =
      this.view === GerritView.REPO
        ? this.repoName
        : this.groupName ?? this.groupId;

    if (!name) return nothing;

    return html`
      <div class="breadcrumb">
        <span> ${this.view === GerritView.REPO ? 'Repository' : 'Group'} </span>
        <gr-icon icon="chevron_right"></gr-icon>
        <span class="breadcrumbCurrent">${name}</span>
      </div>
    `;
  }

  private renderAdminTabs() {
    if (!this.filteredLinks?.length) return nothing;

    return html`
      <div class="tabRow primaryTabs">
        ${this.renderScrollButton('primary', 'left')}
        <md-tabs aria-label="Admin sections">
          ${this.filteredLinks.map(
            item => html`
              <md-primary-tab
                ?inline-icon=${item.view === undefined}
                ?active=${item.view !== undefined &&
                this.computeSelectedClass(item.view) === 'selected'}
                aria-label=${item.view === undefined
                  ? `${item.name} (opens in a new tab)`
                  : item.name}
                @click=${(e: MouseEvent) => {
                  const url = this.computeLinkURL(item);
                  if (!url) return;

                  if (item.view === undefined) {
                    e.preventDefault();
                    window.open(url, '_blank', 'noopener,noreferrer');
                    return;
                  }

                  this.getNavigation().setUrl(url);
                }}
              >
                ${item.view === undefined
                  ? html`
                      <gr-icon
                        slot="icon"
                        class="externalLinkIcon"
                        icon="open_in_new"
                        aria-hidden="true"
                      ></gr-icon>
                    `
                  : nothing}
                ${item.name}
              </md-primary-tab>
            `
          )}
        </md-tabs>
        ${this.renderScrollButton('primary', 'right')}
      </div>
    `;
  }

  private renderSubsectionLinks() {
    if (!this.subsectionLinks?.length) return nothing;

    return html`
      <div class="tabRow secondaryTabs">
        ${this.renderScrollButton('secondary', 'left')}
        <md-tabs
          aria-label=${this.view === GerritView.REPO
            ? 'Repository sections'
            : 'Group sections'}
        >
          ${this.subsectionLinks.map(
            section => html`
              <md-secondary-tab
                ?active=${this.selectedIsCurrentPage(section)}
                @click=${() => this.navigateToSubsection(section)}
              >
                ${section.text}
              </md-secondary-tab>
            `
          )}
        </md-tabs>
        ${this.renderScrollButton('secondary', 'right')}
      </div>
    `;
  }

  private renderScrollButton(type: TabType, direction: ScrollDirection) {
    const scroll =
      type === 'primary' ? this.primaryScroll : this.secondaryScroll;

    return html`
      <button
        type="button"
        class="moreTabs ${direction === 'left' ? 'previous' : ''}"
        aria-label=${direction === 'left'
          ? 'Show previous tabs'
          : 'Show more tabs'}
        ?disabled=${!scroll[direction]}
        @click=${() => this.scrollTabs(type, direction)}
      >
        <gr-icon
          icon=${direction === 'left' ? 'chevron_left' : 'chevron_right'}
        ></gr-icon>
      </button>
    `;
  }

  private scrollTabs(type: TabType, direction: ScrollDirection) {
    const tabs = this.shadowRoot?.querySelector<MdTabs>(`.${type}Tabs md-tabs`);

    if (!tabs?.tabs.length) return;

    const bounds = tabs.getBoundingClientRect();
    const tabBounds = tabs.tabs.map(tab => tab.getBoundingClientRect());

    const leftButton =
      tabs.parentElement?.querySelector<HTMLButtonElement>(
        '.moreTabs.previous'
      );

    const visibleStart = leftButton?.disabled
      ? bounds.left
      : Math.max(
          bounds.left,
          leftButton?.getBoundingClientRect().right ?? bounds.left
        );

    const currentIndex = tabBounds.reduce(
      (closest, tab, index) =>
        Math.abs(tab.left - visibleStart) <
        Math.abs(tabBounds[closest].left - visibleStart)
          ? index
          : closest,
      0
    );

    const nextIndex =
      direction === 'right'
        ? Math.min(currentIndex + 1, tabs.tabs.length - 1)
        : Math.max(currentIndex - 1, 0);

    tabs.tabs[nextIndex].scrollIntoView({
      behavior: 'instant',
      block: 'nearest',
      inline: 'start',
    });

    this.updateTabScrollState(type, tabs);
  }

  private setTabScrollState(type: TabType, left: boolean, right: boolean) {
    const current =
      type === 'primary' ? this.primaryScroll : this.secondaryScroll;

    if (current.left === left && current.right === right) return;

    if (type === 'primary') {
      this.primaryScroll = {left, right};
    } else {
      this.secondaryScroll = {left, right};
    }
  }

  private updateTabScrollState(type: TabType, tabs: MdTabs) {
    const first = tabs.tabs[0];
    const last = tabs.tabs[tabs.tabs.length - 1];

    if (!first || !last) {
      this.setTabScrollState(type, false, false);
      return;
    }

    const bounds = tabs.getBoundingClientRect();

    this.setTabScrollState(
      type,
      Math.round(first.getBoundingClientRect().left - bounds.left) < 0,
      Math.round(last.getBoundingClientRect().right - bounds.right) > 0
    );
  }

  private observeTabScroll(type: TabType) {
    this.tabScrollObservers.get(type)?.disconnect();
    this.tabScrollObservers.delete(type);

    const tabs = this.shadowRoot?.querySelector<MdTabs>(`.${type}Tabs md-tabs`);

    if (!tabs || !tabs.children.length) {
      this.setTabScrollState(type, false, false);
      return;
    }

    const observer = new IntersectionObserver(
      () => {
        if (
          !this.isConnected ||
          this.tabScrollObservers.get(type) !== observer
        ) {
          return;
        }

        this.updateTabScrollState(type, tabs);
      },
      {
        root: tabs,
        threshold: [0, 1],
      }
    );

    this.tabScrollObservers.set(type, observer);

    for (const tab of tabs.children) {
      observer.observe(tab);
    }
  }

  private renderRepoList() {
    if (this.view !== GerritView.ADMIN) return nothing;
    if (this.adminViewState?.adminView !== AdminChildView.REPOS) return nothing;

    return html`
      <div class="main table">
        <gr-repo-list
          class="table"
          .params=${this.adminViewState}
        ></gr-repo-list>
      </div>
    `;
  }

  private renderGroupList() {
    if (this.view !== GerritView.ADMIN) return nothing;
    if (this.adminViewState?.adminView !== AdminChildView.GROUPS)
      return nothing;

    return html`
      <div class="main table">
        <gr-admin-group-list class="table" .params=${this.adminViewState}>
        </gr-admin-group-list>
      </div>
    `;
  }

  private renderPluginList() {
    if (this.view !== GerritView.ADMIN) return nothing;
    if (this.adminViewState?.adminView !== AdminChildView.PLUGINS)
      return nothing;

    return html`
      <div class="main table">
        <gr-plugin-list
          class="table"
          .params=${this.adminViewState}
        ></gr-plugin-list>
      </div>
    `;
  }

  private renderRepoMain() {
    if (this.view !== GerritView.REPO) return nothing;
    const detail = this.repoViewState?.detail ?? RepoDetailView.GENERAL;
    if (detail !== RepoDetailView.GENERAL) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo .repo=${this.repoViewState?.repo}></gr-repo>
      </div>
    `;
  }

  private renderGroup() {
    if (this.view !== GerritView.GROUP) return nothing;
    if (this.groupViewState?.detail !== undefined) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-group
          .groupId=${this.groupViewState?.groupId}
          .canDelete=${this.canDeleteCapability}
          @name-changed=${(e: CustomEvent<GroupNameChangedDetail>) => {
            this.updateGroupName(e);
          }}
        ></gr-group>
      </div>
    `;
  }

  private renderGroupMembers() {
    if (this.view !== GerritView.GROUP) return nothing;
    if (this.groupViewState?.detail !== GroupDetailView.MEMBERS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-group-members
          .groupId=${this.groupViewState?.groupId}
        ></gr-group-members>
      </div>
    `;
  }

  private renderGroupAuditLog() {
    if (this.view !== GerritView.GROUP) return nothing;
    if (this.groupViewState?.detail !== GroupDetailView.LOG) return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-group-audit-log
          class="table"
          .groupId=${this.groupViewState?.groupId}
        ></gr-group-audit-log>
      </div>
    `;
  }

  private renderRepoDetailList() {
    if (this.view !== GerritView.REPO) return nothing;
    const detail = this.repoViewState?.detail;
    if (detail !== RepoDetailView.BRANCHES && detail !== RepoDetailView.TAGS) {
      return nothing;
    }

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-detail-list
          class="table"
          .params=${this.repoViewState}
        ></gr-repo-detail-list>
      </div>
    `;
  }

  private renderRepoCommands() {
    if (this.view !== GerritView.REPO) return nothing;
    if (this.repoViewState?.detail !== RepoDetailView.COMMANDS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-commands
          .repo=${this.repoViewState.repo}
          .createEdit=${this.repoViewState.createEdit}
        ></gr-repo-commands>
      </div>
    `;
  }

  private renderRepoAccess() {
    if (this.view !== GerritView.REPO) return nothing;
    if (this.repoViewState?.detail !== RepoDetailView.ACCESS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-access .repo=${this.repoViewState.repo}></gr-repo-access>
      </div>
    `;
  }

  private renderRepoDashboards() {
    if (this.view !== GerritView.REPO) return nothing;
    if (this.repoViewState?.detail !== RepoDetailView.DASHBOARDS)
      return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-dashboards
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-dashboards>
      </div>
    `;
  }

  private renderRepoSubmitRequirements() {
    if (this.view !== GerritView.REPO) return nothing;
    if (this.repoViewState?.detail !== RepoDetailView.SUBMIT_REQUIREMENTS)
      return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-submit-requirements
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-submit-requirements>
      </div>
    `;
  }

  private renderRepoLabels() {
    if (this.view !== GerritView.REPO) return nothing;
    if (this.repoViewState?.detail !== RepoDetailView.LABELS) return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-labels
          .repo=${this.repoViewState.repo}
          .params=${this.repoViewState}
        ></gr-repo-labels>
      </div>
    `;
  }

  private renderServerInfo() {
    if (this.view !== GerritView.ADMIN) return nothing;
    if (this.adminViewState?.adminView !== AdminChildView.SERVER_INFO)
      return nothing;

    return html`
      <div class="main table">
        <gr-server-info class="table"></gr-server-info>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('groupId')) {
      this.computeGroupName();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (!this.isConnected) return;

    if (
      changedProperties.has('filteredLinks') ||
      changedProperties.has('view')
    ) {
      this.observeTabScroll('primary');
    }

    if (
      changedProperties.has('subsectionLinks') ||
      changedProperties.has('view')
    ) {
      this.observeTabScroll('secondary');
    }
  }

  async reload() {
    try {
      this.reloading = true;
      // There is async barrier inside reload function, we need to clear
      // subsectionLinks now, because the element might render while waiting for
      // RestApi responses breaking the invariant that this.view is part of
      // subsectionLinks if non-empty.
      this.subsectionLinks = [];
      const promises: [Promise<AccountDetailInfo | undefined>, Promise<void>] =
        [
          this.restApiService.getAccount(),
          this.getPluginLoader().awaitPluginsLoaded(),
        ];
      const result = await Promise.all(promises);
      const account = result[0];

      if (account) {
        const capabilities = await this.restApiService.getAccountCapabilities([
          'deleteGroup',
        ]);
        this.canDeleteCapability = !!capabilities?.deleteGroup;
      }

      let options: AdminNavLinksOption | undefined = undefined;
      if (this.repoName) {
        options = {repoName: this.repoName};
      } else if (this.groupId) {
        const isAdmin = await this.restApiService.getIsAdmin();
        const isOwner = await this.restApiService.getIsGroupOwner(
          this.groupName
        );

        options = {
          groupId: this.groupId,
          groupName: this.groupName,
          groupIsInternal: this.groupIsInternal,
          isAdmin,
          groupOwner: isOwner,
        };
      }

      const res = await getAdminLinks(
        account,
        () =>
          this.restApiService.getAccountCapabilities().then(capabilities => {
            if (!capabilities) {
              throw new Error('getAccountCapabilities returns undefined');
            }

            return capabilities;
          }),
        () => this.getPluginLoader().jsApiService.getAdminMenuLinks(),
        options
      );

      this.filteredLinks = res.links;
      this.breadcrumbParentName = res.expandedSection
        ? res.expandedSection.name
        : '';

      if (!res.expandedSection) {
        return;
      }

      const children = res.expandedSection.children ?? [];
      const hasGeneral =
        this.view === GerritView.REPO &&
        children.some(section => section.detailType === RepoDetailView.GENERAL);

      const sections = hasGeneral
        ? children
        : [res.expandedSection].concat(children);

      this.subsectionLinks = sections.map(section => {
        return {
          text: !section.detailType ? 'General' : section.name,
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
    if (this.view === GerritView.REPO) return this.repoViewState?.detail;
    if (this.view === GerritView.GROUP) return this.groupViewState?.detail;
    return undefined;
  }

  // private but used in test
  selectedIsCurrentPage(selected: AdminSubsectionLink) {
    if (!this.view) return false;

    return (
      selected.parent === (this.repoName ?? this.groupId) &&
      selected.view === this.view &&
      (this.view === GerritView.REPO
        ? (selected.detailType ?? RepoDetailView.GENERAL) ===
          (this.getDetailView() ?? RepoDetailView.GENERAL)
        : selected.detailType === this.getDetailView())
    );
  }

  // private but used in test
  handleSubsectionChange(e: ValueChangedEvent<string>) {
    if (!this.subsectionLinks) return;

    const selected = this.subsectionLinks.find(
      section => section.value === e.detail.value
    );

    if (selected) this.navigateToSubsection(selected);
  }

  private navigateToSubsection(selected: AdminSubsectionLink) {
    if (this.selectedIsCurrentPage(selected)) return;
    if (selected.url === undefined) return;
    if (this.reloading) return;

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

    if (newRepoName !== this.repoName) {
      this.repoName = newRepoName;
      // Reloads the admin menu.
      needsReload = true;
    }

    const newGroupId =
      this.view === GerritView.GROUP ? this.groupViewState?.groupId : undefined;

    if (newGroupId !== this.groupId) {
      this.groupId = newGroupId;
      // Reloads the admin menu.
      needsReload = true;
    }

    if (
      this.breadcrumbParentName &&
      (this.view !== GerritView.GROUP || !this.groupViewState?.groupId) &&
      (this.view !== GerritView.REPO || !this.repoViewState?.repo)
    ) {
      needsReload = true;
    }

    return needsReload;
  }

  // private but used in test
  computeLinkURL(link?: NavLink | SubsectionInterface) {
    return link?.url || '';
  }

  private computeSelectedClass(itemView?: GerritView | AdminChildView) {
    if (!this.view) return '';

    if (this.view === GerritView.REPO) {
      return itemView === AdminChildView.REPOS ? 'selected' : '';
    }

    if (this.view === GerritView.GROUP) {
      return itemView === AdminChildView.GROUPS ? 'selected' : '';
    }

    return this.view === GerritView.ADMIN &&
      itemView === this.adminViewState?.adminView
      ? 'selected'
      : '';
  }

  // private but used in test
  async computeGroupName() {
    if (!this.groupId) return;

    const group = await this.restApiService.getGroupConfig(this.groupId);
    if (!group || !group.name) {
      return;
    }

    this.groupName = group.name;
    this.groupIsInternal = !!group.id.match(INTERNAL_GROUP_REGEX);

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
