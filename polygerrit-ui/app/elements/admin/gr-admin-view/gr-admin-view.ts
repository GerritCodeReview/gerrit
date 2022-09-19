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
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
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
import {GerritView} from '../../../services/router/router-model';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {ValueChangedEvent} from '../../../types/events';
import {
  AdminChildView,
  adminViewModelToken,
  AdminViewState,
} from '../../../models/views/admin';
import {GroupDetailView, GroupViewState} from '../../../models/views/group';
import {RepoDetailView, RepoViewState} from '../../../models/views/repo';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

export interface AdminSubsectionLink {
  text: string;
  value: string;
  view: GerritView;
  url?: string;
  detailType?: GroupDetailView | RepoDetailView;
  parent?: GroupId | RepoName;
}

type ViewState = AdminViewState | GroupViewState | RepoViewState;

function isAdminView(viewState?: ViewState): viewState is AdminViewState {
  return viewState?.view === GerritView.ADMIN;
}

function isGroupView(viewState?: ViewState): viewState is GroupViewState {
  return viewState?.view === GerritView.GROUP;
}

function isRepoView(viewState?: ViewState): viewState is RepoViewState {
  return viewState?.view === GerritView.REPO;
}

function getDetailView(
  state: ViewState
): GroupDetailView | RepoDetailView | undefined {
  if (state.view !== GerritView.ADMIN) {
    return state.detail;
  }
  return undefined;
}

@customElement('gr-admin-view')
export class GrAdminView extends LitElement {
  private account?: AccountDetailInfo;

  @property({type: Object})
  viewState?: ViewState;

  @property({type: String})
  adminView?: string;

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
  readonly jsAPI = getAppContext().jsApiService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getViewModel = resolve(this, adminViewModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getViewModel().state$,
      x => {
        this.viewState = x;
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
    if (!isAdminView(this.viewState)) return nothing;
    if (this.viewState.adminView !== AdminChildView.REPOS) return nothing;

    return html`
      <div class="main table">
        <gr-repo-list class="table" .params=${this.viewState}></gr-repo-list>
      </div>
    `;
  }

  private renderGroupList() {
    if (!isAdminView(this.viewState)) return nothing;
    if (this.viewState.adminView !== AdminChildView.GROUPS) return nothing;

    return html`
      <div class="main table">
        <gr-admin-group-list class="table" .params=${this.viewState}>
        </gr-admin-group-list>
      </div>
    `;
  }

  private renderPluginList() {
    if (!isAdminView(this.viewState)) return nothing;
    if (this.viewState.adminView !== AdminChildView.PLUGINS) return nothing;

    return html`
      <div class="main table">
        <gr-plugin-list
          class="table"
          .params=${this.viewState}
        ></gr-plugin-list>
      </div>
    `;
  }

  private renderRepoMain() {
    if (!isRepoView(this.viewState)) return nothing;
    const detail = this.viewState.detail ?? RepoDetailView.GENERAL;
    if (detail !== RepoDetailView.GENERAL) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo .repo=${this.viewState.repo}></gr-repo>
      </div>
    `;
  }

  private renderGroup() {
    if (!isGroupView(this.viewState)) return nothing;
    if (this.viewState.detail !== undefined) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-group
          .groupId=${this.viewState.groupId}
          @name-changed=${(e: CustomEvent<GroupNameChangedDetail>) => {
            this.updateGroupName(e);
          }}
        ></gr-group>
      </div>
    `;
  }

  private renderGroupMembers() {
    if (!isGroupView(this.viewState)) return nothing;
    if (this.viewState.detail !== GroupDetailView.MEMBERS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-group-members .groupId=${this.viewState.groupId}></gr-group-members>
      </div>
    `;
  }

  private renderGroupAuditLog() {
    if (!isGroupView(this.viewState)) return nothing;
    if (this.viewState.detail !== GroupDetailView.LOG) return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-group-audit-log
          class="table"
          .groupId=${this.viewState.groupId}
        ></gr-group-audit-log>
      </div>
    `;
  }

  private renderRepoDetailList() {
    if (!isRepoView(this.viewState)) return nothing;
    const detail = this.viewState.detail;
    if (detail !== RepoDetailView.BRANCHES && detail !== RepoDetailView.TAGS) {
      return nothing;
    }

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-detail-list
          class="table"
          .params=${this.viewState}
        ></gr-repo-detail-list>
      </div>
    `;
  }

  private renderRepoCommands() {
    if (!isRepoView(this.viewState)) return nothing;
    if (this.viewState.detail !== RepoDetailView.COMMANDS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-commands .repo=${this.viewState.repo}></gr-repo-commands>
      </div>
    `;
  }

  private renderRepoAccess() {
    if (!isRepoView(this.viewState)) return nothing;
    if (this.viewState.detail !== RepoDetailView.ACCESS) return nothing;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-access .repo=${this.viewState.repo}></gr-repo-access>
      </div>
    `;
  }

  private renderRepoDashboards() {
    if (!isRepoView(this.viewState)) return nothing;
    if (this.viewState.detail !== RepoDetailView.DASHBOARDS) return nothing;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-dashboards .repo=${this.viewState.repo}></gr-repo-dashboards>
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
          getPluginLoader().awaitPluginsLoaded(),
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
        () => this.jsAPI.getAdminMenuLinks(),
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

  private computeSelectValue() {
    if (!this.viewState?.view) return;
    return `${this.viewState.view}${getDetailView(this.viewState) ?? ''}`;
  }

  // private but used in test
  selectedIsCurrentPage(selected: AdminSubsectionLink) {
    if (!this.viewState) return false;

    return (
      selected.parent === (this.repoName ?? this.groupId) &&
      selected.view === this.viewState.view &&
      selected.detailType === getDetailView(this.viewState)
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
    GerritNav.navigateToRelativeUrl(selected.url);
  }

  needsReload(): boolean {
    if (!this.viewState) return false;

    let needsReload = false;
    const newRepoName =
      this.viewState.view === GerritView.REPO ? this.viewState.repo : undefined;
    if (newRepoName !== this.repoName) {
      this.repoName = newRepoName;
      // Reloads the admin menu.
      needsReload = true;
    }
    const newGroupId =
      this.viewState.view === GerritView.GROUP
        ? this.viewState.groupId
        : undefined;
    if (newGroupId !== this.groupId) {
      this.groupId = newGroupId;
      // Reloads the admin menu.
      needsReload = true;
    }
    if (
      this.breadcrumbParentName &&
      (this.viewState.view !== GerritView.GROUP || !this.viewState.groupId) &&
      (this.viewState.view !== GerritView.REPO || !this.viewState.repo)
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
    const viewState = this.viewState;
    if (!viewState) return '';
    // Group view state is structured differently than admin view state. Compute
    // selected differently for groups.
    // TODO(wyatta): Simplify this when all routes work like group view state.
    if (viewState.view === GerritView.GROUP && itemView === GerritView.GROUP) {
      if (!viewState.detail && !detailType) {
        return 'selected';
      }
      if (viewState.detail === detailType) {
        return 'selected';
      }
      return '';
    }

    if (viewState.view === GerritView.REPO && itemView === GerritView.REPO) {
      if (!viewState.detail && !detailType) {
        return 'selected';
      }
      if (viewState.detail === detailType) {
        return 'selected';
      }
      return '';
    }
    return viewState.view === GerritView.ADMIN &&
      itemView === viewState.adminView
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
