/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-icons/gr-icons';
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
import {
  GerritNav,
  GroupDetailView,
  RepoDetailView,
} from '../../core/gr-navigation/gr-navigation';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {
  AdminNavLinksOption,
  getAdminLinks,
  NavLink,
  SubsectionInterface,
} from '../../../utils/admin-nav-util';
import {
  AppElementAdminParams,
  AppElementGroupParams,
  AppElementRepoParams,
} from '../../gr-app-types';
import {
  AccountDetailInfo,
  GroupId,
  GroupName,
  RepoName,
} from '../../../types/common';
import {GroupNameChangedDetail} from '../gr-group/gr-group';
import {ValueChangeDetail} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {getAppContext} from '../../../services/app-context';
import {GerritView} from '../../../services/router/router-model';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

export interface AdminSubsectionLink {
  text: string;
  value: string;
  view: GerritView;
  url?: string;
  detailType?: GroupDetailView | RepoDetailView;
  parent?: GroupId | RepoName;
}

// The type is matched to the _showAdminView function from the gr-app-element
type AdminViewParams =
  | AppElementAdminParams
  | AppElementGroupParams
  | AppElementRepoParams;

function getAdminViewParamsDetail(
  params: AdminViewParams
): GroupDetailView | RepoDetailView | undefined {
  if (params.view !== GerritView.ADMIN) {
    return params.detail;
  }
  return undefined;
}

@customElement('gr-admin-view')
export class GrAdminView extends LitElement {
  private account?: AccountDetailInfo;

  @property({type: Object})
  params?: AdminViewParams;

  @property({type: String})
  path?: string;

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
  @state() groupOwner = false;

  // private but used in test
  @state() subsectionLinks?: AdminSubsectionLink[];

  // private but used in test
  @state() filteredLinks?: NavLink[];

  // private but used in test
  @state() isAdmin = false;

  @state() private showGroup?: boolean;

  @state() private showGroupAuditLog?: boolean;

  @state() private showGroupList?: boolean;

  @state() private showGroupMembers?: boolean;

  @state() private showRepoAccess?: boolean;

  @state() private showRepoCommands?: boolean;

  @state() private showRepoDashboards?: boolean;

  @state() private showRepoDetailList?: boolean;

  @state() private showRepoMain?: boolean;

  @state() private showRepoList?: boolean;

  @state() private showPluginList?: boolean;

  // private but used in the tests
  readonly jsAPI = getAppContext().jsApiService;

