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
import '../../../styles/gr-menu-page-styles';
import '../../../styles/gr-page-nav-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import '../../shared/gr-page-nav/gr-page-nav';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-admin-view_html';
import {getBaseUrl} from '../../../utils/url-util';
import {
  GerritNav,
  GerritView,
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
import {customElement, observe, property} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
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
import {GrJsApiInterface} from '../../shared/gr-js-api-interface/gr-js-api-interface-element';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

export interface GrAdminView {
  $: {
    restAPI: RestApiService & Element;
    jsAPI: GrJsApiInterface;
  };
}

interface AdminSubsectionLink {
  text: string;
  value: string;
  view: GerritView;
  url: string;
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
export class GrAdminView extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  private _account?: AccountDetailInfo;

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

  @property({type: String, observer: '_computeGroupName'})
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

  /** @override */
  attached() {
    super.attached();
    this.reload();
  }

  reload() {
    const promises: [Promise<AccountDetailInfo | undefined>, Promise<void>] = [
      this.$.restAPI.getAccount(),
      getPluginLoader().awaitPluginsLoaded(),
    ];
    return Promise.all(promises).then(result => {
      this._account = result[0];
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
        this._account,
        () =>
          this.$.restAPI.getAccountCapabilities().then(capabilities => {
            if (!capabilities) {
              throw new Error('getAccountCapabilities returns undefined');
            }
            return capabilities;
          }),
        () => this.$.jsAPI.getAdminMenuLinks(),
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

  _computeSelectValue(params: AdminViewParams) {
    if (!params || !params.view) return;
    return `${params.view}${getAdminViewParamsDetail(params) ?? ''}`;
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
    GerritNav.navigateToRelativeUrl(selected.url);
  }

  @observe('params')
  _paramsChanged(params: AdminViewParams) {
    this.set('_showGroup', params.view === GerritView.GROUP && !params.detail);
    this.set(
      '_showGroupAuditLog',
      params.view === GerritView.GROUP && params.detail === GroupDetailView.LOG
    );
    this.set(
      '_showGroupMembers',
      params.view === GerritView.GROUP &&
        params.detail === GroupDetailView.MEMBERS
    );

    this.set(
      '_showGroupList',
      params.view === GerritView.ADMIN &&
        params.adminView === 'gr-admin-group-list'
    );

    this.set(
      '_showRepoAccess',
      params.view === GerritView.REPO && params.detail === RepoDetailView.ACCESS
    );
    this.set(
      '_showRepoCommands',
      params.view === GerritView.REPO &&
        params.detail === RepoDetailView.COMMANDS
    );
    this.set(
      '_showRepoDetailList',
      params.view === GerritView.REPO &&
        (params.detail === RepoDetailView.BRANCHES ||
          params.detail === RepoDetailView.TAGS)
    );
    this.set(
      '_showRepoDashboards',
      params.view === GerritView.REPO &&
        params.detail === RepoDetailView.DASHBOARDS
    );
    this.set(
      '_showRepoMain',
      params.view === GerritView.REPO && !params.detail
    );

    this.set(
      '_showRepoList',
      params.view === GerritView.ADMIN && params.adminView === 'gr-repo-list'
    );

    this.set(
      '_showPluginList',
      params.view === GerritView.ADMIN && params.adminView === 'gr-plugin-list'
    );

    let needsReload = false;
    const newRepoName =
      params.view === GerritView.REPO ? params.repo : undefined;
    if (newRepoName !== this._repoName) {
      this._repoName = newRepoName;
      // Reloads the admin menu.
      needsReload = true;
    }
    const newGroupId =
      params.view === GerritView.GROUP ? params.groupId : undefined;
    if (newGroupId !== this._groupId) {
      this._groupId = newGroupId;
      // Reloads the admin menu.
      needsReload = true;
    }
    if (
      this._breadcrumbParentName &&
      (params.view !== GerritView.GROUP || !params.groupId) &&
      (params.view !== GerritView.REPO || !params.repo)
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

  _computeLinkURL(link: NavLink | SubsectionInterface) {
    if (!link || typeof link.url === 'undefined') return '';

    if ((link as NavLink).target || !(link as NavLink).noBaseUrl) {
      return link.url;
    }
    return this._computeRelativeURL(link.url);
  }

  _computeSelectedClass(
    itemView?: GerritView,
    params?: AdminViewParams,
    detailType?: GroupDetailView | RepoDetailView
  ) {
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
    // TODO(TS): The following condtion seems always false, because params
    // never has detailType property. Remove it.
    if (
      ((params as unknown) as AdminSubsectionLink).detailType &&
      ((params as unknown) as AdminSubsectionLink).detailType !== detailType
    ) {
      return '';
    }
    return params.view === GerritView.ADMIN && itemView === params.adminView
      ? 'selected'
      : '';
  }

  _computeGroupName(groupId?: GroupId) {
    if (!groupId) return;

    const promises: Array<Promise<void>> = [];
    this.$.restAPI.getGroupConfig(groupId).then(group => {
      if (!group || !group.name) {
        return;
      }

      this._groupName = group.name;
      this._groupIsInternal = !!group.id.match(INTERNAL_GROUP_REGEX);
      this.reload();

      promises.push(
        this.$.restAPI.getIsAdmin().then(isAdmin => {
          this._isAdmin = !!isAdmin;
        })
      );

      promises.push(
        this.$.restAPI.getIsGroupOwner(group.name).then(isOwner => {
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
