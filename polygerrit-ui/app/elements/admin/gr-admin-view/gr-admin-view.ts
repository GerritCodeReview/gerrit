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
import {customElement, property /* , state*/} from 'lit/decorators';

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

  @property({type: String})
  _breadcrumbParentName?: string;

  @property({type: String})
  _repoName?: RepoName;

  @property({type: String})
  _groupId?: GroupId;

  @property({type: Boolean})
  _groupIsInternal?: boolean;

  @property({type: String})
  _groupName?: GroupName;

  @property({type: Boolean})
  _groupOwner = false;

  @property({type: Array})
  _subsectionLinks?: AdminSubsectionLink[];

  @property({type: Array})
  _filteredLinks?: NavLink[];

  @property({type: Boolean})
  _showDownload = false;

  @property({type: Boolean})
  _isAdmin = false;

  @property({type: Boolean})
  _showGroup?: boolean;

  @property({type: Boolean})
  _showGroupAuditLog?: boolean;

  @property({type: Boolean})
  _showGroupList?: boolean;

  @property({type: Boolean})
  _showGroupMembers?: boolean;

  @property({type: Boolean})
  _showRepoAccess?: boolean;

  @property({type: Boolean})
  _showRepoCommands?: boolean;

  @property({type: Boolean})
  _showRepoDashboards?: boolean;

  @property({type: Boolean})
  _showRepoDetailList?: boolean;

  @property({type: Boolean})
  _showRepoMain?: boolean;

  @property({type: Boolean})
  _showRepoList?: boolean;

  @property({type: Boolean})
  _showPluginList?: boolean;

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
          ${this._filteredLinks?.map(item => this.renderAdminNav(item))}
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
      <li class="sectionTitle ${this._computeSelectedClass(item.view)}">
        <a class="title" href="${this._computeLinkURL(item)}" rel="noopener"
          >${item.name}</a
        >
      </li>
      ${item.children?.map(child => this.renderAdminNavChild(child))}
      ${this.renderAdminNavSubsection(item)}
    `;
  }

  private renderAdminNavChild(child: SubsectionInterface) {
    return html`
      <li class="${this._computeSelectedClass(child.view)}">
        <a href="${this._computeLinkURL(child)}" rel="noopener"
          >${child.name}</a
        >
      </li>
    `;
  }

  private renderAdminNavSubsection(item: NavLink) {
    if (!item.subsection) return;

    return html`
      <!--If a section has a subsection, render that.-->
      <li class="${this._computeSelectedClass(item.subsection.view)}">
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
        href="${this._computeLinkURL(item.subsection)}"
        rel="noopener"
      >
        ${item.subsection!.name}</a
      >
    `;
  }

  private renderAdminNavSubsectionChild(child: SubsectionInterface) {
    return html`
      <li
        class="subsectionItem ${this._computeSelectedClass(
          child.view,
          child.detailType
        )}"
      >
        <a href="${this._computeLinkURL(child)}">${child.name}</a>
      </li>
    `;
  }

  private renderSubsectionLinks() {
    if (!this._subsectionLinks?.length) return;

    return html`
      <section class="mainHeader">
        <span class="breadcrumb">
          <span class="breadcrumbText">${this._breadcrumbParentName}</span>
          <iron-icon icon="gr-icons:chevron-right"></iron-icon>
        </span>
        <gr-dropdown-list
          id="pageSelect"
          lowercase
          value=${this._computeSelectValue()}
          .items=${this._subsectionLinks}
          @value-change=${this._handleSubsectionChange}
        >
        </gr-dropdown-list>
      </section>
    `;
  }

  private renderRepoList() {
    if (!this._showRepoList) return;

    return html`
      <div class="main table">
        <gr-repo-list class="table" .params=${this.params}></gr-repo-list>
      </div>
    `;
  }

  private renderGroupList() {
    if (!this._showGroupList) return;

    return html`
      <div class="main table">
        <gr-admin-group-list class="table" .params=${this.params}>
        </gr-admin-group-list>
      </div>
    `;
  }

  private renderPluginList() {
    if (!this._showPluginList) return;

    return html`
      <div class="main table">
        <gr-plugin-list class="table" .params=${this.params}></gr-plugin-list>
      </div>
    `;
  }

  private renderRepoMain() {
    if (!this._showRepoMain) return;

    return html`
      <div class="main breadcrumbs">
        <gr-repo .repo=${(this.params as AppElementRepoParams).repo}></gr-repo>
      </div>
    `;
  }

  private renderGroup() {
    if (!this._showGroup) return;

    return html`
      <div class="main breadcrumbs">
        <gr-group
          .groupId=${(this.params as AppElementGroupParams).groupId}
          @name-changed=${(e: CustomEvent<GroupNameChangedDetail>) => {
            this._updateGroupName(e);
          }}
        ></gr-group>
      </div>
    `;
  }

  private renderGroupMembers() {
    if (!this._showGroupMembers) return;

    return html`
      <div class="main breadcrumbs">
        <gr-group-members
          .groupId=${(this.params as AppElementGroupParams).groupId}
        ></gr-group-members>
      </div>
    `;
  }

  private renderGroupAuditLog() {
    if (!this._showGroupAuditLog) return;

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
    if (!this._showRepoDetailList) return;

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
    if (!this._showRepoCommands) return;

    return html`
      <div class="main breadcrumbs">
        <gr-repo-commands
          .repo=${(this.params as AppElementRepoParams).repo}
        ></gr-repo-commands>
      </div>
    `;
  }

  private renderRepoAccess() {
    if (!this._showRepoAccess) return;

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
    if (!this._showRepoDashboards) return;

    return html`
      <div class="main table breadcrumbs">
        <gr-repo-dashboards
          .repo=${(this.params as AppElementRepoParams).repo}
        ></gr-repo-dashboards>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('_groupId')) {
      this._computeGroupName();
    }

    if (changedProperties.has('params')) {
      this._paramsChanged();
    }
  }

  reload() {
    const promises: [Promise<AccountDetailInfo | undefined>, Promise<void>] = [
      this.restApiService.getAccount(),
      getPluginLoader().awaitPluginsLoaded(),
    ];
    return Promise.all(promises).then(result => {
      this.account = result[0];
      let options: AdminNavLinksOption | undefined = undefined;
      if (this._repoName) {
        options = {repoName: this._repoName};
      } else if (this._groupId) {
        options = {
          groupId: this._groupId,
          groupName: this._groupName,
          groupIsInternal: this._groupIsInternal,
          isAdmin: this._isAdmin,
          groupOwner: this._groupOwner,
        };
      }

      return getAdminLinks(
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
      ).then(res => {
        this._filteredLinks = res.links;
        this._breadcrumbParentName = res.expandedSection
          ? res.expandedSection.name
          : '';

        if (!res.expandedSection) {
          this._subsectionLinks = [];
          return;
        }
        this._subsectionLinks = [res.expandedSection]
          .concat(res.expandedSection.children ?? [])
          .map(section => {
            return {
              text: !section.detailType ? 'Home' : section.name,
              value: section.view + (section.detailType ?? ''),
              view: section.view,
              url: section.url,
              detailType: section.detailType,
              parent: this._groupId ?? this._repoName,
            };
          });
      });
    });
  }

  _computeSelectValue() {
    if (!this.params?.view) return;
    return `${this.params.view}${getAdminViewParamsDetail(this.params) ?? ''}`;
  }

  _selectedIsCurrentPage(selected: AdminSubsectionLink) {
    if (!this.params) return false;

    return (
      selected.parent === (this._repoName ?? this._groupId) &&
      selected.view === this.params.view &&
      selected.detailType === getAdminViewParamsDetail(this.params)
    );
  }

  _handleSubsectionChange(e: CustomEvent<ValueChangeDetail>) {
    if (!this._subsectionLinks) return;

    // The GrDropdownList items are _subsectionLinks, so find(...) always return
    // an item _subsectionLinks and never returns undefined
    const selected = this._subsectionLinks.find(
      section => section.value === e.detail.value
    )!;

    // This is when it gets set initially.
    if (this._selectedIsCurrentPage(selected)) return;
    if (selected.url === undefined) return;
    GerritNav.navigateToRelativeUrl(selected.url);
  }

  _paramsChanged() {
    if (!this.params) return;

    this._showGroup =
      this.params.view === GerritView.GROUP && !this.params.detail;
    this._showGroupAuditLog =
      this.params.view === GerritView.GROUP &&
      this.params.detail === GroupDetailView.LOG;
    this._showGroupMembers =
      this.params.view === GerritView.GROUP &&
      this.params.detail === GroupDetailView.MEMBERS;

    this._showGroupList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-admin-group-list';

    this._showRepoAccess =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.ACCESS;
    this._showRepoCommands =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.COMMANDS;
    this._showRepoDetailList =
      this.params.view === GerritView.REPO &&
      (this.params.detail === RepoDetailView.BRANCHES ||
        this.params.detail === RepoDetailView.TAGS);
    this._showRepoDashboards =
      this.params.view === GerritView.REPO &&
      this.params.detail === RepoDetailView.DASHBOARDS;
    this._showRepoMain =
      this.params.view === GerritView.REPO &&
      (!this.params.detail || this.params.detail === RepoDetailView.GENERAL);
    this._showRepoList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-repo-list';

    this._showPluginList =
      this.params.view === GerritView.ADMIN &&
      this.params.adminView === 'gr-plugin-list';

    let needsReload = false;
    const newRepoName =
      this.params.view === GerritView.REPO ? this.params.repo : undefined;
    if (newRepoName !== this._repoName) {
      this._repoName = newRepoName;
      // Reloads the admin menu.
      needsReload = true;
    }
    const newGroupId =
      this.params.view === GerritView.GROUP ? this.params.groupId : undefined;
    if (newGroupId !== this._groupId) {
      this._groupId = newGroupId;
      // Reloads the admin menu.
      needsReload = true;
    }
    if (
      this._breadcrumbParentName &&
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

  // TODO (beckysiegel): Update these functions after router abstraction is
  // updated. They are currently copied from gr-dropdown (and should be
  // updated there as well once complete).
  _computeURLHelper(host: string, path: string) {
    return '//' + host + getBaseUrl() + path;
  }

  _computeRelativeURL(path: string) {
    const host = window.location.host;
    return this._computeURLHelper(host, path);
  }

  _computeLinkURL(link?: NavLink | SubsectionInterface) {
    if (!link || typeof link.url === 'undefined') return '';

    if ((link as NavLink).target || !(link as NavLink).noBaseUrl) {
      return link.url;
    }
    return this._computeRelativeURL(link.url);
  }

  _computeSelectedClass(
    itemView?: GerritView,
    detailType?: GroupDetailView | RepoDetailView
  ) {
    if (!this.params) return '';
    // Group params are structured differently from admin params. Compute
    // selected differently for groups.
    // TODO(wyatta): Simplify this when all routes work like group params.
    if (
      this.params.view === GerritView.GROUP &&
      itemView === GerritView.GROUP
    ) {
      if (!this.params.detail && !detailType) {
        return 'selected';
      }
      if (this.params.detail === detailType) {
        return 'selected';
      }
      return '';
    }

    if (this.params.view === GerritView.REPO && itemView === GerritView.REPO) {
      if (!this.params.detail && !detailType) {
        return 'selected';
      }
      if (this.params.detail === detailType) {
        return 'selected';
      }
      return '';
    }
    // TODO(TS): The following condition seems always false, because params
    // never has detailType property. Remove it.
    if (
      (this.params as unknown as AdminSubsectionLink).detailType &&
      (this.params as unknown as AdminSubsectionLink).detailType !== detailType
    ) {
      return '';
    }
    return this.params.view === GerritView.ADMIN &&
      itemView === this.params.adminView
      ? 'selected'
      : '';
  }

  _computeGroupName() {
    if (!this._groupId) return;

    const promises: Array<Promise<void>> = [];
    this.restApiService.getGroupConfig(this._groupId).then(group => {
      if (!group || !group.name) {
        return;
      }

      this._groupName = group.name;
      this._groupIsInternal = !!group.id.match(INTERNAL_GROUP_REGEX);
      this.reload();

      promises.push(
        this.restApiService.getIsAdmin().then(isAdmin => {
          this._isAdmin = !!isAdmin;
        })
      );

      promises.push(
        this.restApiService.getIsGroupOwner(group.name).then(isOwner => {
          this._groupOwner = isOwner;
        })
      );

      return Promise.all(promises).then(() => {
        this.reload();
      });
    });
  }

  _updateGroupName(e: CustomEvent<GroupNameChangedDetail>) {
    this._groupName = e.detail.name;
    this.reload();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-admin-view': GrAdminView;
  }
}