  private readonly restApiService = getAppContext().restApiService;

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
        iron-icon {
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
        <a class="title" href="${this.computeLinkURL(item)}" rel="noopener"
          >${item.name}</a
        >
      </li>
      ${item.children?.map(child => this.renderAdminNavChild(child))}
      ${this.renderAdminNavSubsection(item)}
    `;
  }

  private renderAdminNavChild(child: SubsectionInterface) {
    return html`
      <li class="${this.computeSelectedClass(child.view)}">
        <a href="${this.computeLinkURL(child)}" rel="noopener">${child.name}</a>
      </li>
    `;
  }

  private renderAdminNavSubsection(item: NavLink) {
    if (!item.subsection) return;

    return html`
      <!--If a section has a subsection, render that.-->
      <li class="${this.computeSelectedClass(item.subsection.view)}">
        ${this.renderAdminNavSubsectionUrl(item)}
      </li>
      <!--Loop through the links in the sub-section.-->
      ${item.subsection?.children?.map(child =>
        this.renderAdminNavSubsectionChild(child)
      )}
    `;
  }

  private renderAdminNavSubsectionUrl(item: NavLink) {
    if (!item.subsection!.url) return html`${item.subsection!.name}`;

    return html`
      <a
        class="title"
        href="${this.computeLinkURL(item.subsection)}"
        rel="noopener"
      >
        ${item.subsection!.name}</a
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
        <a href="${this.computeLinkURL(child)}">${child.name}</a>
      </li>
    `;
  }

  private renderSubsectionLinks() {
    if (!this.subsectionLinks?.length) return;

    return html`
      <section class="mainHeader">
        <span class="breadcrumb">
          <span class="breadcrumbText">${this.breadcrumbParentName}</span>
          <iron-icon icon="gr-icons:chevron-right"></iron-icon>
        </span>
        <gr-dropdown-list
          id="pageSelect"
          lowercase
          value=${this.computeSelectValue()}
          .items=${this.subsectionLinks}
          @value-change=${this.handleSubsectionChange}
        >
        </gr-dropdown-list>
      </section>
    `;
  }

  private renderRepoList() {
    if (!this.showRepoList) return;

    return html`
      <div class="main table">
        <gr-repo-list class="table" .params=${this.params}></gr-repo-list>
      </div>
    `;
  }

  private renderGroupList() {
    if (!this.showGroupList) return;

    return html`
      <div class="main table">
        <gr-admin-group-list class="table" .params=${this.params}>
        </gr-admin-group-list>
      </div>
    `;
  }

  private renderPluginList() {
    if (!this.showPluginList) return;

    return html`
      <div class="main table">
        <gr-plugin-list class="table" .params=${this.params}></gr-plugin-list>
      </div>
    `;
  }

  private renderRepoMain() {
    if (!this.showRepoMain) return;

    return html`
      <div class="main breadcrumbs">
        <gr-repo .repo=${(this.params as AppElementRepoParams).repo}></gr-repo>
      </div>
    `;
  }

  private renderGroup() {
    if (!this.showGroup) return;

    return html`
      <div class="main breadcrumbs">
        <gr-group
          .groupId=${(this.params as AppElementGroupParams).groupId}
          @name-changed=${(e: CustomEvent<GroupNameChangedDetail>) => {
            this.updateGroupName(e);
          }}
        ></gr-group>
      </div>
    `;
  }

  private renderGroupMembers() {
    if (!this.showGroupMembers) return;

    return html`
      <div class="main breadcrumbs">
        <gr-group-members
          .groupId=${(this.params as AppElementGroupParams).groupId}
        ></gr-group-members>
      </div>
    `;
  }

  private renderGroupAuditLog() {
    if (!this.showGroupAuditLog) return;

    return html`
      <div class="main table breadcrumbs">
        <gr-group-audit-log
          class="table"
          .groupId=${(this.params as AppElementGroupParams).groupId}
        ></gr-group-audit-log>
      </div>
    `;
  }

  private renderRepoDetailList() {
    if (!this.showRepoDetailList) return;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-detail-list
          class="table"
          .params=${this.params}
        ></gr-repo-detail-list>
      </div>
    `;
  }

  private renderRepoCommands() {
    if (!this.showRepoCommands) return;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-commands
          .repo=${(this.params as AppElementRepoParams).repo}
        ></gr-repo-commands>
      </div>
    `;
  }

  private renderRepoAccess() {
    if (!this.showRepoAccess) return;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-access
          .path=${this.path}
          .repo=${(this.params as AppElementRepoParams).repo}
        ></gr-repo-access>
      </div>
    `;
  }

  private renderRepoDashboards() {
    if (!this.showRepoDashboards) return;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-dashboards
          .repo=${(this.params as AppElementRepoParams).repo}
        ></gr-repo-dashboards>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }

