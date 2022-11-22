/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-page-nav/gr-page-nav';
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
import '../gr-repo-list/gr-repo-list';
import {getBaseUrl} from '../../../utils/url-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  AdminNavLinksOption,
  getAdminLinks,
  NavLink,
  SubsectionInterface,
} from '../../../utils/admin-nav-util';
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
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {ValueChangedEvent} from '../../../types/events';
import {
  AdminChildView,
  adminViewModelToken,
  AdminViewState,
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
  private account?: AccountDetailInfo;

  @state()
  view?: GerritView;

  @state()
  adminViewState?: AdminViewState;

  @state()
  groupViewState?: GroupViewState;

  @state()
  repoViewState?: RepoViewState;

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

  static override get styles() {
    return [
      sharedStyles,
      menuPageStyles,
      pageNavStyles,
      css`
        .breadcrumbText {
          /* Same as dropdown trigger so chevron spacing is consistent. */
          padding: 5px 4px;
        }
        gr-icon {
          margin: 0 var(--spacing-xs);
        }
        .breadcrumb {
          align-items: center;
          display: flex;
        }
        .mainHeader {
          align-items: baseline;
          border-bottom: 1px solid var(--border-color);
          display: flex;
        }
        .selectText {
          display: none;
        }
        .selectText.show {
          display: inline-block;
        }
        .main.breadcrumbs:not(.table) {
          margin-top: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    if (!this.isAdminView()) return nothing;
    return html`
      <gr-page-nav class="navStyles">
        <ul class="sectionContent">
          ${this.filteredLinks?.map(item => this.renderAdminNav(item))}
        </ul>
      </gr-page-nav>
      ${this.renderSubsectionLinks()} ${this.renderRepoList()}
      ${this.renderGroupList()} ${this.renderPluginList()}
      ${this.renderRepoMain()} ${this.renderGroup()}
      ${this.renderGroupMembers()} ${this.renderGroupAuditLog()}
      ${this.renderRepoDetailList()} ${this.renderRepoCommands()}
      ${this.renderRepoAccess()} ${this.renderRepoDashboards()}
    `;
  }

  private renderAdminNav(item: NavLink) {
    return html`
      <li class="sectionTitle ${this.computeSelectedClass(item.view)}">
        <a class="title" href=${this.computeLinkURL(item)} rel="noopener"
          >${item.name}</a
        >
      </li>
      ${item.children?.map(child => this.renderAdminNavChild(child))}
      ${this.renderAdminNavSubsection(item)}
    `;
  }

  private renderAdminNavChild(child: SubsectionInterface) {
    return html`
      <li class=${this.computeSelectedClass(child.view)}>
        <a href=${this.computeLinkURL(child)} rel="noopener">${child.name}</a>
      </li>
    `;
  }

  private renderAdminNavSubsection(item: NavLink) {
    if (!item.subsection) return nothing;

    return html`
      <!--If a section has a subsection, render that.-->
      <li class=${this.computeSelectedClass(item.subsection.view)}>
        ${this.renderAdminNavSubsectionUrl(item.subsection)}
      </li>
      <!--Loop through the links in the sub-section.-->
      ${item.subsection?.children?.map(child =>
        this.renderAdminNavSubsectionChild(child)
      )}
    `;
  }

  private renderAdminNavSubsectionUrl(subsection?: SubsectionInterface) {
    if (!subsection!.url) return html`${subsection!.name}`;

    return html`
      <a class="title" href=${this.computeLinkURL(subsection)} rel="noopener">
        ${subsection!.name}</a
      >
    `;
  }

  private renderAdminNavSubsectionChild(child: SubsectionInterface) {
    return html`
      <li
        class="subsectionItem ${this.computeSelectedClass(
          child.view,
          child.detailType
        )}"
      >
        <a href=${this.computeLinkURL(child)}>${child.name}</a>
      </li>
    `;
  }

  private renderSubsectionLinks() {
    if (!this.subsectionLinks?.length) return nothing;

    return html`
      <section class="mainHeader">
        <span class="breadcrumb">
          <span class="breadcrumbText">${this.breadcrumbParentName}</span>
          <gr-icon icon="chevron_right"></gr-icon>
        </span>
        <gr-dropdown-list
          id="pageSelect"
          value=${ifDefined(this.computeSelectValue())}
          .items=${this.subsectionLinks}
          @value-change=${this.handleSubsectionChange}
        >
        </gr-dropdown-list>
      </section>
    `;
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
          .createBranch=${this.repoViewState.createBranch}
          .createPath=${this.repoViewState.createPath}
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
        ></gr-repo-dashboards>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('groupId')) {
      this.computeGroupName();
    }
  }

  async reload() {
    try {
      this.reloading = true;
      const promises: [Promise<AccountDetailInfo | undefined>, Promise<void>] =
        [
          this.restApiService.getAccount(),
          this.getPluginLoader().awaitPluginsLoaded(),
        ];
      const result = await Promise.all(promises);
      this.account = result[0];
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
        this.account,
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
        this.subsectionLinks = [];
        return;
      }
      this.subsectionLinks = [res.expandedSection]
        .concat(res.expandedSection.children ?? [])
        .map(section => {
          return {
            text: !section.detailType ? 'Home' : section.name,
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

  private computeSelectValue() {
    return `${this.view}${this.getDetailView() ?? ''}`;
  }

  // private but used in test
  selectedIsCurrentPage(selected: AdminSubsectionLink) {
    if (!this.view) return false;

    return (
      selected.parent === (this.repoName ?? this.groupId) &&
      selected.view === this.view &&
      selected.detailType === this.getDetailView()
    );
  }

  // private but used in test
  handleSubsectionChange(e: ValueChangedEvent<string>) {
    if (!this.subsectionLinks) return;

    // The GrDropdownList items are subsectionLinks, so find(...) always return
    // an item subsectionLinks and never returns undefined
    const selected = this.subsectionLinks.find(
      section => section.value === e.detail.value
    )!;

    // This is when it gets set initially.
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
    if (!link || typeof link.url === 'undefined') return '';

    if ((link as NavLink).target || !(link as NavLink).noBaseUrl) {
      return link.url;
    }
    return `//${window.location.host}${getBaseUrl()}${link.url}`;
  }

  private computeSelectedClass(
    itemView?: GerritView | AdminChildView,
    detailType?: GroupDetailView | RepoDetailView
  ) {
    if (!this.view) return '';
    // Group view state is structured differently than admin view state. Compute
    // selected differently for groups.
    // TODO(wyatta): Simplify this when all routes work like group view state.
    if (this.view === GerritView.GROUP && itemView === GerritView.GROUP) {
      if (!this.groupViewState?.detail && !detailType) {
        return 'selected';
      }
      if (this.groupViewState?.detail === detailType) {
        return 'selected';
      }
      return '';
    }

    if (this.view === GerritView.REPO && itemView === GerritView.REPO) {
      if (!this.repoViewState?.detail && !detailType) {
        return 'selected';
      }
      if (this.repoViewState?.detail === detailType) {
        return 'selected';
      }
      return '';
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