    if (changedProperties.has('groupId')) {
      this.computeGroupName();
    }
  }

  async reload() {
    const promises: [Promise<AccountDetailInfo | undefined>, Promise<void>] = [
      this.restApiService.getAccount(),
      getPluginLoader().awaitPluginsLoaded(),
    ];
    const result = await Promise.all(promises);
    this.account = result[0];
    let options: AdminNavLinksOption | undefined = undefined;
    if (this.repoName) {
      options = {repoName: this.repoName};
    } else if (this.groupId) {
      options = {
        groupId: this.groupId,
        groupName: this.groupName,
        groupIsInternal: this.groupIsInternal,
        isAdmin: this.isAdmin,
        groupOwner: this.groupOwner,
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
  }

  private computeSelectValue() {
    if (!this.params?.view) return;
    return `${this.params.view}${getAdminViewParamsDetail(this.params) ?? ''}`;
  }

  // private but used in test
  selectedIsCurrentPage(selected: AdminSubsectionLink) {
    if (!this.params) return false;

    return (
      selected.parent === (this.repoName ?? this.groupId) &&
      selected.view === this.params.view &&
      selected.detailType === getAdminViewParamsDetail(this.params)
    );
  }

  // private but used in test
  handleSubsectionChange(e: CustomEvent<ValueChangeDetail>) {
    if (!this.subsectionLinks) return;

    // The GrDropdownList items are subsectionLinks, so find(...) always return
    // an item subsectionLinks and never returns undefined
    const selected = this.subsectionLinks.find(
      section => section.value === e.detail.value
    )!;

    // This is when it gets set initially.
    if (this.selectedIsCurrentPage(selected)) return;
    if (selected.url === undefined) return;
    GerritNav.navigateToRelativeUrl(selected.url);
  }

  private paramsChanged() {
    if (!this.params) return;

    this.showGroup =
      this.params.view === GerritView.GROUP && !this.params.detail;
    this.showGroupAuditLog =
      this.params.view === GerritView.GROUP &&
      this.params.detail === GroupDetailView.LOG;
    this.showGroupMembers =
      this.params.view === GerritView.GROUP &&
      this.params.detail === GroupDetailView.MEMBERS;

    this.showGroupList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-admin-group-list';

    this.showRepoAccess =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.ACCESS;
    this.showRepoCommands =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.COMMANDS;
    this.showRepoDetailList =
      this.params.view === GerritView.REPO &&
      (this.params.detail === RepoDetailView.BRANCHES ||
        this.params.detail === RepoDetailView.TAGS);
    this.showRepoDashboards =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.DASHBOARDS;
    this.showRepoMain =
      this.params.view === GerritView.REPO &&
      (!this.params.detail || this.params.detail === RepoDetailView.GENERAL);
    this.showRepoList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-repo-list';

    this.showPluginList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-plugin-list';

    let needsReload = false;
    const newRepoName =
      this.params.view === GerritView.REPO ? this.params.repo : undefined;
    if (newRepoName !== this.repoName) {
      this.repoName = newRepoName;
      // Reloads the admin menu.
      needsReload = true;
    }
    const newGroupId =
      this.params.view === GerritView.GROUP ? this.params.groupId : undefined;
    if (newGroupId !== this.groupId) {
      this.groupId = newGroupId;
      // Reloads the admin menu.
      needsReload = true;
    }
    if (
      this.breadcrumbParentName &&
      (this.params.view !== GerritView.GROUP || !this.params.groupId) &&
      (this.params.view !== GerritView.REPO || !this.params.repo)
    ) {
      needsReload = true;
    }
    if (!needsReload) {
      return;
    }
    this.reload();
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
    itemView?: GerritView,
    detailType?: GroupDetailView | RepoDetailView
  ) {
    const params = this.params;
    if (!params) return '';
    // Group params are structured differently from admin params. Compute
    // selected differently for groups.
    // TODO(wyatta): Simplify this when all routes work like group params.
    if (params.view === GerritView.GROUP && itemView === GerritView.GROUP) {
      if (!params.detail && !detailType) {
        return 'selected';
      }
      if (params.detail === detailType) {
        return 'selected';
      }
      return '';
    }

    if (params.view === GerritView.REPO && itemView === GerritView.REPO) {
      if (!params.detail && !detailType) {
        return 'selected';
      }
      if (params.detail === detailType) {
        return 'selected';
      }
      return '';
    }
    // TODO(TS): The following condition seems always false, because params
    // never has detailType property. Remove it.
    if (
      (params as unknown as AdminSubsectionLink).detailType &&
      (params as unknown as AdminSubsectionLink).detailType !== detailType
    ) {
      return '';
    }
    return params.view === GerritView.ADMIN && itemView === params.adminView
      ? 'selected'
      : '';
  }

  // private but used in test
  async computeGroupName() {
    if (!this.groupId) return;

    const promises: Array<Promise<void>> = [];
    const group = await this.restApiService.getGroupConfig(this.groupId);
    if (!group || !group.name) {
      return;
    }

    this.groupName = group.name;
    this.groupIsInternal = !!group.id.match(INTERNAL_GROUP_REGEX);
    this.reload();

    promises.push(
      this.restApiService.getIsAdmin().then(isAdmin => {
        this.isAdmin = !!isAdmin;
      })
    );

    promises.push(
      this.restApiService.getIsGroupOwner(group.name).then(isOwner => {
        this.groupOwner = isOwner;
      })
    );

    await Promise.all(promises);
    this.reload();
  }

  private updateGroupName(e: CustomEvent<GroupNameChangedDetail>) {
    this.groupName = e.detail.name;
    this.reload();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-view': GrAdminView;
  }
}
