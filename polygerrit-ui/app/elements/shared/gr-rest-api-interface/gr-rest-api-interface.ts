/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
/* NB: Order is important, because of namespaced classes. */

import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {GrEtagDecorator} from './gr-etag-decorator';
import {
  FetchJSONRequest,
  FetchPromisesCache,
  GrRestApiHelper,
  SendJSONRequest,
  SiteBasedCache,
} from './gr-rest-apis/gr-rest-api-helper';
import {GrReviewerUpdatesParser} from './gr-reviewer-updates-parser';
import {parseDate} from '../../../utils/date-util';
import {getBaseUrl} from '../../../utils/url-util';
import {appContext} from '../../../services/app-context';
import {
  getParentIndex,
  isMergeParent,
  patchNumEquals,
  SPECIAL_PATCH_SET_NUM,
} from '../../../utils/patch-set-util';
import {
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../../utils/change-util';
import {hasOwnProperty} from '../../../utils/common-util';
import {customElement} from '@polymer/decorators';
import {property} from '@polymer/decorators/lib/decorators';
import {AuthService} from '../../../services/gr-auth/gr-auth';
import {
  BranchName,
  ChangeInfo,
  ConfigInfo,
  ConfigInput,
  DashboardInfo,
  GitRef,
  ParsedJSON,
  ProjectAccessInfoMap,
  ProjectInfo,
  ProjectInput,
  RepositoryName,
  ServerInfo,
  BranchInfo,
  ProjectAccessInput,
  ProjectName,
  NameToProjectInfoMap,
  CommitId,
  CommitInfo,
  Base64File,
  ChangeId,
  PatchSetNum,
  DashboardId,
  GroupId,
  AccountInfo,
  GroupInfo,
  GroupOptionsInput,
  AccountId,
  GroupInput,
  EncodedGroupId,
  ProjectWatchInfo,
  GpgKeyId,
  DiffPreferencesInfo,
  ChangeMessageId,
} from '../../../types/common';
import {
  CancelConditionCallback,
  ErrorCallback,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {HttpMethod} from '../../../constants/constants';

const DiffViewMode = {
  SIDE_BY_SIDE: 'SIDE_BY_SIDE',
  UNIFIED: 'UNIFIED_DIFF',
};
const JSON_PREFIX = ")]}'";
const MAX_PROJECT_RESULTS = 25;
// This value is somewhat arbitrary and not based on research or calculations.
const MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX = 850;

const Requests = {
  SEND_DIFF_DRAFT: 'sendDiffDraft',
};

const CREATE_DRAFT_UNEXPECTED_STATUS_MESSAGE =
  'Saving draft resulted in HTTP 200 (OK) but expected HTTP 201 (Created)';
const HEADER_REPORTING_BLOCK_REGEX = /^set-cookie$/i;

const ANONYMIZED_CHANGE_BASE_URL = '/changes/*~*';
const ANONYMIZED_REVISION_BASE_URL =
  ANONYMIZED_CHANGE_BASE_URL + '/revisions/*';

let siteBasedCache = new SiteBasedCache(); // Shared across instances.
let fetchPromisesCache = new FetchPromisesCache(); // Shared across instances.
let pendingRequest = {}; // Shared across instances.
let grEtagDecorator = new GrEtagDecorator(); // Shared across instances.
let projectLookup = {}; // Shared across instances.

type ChangeNum = number; // !!!TODO: define correct types
type RestApiInputOrOutput = ParsedJSON;

export function _testOnlyResetGrRestApiSharedObjects() {
  for (const key in fetchPromisesCache._data) {
    if (hasOwnProperty(fetchPromisesCache._data, key)) {
      // reject already fulfilled promise does nothing
      fetchPromisesCache._data[key].reject();
    }
  }

  for (const key in pendingRequest) {
    if (!hasOwnProperty(pendingRequest, key)) {
      continue;
    }
    for (const req of pendingRequest[key]) {
      // reject already fulfilled promise does nothing
      req.reject();
    }
  }

  siteBasedCache = new SiteBasedCache();
  fetchPromisesCache = new FetchPromisesCache();
  pendingRequest = {};
  grEtagDecorator = new GrEtagDecorator();
  projectLookup = {};
  appContext.authService.clearCache();
}

/**
 * @extends PolymerElement
 */
@customElement('gr-rest-api-interface')
class GrRestApiInterface extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  readonly JSON_PREFIX = JSON_PREFIX;
  /**
   * Fired when an server error occurs.
   *
   * @event server-error
   */

  /**
   * Fired when a network error occurs.
   *
   * @event network-error
   */

  /**
   * Fired after an RPC completes.
   *
   * @event rpc-log
   */

  @property({type: Object})
  readonly _cache = siteBasedCache; // Shared across instances.

  @property({type: Object})
  readonly _sharedFetchPromises = fetchPromisesCache; // Shared across instances.

  @property({type: Object})
  readonly _pendingRequests = pendingRequest; // Shared across instances.

  @property({type: Object})
  readonly _etags = grEtagDecorator; // Shared across instances.

  @property({type: Object})
  readonly _projectLookup = projectLookup; // Shared across instances.

  private authService: AuthService;

  private _restApiHelper: GrRestApiHelper;

  /** @override */
  created() {
    super.created();
    this.authService = appContext.authService;
    this._initRestApiHelper();
  }

  private _initRestApiHelper() {
    if (this._restApiHelper) {
      return;
    }
    if (this._cache && this.authService && this._sharedFetchPromises) {
      this._restApiHelper = new GrRestApiHelper(
        this._cache,
        this.authService,
        this._sharedFetchPromises,
        this
      );
    }
  }

  private _fetchSharedCacheURL(
    req: FetchJSONRequest
  ): Promise<ParsedJSON | null | undefined> {
    // Cache is shared across instances
    return this._restApiHelper.fetchCacheURL(req);
  }

  getResponseObject(response: Response): Promise<ParsedJSON | null> {
    return this._restApiHelper.getResponseObject(response);
  }

  getConfig(noCache?: boolean): Promise<ServerInfo | null | undefined> {
    if (!noCache) {
      return this._fetchSharedCacheURL({
        url: '/config/server/info',
        reportUrlAsIs: true,
      }) as Promise<ServerInfo | null | undefined>;
    }

    return this._restApiHelper.fetchJSON({
      url: '/config/server/info',
      reportUrlAsIs: true,
    }) as Promise<ServerInfo | null | undefined>;
  }

  getRepo(
    repo: RepositoryName,
    errFn?: ErrorCallback
  ): Promise<ProjectInfo | null | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/projects/' + encodeURIComponent(repo),
      errFn,
      anonymizedUrl: '/projects/*',
    }) as Promise<ProjectInfo | null | undefined>;
  }

  getProjectConfig(
    repo: RepositoryName,
    errFn?: ErrorCallback
  ): Promise<ConfigInfo | null | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/projects/' + encodeURIComponent(repo) + '/config',
      errFn,
      anonymizedUrl: '/projects/*/config',
    }) as Promise<ConfigInfo | null | undefined>;
  }

  getRepoAccess(
    repo: RepositoryName
  ): Promise<ProjectAccessInfoMap | null | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/access/?project=' + encodeURIComponent(repo),
      anonymizedUrl: '/access/?project=*',
    }) as Promise<ProjectAccessInfoMap | null | undefined>;
  }

  getRepoDashboards(
    repo: RepositoryName,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo[] | null | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: `/projects/${encodeURIComponent(repo)}/dashboards?inherited`,
      errFn,
      anonymizedUrl: '/projects/*/dashboards?inherited',
    }) as Promise<DashboardInfo[] | null | undefined>;
  }

  saveRepoConfig(
    repo: RepositoryName,
    config: ConfigInput,
    errFn?: ErrorCallback
  ): Promise<Response | void> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const url = `/projects/${encodeURIComponent(repo)}/config`;
    this._cache.delete(url);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url,
      body: config,
      errFn,
      anonymizedUrl: '/projects/*/config',
    });
  }

  runRepoGC(repo: RepositoryName, errFn?: ErrorCallback): Promise<> {
    if (!repo) {
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: `/projects/${encodeName}/gc`,
      body: '',
      errFn,
      anonymizedUrl: '/projects/*/gc',
    });
  }

  createRepo(
    config: ProjectInput,
    errFn?: ErrorCallback
  ): Promise<Response | void> | '' {
    if (!config.name) {
      // TODO(TS): Fix return value
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}`,
      body: config,
      errFn,
      anonymizedUrl: '/projects/*',
    });
  }

  createGroup(
    config: GroupInput,
    errFn?: ErrorCallback
  ): Promise<Response | void> | '' {
    if (!config.name) {
      // TODO(TS): Fix return value
      return '';
    }
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}`,
      body: config,
      errFn,
      anonymizedUrl: '/groups/*',
    });
  }

  getGroupConfig(
    group: GroupId,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | null | undefined> {
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeURIComponent(group)}/detail`,
      errFn,
      anonymizedUrl: '/groups/*/detail',
    }) as Promise<GroupInfo | null | undefined>;
  }

  deleteRepoBranches(
    repo: RepositoryName,
    ref: GitRef,
    errFn?: ErrorCallback
  ): Promise<void | Response> | '' {
    if (!repo || !ref) {
      // TODO(TS): fix return value
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/projects/${encodeName}/branches/${encodeRef}`,
      body: '',
      errFn,
      anonymizedUrl: '/projects/*/branches/*',
    });
  }

  deleteRepoTags(
    repo: RepositoryName,
    ref: GitRef,
    errFn?: ErrorCallback
  ): Promise<void | Response> | '' {
    if (!repo || !ref) {
      // TODO(TS): fix return type
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/projects/${encodeName}/tags/${encodeRef}`,
      body: '',
      errFn,
      anonymizedUrl: '/projects/*/tags/*',
    });
  }

  createRepoBranch(
    name: RepositoryName,
    branch: BranchName,
    revision: string,
    errFn?: ErrorCallback
  ): Promise<Response | void> | '' {
    if (!name || !branch || !revision) {
      // TODO(TS) fix return type
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(name);
    const encodeBranch = encodeURIComponent(branch);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}/branches/${encodeBranch}`,
      body: revision,
      errFn,
      anonymizedUrl: '/projects/*/branches/*',
    });
  }

  createRepoTag(
    name: RepositoryName,
    tag: string,
    revision: string,
    errFn?: ErrorCallback
  ): Promise<void | Response> | '' {
    if (!name || !tag || !revision) {
      // TODO(TS): Fix returen type
      return '';
    }
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(name);
    const encodeTag = encodeURIComponent(tag);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}/tags/${encodeTag}`,
      body: revision,
      errFn,
      anonymizedUrl: '/projects/*/tags/*',
    });
  }

  getIsGroupOwner(groupName: GroupId) {
    const encodeName = encodeURIComponent(groupName);
    const req = {
      url: `/groups/?owned&g=${encodeName}`,
      anonymizedUrl: '/groups/owned&g=*',
    };
    return this._fetchSharedCacheURL(req).then(configs =>
      hasOwnProperty(configs, groupName)
    );
  }

  getGroupMembers(
    groupName: GroupId,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | null | undefined> {
    const encodeName = encodeURIComponent(groupName);
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeName}/members/`,
      errFn,
      anonymizedUrl: '/groups/*/members',
    }) as Promise<AccountInfo[] | null | undefined>;
  }

  getIncludedGroup(
    groupName: GroupId
  ): Promise<GroupInfo[] | null | undefined> {
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeURIComponent(groupName)}/groups/`,
      anonymizedUrl: '/groups/*/groups',
    }) as Promise<GroupInfo[] | null | undefined>;
  }

  saveGroupName(groupId: GroupId, name: string) {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/name`,
      body: {name},
      anonymizedUrl: '/groups/*/name',
    });
  }

  saveGroupOwner(groupId: GroupId, ownerId: string) {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/owner`,
      body: {owner: ownerId},
      anonymizedUrl: '/groups/*/owner',
    });
  }

  saveGroupDescription(groupId: GroupId, description: string) {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/description`,
      body: {description},
      anonymizedUrl: '/groups/*/description',
    });
  }

  saveGroupOptions(groupId: GroupId, options: GroupOptionsInput) {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/options`,
      body: options,
      anonymizedUrl: '/groups/*/options',
    });
  }

  getGroupAuditLog(group: EncodedGroupId, errFn?: ErrorCallback) {
    return this._fetchSharedCacheURL({
      url: `/groups/${group}/log.audit`,
      errFn,
      anonymizedUrl: '/groups/*/log.audit',
    });
  }

  saveGroupMembers(
    groupName: GroupId,
    groupMembers: AccountId
  ): Promise<AccountInfo> {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(groupMembers);
    return (this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}/members/${encodeMember}`,
      parseResponse: true,
      anonymizedUrl: '/groups/*/members/*',
    }) as unknown) as Promise<AccountInfo>;
  }

  saveIncludedGroup(
    groupName: GroupId,
    includedGroup: GroupId,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | null | undefined> {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    const req = {
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      errFn,
      anonymizedUrl: '/groups/*/groups/*',
    };
    return this._restApiHelper.send(req).then(response => {
      if (response && response.ok) {
        return this.getResponseObject(response) as Promise<GroupInfo | null>;
      }
      return undefined;
    });
  }

  deleteGroupMembers(groupName: GroupId, groupMembers: AccountId) {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(groupMembers);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/groups/${encodeName}/members/${encodeMember}`,
      anonymizedUrl: '/groups/*/members/*',
    });
  }

  deleteIncludedGroup(groupName: GroupId, includedGroup: GroupId) {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      anonymizedUrl: '/groups/*/groups/*',
    });
  }

  getVersion() {
    return this._fetchSharedCacheURL({
      url: '/config/server/version',
      reportUrlAsIs: true,
    });
  }

  getDiffPreferences(): Promise<DiffPreferencesInfo | null | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return this._fetchSharedCacheURL({
          url: '/accounts/self/preferences.diff',
          reportUrlAsIs: true,
        }) as Promise<DiffPreferencesInfo | null | undefined>;
      }
      const anonymousResult: DiffPreferencesInfo = {
        auto_hide_diff_table_header: true,
        context: 10,
        cursor_blink_rate: 0,
        font_size: 12,
        ignore_whitespace: 'IGNORE_NONE',
        intraline_difference: true,
        line_length: 100,
        line_wrapping: false,
        show_line_endings: true,
        show_tabs: true,
        show_whitespace_errors: true,
        syntax_highlighting: true,
        tab_size: 8,
        theme: 'DEFAULT',
      };
      // These defaults should match the defaults in
      // java/com/google/gerrit/extensions/client/DiffPreferencesInfo.java
      // NOTE: There are some settings that don't apply to PolyGerrit
      // (Render mode being at least one of them).
      return Promise.resolve(anonymousResult);
    });
  }

  getEditPreferences() {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return this._fetchSharedCacheURL({
          url: '/accounts/self/preferences.edit',
          reportUrlAsIs: true,
        });
      }
      // These defaults should match the defaults in
      // java/com/google/gerrit/extensions/client/EditPreferencesInfo.java
      return Promise.resolve({
        auto_close_brackets: false,
        cursor_blink_rate: 0,
        hide_line_numbers: false,
        hide_top_menu: false,
        indent_unit: 2,
        indent_with_tabs: false,
        key_map_type: 'DEFAULT',
        line_length: 100,
        line_wrapping: false,
        match_brackets: true,
        show_base: false,
        show_tabs: true,
        show_whitespace_errors: true,
        syntax_highlighting: true,
        tab_size: 8,
        theme: 'DEFAULT',
      });
    });
  }

  savePreferences(prefs, opt_errFn) {
    // Note (Issue 5142): normalize the download scheme with lower case before
    // saving.
    if (prefs.download_scheme) {
      prefs.download_scheme = prefs.download_scheme.toLowerCase();
    }

    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences',
      body: prefs,
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  saveDiffPreferences(prefs, opt_errFn) {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.diff');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.diff',
      body: prefs,
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  saveEditPreferences(prefs, opt_errFn) {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.edit');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.edit',
      body: prefs,
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  getAccount() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/detail',
      reportUrlAsIs: true,
      errFn: resp => {
        if (!resp || resp.status === 403) {
          this._cache.delete('/accounts/self/detail');
        }
      },
    });
  }

  getAvatarChangeUrl() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/avatar.change.url',
      reportUrlAsIs: true,
      errFn: resp => {
        if (!resp || resp.status === 403) {
          this._cache.delete('/accounts/self/avatar.change.url');
        }
      },
    });
  }

  getExternalIds() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/external.ids',
      reportUrlAsIs: true,
    });
  }

  deleteAccountIdentity(id) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/external.ids:delete',
      body: id,
      parseResponse: true,
      reportUrlAsIs: true,
    });
  }

  getAccountDetails(userId) {
    return this._restApiHelper.fetchJSON({
      url: `/accounts/${encodeURIComponent(userId)}/detail`,
      anonymizedUrl: '/accounts/*/detail',
    });
  }

  getAccountEmails() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/emails',
      reportUrlAsIs: true,
    });
  }

  addAccountEmail(email, opt_errFn) {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      errFn: opt_errFn,
      anonymizedUrl: '/account/self/emails/*',
    });
  }

  deleteAccountEmail(email, opt_errFn) {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      errFn: opt_errFn,
      anonymizedUrl: '/accounts/self/email/*',
    });
  }

  setPreferredAccountEmail(email, opt_errFn) {
    const encodedEmail = encodeURIComponent(email);
    const req = {
      method: HttpMethod.PUT,
      url: `/accounts/self/emails/${encodedEmail}/preferred`,
      errFn: opt_errFn,
      anonymizedUrl: '/accounts/self/emails/*/preferred',
    };
    return this._restApiHelper.send(req).then(() => {
      // If result of getAccountEmails is in cache, update it in the cache
      // so we don't have to invalidate it.
      const cachedEmails = this._cache.get('/accounts/self/emails');
      if (cachedEmails) {
        const emails = cachedEmails.map(entry => {
          if (entry.email === email) {
            return {email, preferred: true};
          } else {
            return {email};
          }
        });
        this._cache.set('/accounts/self/emails', emails);
      }
    });
  }

  _updateCachedAccount(obj) {
    // If result of getAccount is in cache, update it in the cache
    // so we don't have to invalidate it.
    const cachedAccount = this._cache.get('/accounts/self/detail');
    if (cachedAccount) {
      // Replace object in cache with new object to force UI updates.
      this._cache.set('/accounts/self/detail', {...cachedAccount, ...obj});
    }
  }

  setAccountName(name: string, errFn?: ErrorCallback) {
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/name',
      body: {name},
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newName => this._updateCachedAccount({name: newName}));
  }

  setAccountUsername(username: string, errFn?: ErrorCallback) {
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/username',
      body: {username},
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newName => this._updateCachedAccount({username: newName}));
  }

  setAccountDisplayName(displayName: string, errFn?: ErrorCallback) {
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/displayname',
      body: {display_name: displayName},
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newName => this._updateCachedAccount({displayName: newName}));
  }

  setAccountStatus(status: string, errFn?: ErrorCallback) {
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/status',
      body: {status},
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newStatus => this._updateCachedAccount({status: newStatus}));
  }

  getAccountStatus(userId: AccountId) {
    return this._restApiHelper.fetchJSON({
      url: `/accounts/${encodeURIComponent(userId)}/status`,
      anonymizedUrl: '/accounts/*/status',
    });
  }

  getAccountGroups() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/groups',
      reportUrlAsIs: true,
    });
  }

  getAccountAgreements() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/agreements',
      reportUrlAsIs: true,
    });
  }

  saveAccountAgreement(name: string) {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/agreements',
      body: name,
      reportUrlAsIs: true,
    });
  }

  getAccountCapabilities(params?: string[]) {
    let queryString = '';
    if (params) {
      queryString =
        '?q=' + params.map(param => encodeURIComponent(param)).join('&q=');
    }
    return this._fetchSharedCacheURL({
      url: '/accounts/self/capabilities' + queryString,
      anonymizedUrl: '/accounts/self/capabilities?q=*',
    });
  }

  getLoggedIn() {
    return this.authService.authCheck();
  }

  getIsAdmin() {
    return this.getLoggedIn()
      .then(isLoggedIn => {
        if (isLoggedIn) {
          return this.getAccountCapabilities();
        } else {
          return Promise.resolve();
        }
      })
      .then(capabilities => capabilities && capabilities.administrateServer);
  }

  getDefaultPreferences() {
    return this._fetchSharedCacheURL({
      url: '/config/server/preferences',
      reportUrlAsIs: true,
    });
  }

  getPreferences() {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        const req = {url: '/accounts/self/preferences', reportUrlAsIs: true};
        return this._fetchSharedCacheURL(req).then(res => {
          if (this._isNarrowScreen()) {
            // Note that this can be problematic, because the diff will stay
            // unified even after increasing the window width.
            res.default_diff_view = DiffViewMode.UNIFIED;
          } else {
            res.default_diff_view = res.diff_view;
          }
          return Promise.resolve(res);
        });
      }

      return Promise.resolve({
        changes_per_page: 25,
        default_diff_view: this._isNarrowScreen()
          ? DiffViewMode.UNIFIED
          : DiffViewMode.SIDE_BY_SIDE,
        diff_view: 'SIDE_BY_SIDE',
        size_bar_in_change_table: true,
      });
    });
  }

  getWatchedProjects() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/watched.projects',
      reportUrlAsIs: true,
    });
  }

  saveWatchedProjects(
    projects: ProjectWatchInfo[],
    errFn?: ErrorCallback
  ): Promise<ProjectWatchInfo[]> {
    return (this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/watched.projects',
      body: projects,
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown) as Promise<ProjectWatchInfo[]>;
  }

  deleteWatchedProjects(projects: ProjectWatchInfo[], errFn?: ErrorCallback) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/watched.projects:delete',
      body: projects,
      errFn,
      reportUrlAsIs: true,
    });
  }

  _isNarrowScreen() {
    return window.innerWidth < MAX_UNIFIED_DEFAULT_WINDOW_WIDTH_PX;
  }

  /**
   * @return If opt_query is an
   * array, _fetchJSON will return an array of arrays of changeInfos. If it
   * is unspecified or a string, _fetchJSON will return an array of
   * changeInfos.
   */
  getChanges(opt_changesPerPage, opt_query, opt_offset, opt_options) {
    return this.getConfig(false)
      .then(config => {
        const options = opt_options || this._getChangesOptionsHex(config);
        // Issue 4524: respect legacy token with max sortkey.
        if (opt_offset === 'n,z') {
          opt_offset = 0;
        }
        const params = {
          O: options,
          S: opt_offset || 0,
        };
        if (opt_changesPerPage) {
          params.n = opt_changesPerPage;
        }
        if (opt_query && opt_query.length > 0) {
          params.q = opt_query;
        }
        return {
          url: '/changes/',
          params,
          reportUrlAsIs: true,
        };
      })
      .then(req => this._restApiHelper.fetchJSON(req, true))
      .then(response => {
        const iterateOverChanges = arr => {
          for (const change of arr || []) {
            this._maybeInsertInLookup(change);
          }
        };
        // Response may be an array of changes OR an array of arrays of
        // changes.
        if (opt_query instanceof Array) {
          // Normalize the response to look like a multi-query response
          // when there is only one query.
          if (opt_query.length === 1) {
            response = [response];
          }
          for (const arr of response) {
            iterateOverChanges(arr);
          }
        } else {
          iterateOverChanges(response);
        }
        return response;
      });
  }

  /**
   * Inserts a change into _projectLookup iff it has a valid structure.
   */
  _maybeInsertInLookup(change) {
    if (change && change.project && change._number) {
      this.setInProjectLookup(change._number, change.project);
    }
  }

  getChangeActionURL(
    changeNum: ChangeNum,
    patchNum: PatchSetNum | undefined,
    endpoint: string
  ) {
    return this._changeBaseURL(changeNum, patchNum).then(url => url + endpoint);
  }

  getChangeDetail(
    changeNum: ChangeNum,
    errFn?: ErrorCallback,
    opt_cancelCondition
  ) {
    return this.getConfig(false).then(config => {
      const optionsHex = this._getChangeOptionsHex(config);
      return this._getChangeDetail(
        changeNum,
        optionsHex,
        errFn,
        opt_cancelCondition
      ).then(GrReviewerUpdatesParser.parse);
    });
  }

  _getChangesOptionsHex(config) {
    if (
      window.DEFAULT_DETAIL_HEXES &&
      window.DEFAULT_DETAIL_HEXES.dashboardPage
    ) {
      return window.DEFAULT_DETAIL_HEXES.dashboardPage;
    }
    const options = [
      ListChangesOption.LABELS,
      ListChangesOption.DETAILED_ACCOUNTS,
    ];
    if (config && config.change && config.change.enable_attention_set) {
      options.push(ListChangesOption.DETAILED_LABELS);
    } else {
      options.push(ListChangesOption.REVIEWED);
    }

    return listChangesOptionsToHex(...options);
  }

  _getChangeOptionsHex(config) {
    if (
      window.DEFAULT_DETAIL_HEXES &&
      window.DEFAULT_DETAIL_HEXES.changePage &&
      !(config.receive && config.receive.enable_signed_push)
    ) {
      return window.DEFAULT_DETAIL_HEXES.changePage;
    }

    // This list MUST be kept in sync with
    // ChangeIT#changeDetailsDoesNotRequireIndex
    const options = [
      ListChangesOption.ALL_COMMITS,
      ListChangesOption.ALL_REVISIONS,
      ListChangesOption.CHANGE_ACTIONS,
      ListChangesOption.DETAILED_LABELS,
      ListChangesOption.DOWNLOAD_COMMANDS,
      ListChangesOption.MESSAGES,
      ListChangesOption.SUBMITTABLE,
      ListChangesOption.WEB_LINKS,
      ListChangesOption.SKIP_DIFFSTAT,
    ];
    if (config.receive && config.receive.enable_signed_push) {
      options.push(ListChangesOption.PUSH_CERTIFICATES);
    }
    return listChangesOptionsToHex(...options);
  }

  getDiffChangeDetail(changeNum, opt_errFn, opt_cancelCondition) {
    let optionsHex = '';
    if (window.DEFAULT_DETAIL_HEXES && window.DEFAULT_DETAIL_HEXES.diffPage) {
      optionsHex = window.DEFAULT_DETAIL_HEXES.diffPage;
    } else {
      optionsHex = listChangesOptionsToHex(
        ListChangesOption.ALL_COMMITS,
        ListChangesOption.ALL_REVISIONS,
        ListChangesOption.SKIP_DIFFSTAT
      );
    }
    return this._getChangeDetail(
      changeNum,
      optionsHex,
      opt_errFn,
      opt_cancelCondition
    );
  }

  /**
   * @param optionsHex list changes options in hex
   */
  _getChangeDetail(
    changeNum: ChangeNum,
    optionsHex,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ) {
    return this.getChangeActionURL(changeNum, undefined, '/detail').then(
      url => {
        const urlWithParams = this._restApiHelper.urlWithParams(
          url,
          optionsHex
        );
        const params = {O: optionsHex};
        const req: FetchJSONRequest = {
          url,
          errFn,
          cancelCondition,
          params,
          fetchOptions: this._etags.getOptions(urlWithParams),
          anonymizedUrl: '/changes/*~*/detail?O=' + optionsHex,
        };
        return this._restApiHelper.fetchRawJSON(req).then(response => {
          if (response && response.status === 304) {
            return Promise.resolve(
              this._restApiHelper.parsePrefixedJSON(
                this._etags.getCachedPayload(urlWithParams)
              )
            );
          }

          if (response && !response.ok) {
            if (errFn) {
              errFn.call(null, response);
            } else {
              this.dispatchEvent(
                new CustomEvent('server-error', {
                  detail: {request: req, response},
                  composed: true,
                  bubbles: true,
                })
              );
            }
            return;
          }

          const payloadPromise = response
            ? this._restApiHelper.readResponsePayload(response)
            : Promise.resolve(null);

          return payloadPromise.then(payload => {
            if (!payload) {
              return null;
            }
            this._etags.collect(urlWithParams, response, payload.raw);
            this._maybeInsertInLookup(payload.parsed);

            return payload.parsed;
          });
        });
      }
    );
  }

  getChangeCommitInfo(changeNum: ChangeNum, patchNum: PatchSetNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/commit?links',
      patchNum,
      reportEndpointAsIs: true,
    });
  }

  getChangeFiles(changeNum, patchRange, opt_parentIndex) {
    let params = undefined;
    if (isMergeParent(patchRange.basePatchNum)) {
      params = {parent: getParentIndex(patchRange.basePatchNum)};
    } else if (
      !patchNumEquals(patchRange.basePatchNum, SPECIAL_PATCH_SET_NUM.PARENT)
    ) {
      params = {base: patchRange.basePatchNum};
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/files',
      patchNum: patchRange.patchNum,
      params,
      reportEndpointAsIs: true,
    });
  }

  getChangeEditFiles(changeNum, patchRange) {
    let endpoint = '/edit?list';
    let anonymizedEndpoint = endpoint;
    if (patchRange.basePatchNum !== 'PARENT') {
      endpoint += '&base=' + encodeURIComponent(patchRange.basePatchNum + '');
      anonymizedEndpoint += '&base=*';
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint,
      anonymizedEndpoint,
    });
  }

  queryChangeFiles(changeNum, patchNum, query) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/files?q=${encodeURIComponent(query)}`,
      patchNum,
      anonymizedEndpoint: '/files?q=*',
    });
  }

  getChangeOrEditFiles(changeNum, patchRange) {
    if (patchNumEquals(patchRange.patchNum, SPECIAL_PATCH_SET_NUM.EDIT)) {
      return this.getChangeEditFiles(changeNum, patchRange).then(
        res => res.files
      );
    }
    return this.getChangeFiles(changeNum, patchRange);
  }

  getChangeRevisionActions(changeNum, patchNum) {
    const req = {
      changeNum,
      endpoint: '/actions',
      patchNum,
      reportEndpointAsIs: true,
    };
    return this._getChangeURLAndFetch(req);
  }

  getChangeSuggestedReviewers(changeNum, inputVal, opt_errFn) {
    return this._getChangeSuggestedGroup(
      'REVIEWER',
      changeNum,
      inputVal,
      opt_errFn
    );
  }

  getChangeSuggestedCCs(changeNum, inputVal, opt_errFn) {
    return this._getChangeSuggestedGroup('CC', changeNum, inputVal, opt_errFn);
  }

  _getChangeSuggestedGroup(reviewerState, changeNum, inputVal, opt_errFn) {
    // More suggestions may obscure content underneath in the reply dialog,
    // see issue 10793.
    const params = {n: 6, 'reviewer-state': reviewerState};
    if (inputVal) {
      params.q = inputVal;
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/suggest_reviewers',
      errFn: opt_errFn,
      params,
      reportEndpointAsIs: true,
    });
  }

  getChangeIncludedIn(changeNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/in',
      reportEndpointAsIs: true,
    });
  }

  _computeFilter(filter: string) {
    if (filter && filter.startsWith('^')) {
      filter = '&r=' + encodeURIComponent(filter);
    } else if (filter) {
      filter = '&m=' + encodeURIComponent(filter);
    } else {
      filter = '';
    }
    return filter;
  }

  _getGroupsUrl(filter, groupsPerPage, opt_offset) {
    const offset = opt_offset || 0;

    return (
      `/groups/?n=${groupsPerPage + 1}&S=${offset}` +
      this._computeFilter(filter)
    );
  }

  _getReposUrl(filter: string, reposPerPage: number, offset?: number) {
    const defaultFilter = 'state:active OR state:read-only';
    const namePartDelimiters = /[@.\-\s\/_]/g;
    offset = offset || 0;

    if (filter && !filter.includes(':') && filter.match(namePartDelimiters)) {
      // The query language specifies hyphens as operators. Split the string
      // by hyphens and 'AND' the parts together as 'inname:' queries.
      // If the filter includes a semicolon, the user is using a more complex
      // query so we trust them and don't do any magic under the hood.
      const originalFilter = filter;
      filter = '';
      originalFilter.split(namePartDelimiters).forEach(part => {
        if (part) {
          filter += (filter === '' ? 'inname:' : ' AND inname:') + part;
        }
      });
    }
    // Check if filter is now empty which could be either because the user did
    // not provide it or because the user provided only a split character.
    if (!filter) {
      filter = defaultFilter;
    }

    filter = filter.trim();
    const encodedFilter = encodeURIComponent(filter);

    return (
      `/projects/?n=${reposPerPage + 1}&S=${offset}` + `&query=${encodedFilter}`
    );
  }

  invalidateGroupsCache() {
    this._restApiHelper.invalidateFetchPromisesPrefix('/groups/?');
  }

  invalidateReposCache() {
    this._restApiHelper.invalidateFetchPromisesPrefix('/projects/?');
  }

  invalidateAccountsCache() {
    this._restApiHelper.invalidateFetchPromisesPrefix('/accounts/');
  }

  getGroups(filter: string, groupsPerPage: number, offset?: number) {
    const url = this._getGroupsUrl(filter, groupsPerPage, offset);

    return this._fetchSharedCacheURL({
      url,
      anonymizedUrl: '/groups/?*',
    });
  }

  getRepos(
    filter: string,
    reposPerPage: number,
    offset?: number
  ): Promise<ProjectInfo | undefined | null> {
    const url = this._getReposUrl(filter, reposPerPage, offset);

    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url,
      anonymizedUrl: '/projects/?*',
    }) as Promise<ProjectInfo | undefined | null>;
  }

  setRepoHead(repo: RepositoryName, ref: GitRef) {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeURIComponent(repo)}/HEAD`,
      body: {ref},
      anonymizedUrl: '/projects/*/HEAD',
    });
  }

  getRepoBranches(
    filter: string,
    repo: RepositoryName,
    reposBranchesPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<BranchInfo[] | null | undefined> {
    offset = offset || 0;
    const count = reposBranchesPerPage + 1;
    filter = this._computeFilter(filter);
    const encodedRepo = encodeURIComponent(repo);
    const url = `/projects/${encodedRepo}/branches?n=${count}&S=${offset}${filter}`;
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.fetchJSON({
      url,
      errFn,
      anonymizedUrl: '/projects/*/branches?*',
    }) as Promise<BranchInfo[] | null | undefined>;
  }

  getRepoTags(
    filter: string,
    repo: RepositoryName,
    reposTagsPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ) {
    offset = offset || 0;
    const encodedRepo = encodeURIComponent(repo);
    const n = reposTagsPerPage + 1;
    const encodedFilter = this._computeFilter(filter);
    const url =
      `/projects/${encodedRepo}/tags` + `?n=${n}&S=${offset}` + encodedFilter;
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.fetchJSON({
      url,
      errFn,
      anonymizedUrl: '/projects/*/tags',
    });
  }

  getPlugins(filter, pluginsPerPage, opt_offset, opt_errFn) {
    const offset = opt_offset || 0;
    const encodedFilter = this._computeFilter(filter);
    const n = pluginsPerPage + 1;
    const url = `/plugins/?all&n=${n}&S=${offset}${encodedFilter}`;
    return this._restApiHelper.fetchJSON({
      url,
      errFn: opt_errFn,
      anonymizedUrl: '/plugins/?all',
    });
  }

  getRepoAccessRights(repoName: RepositoryName, errFn?: ErrorCallback) {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.fetchJSON({
      url: `/projects/${encodeURIComponent(repoName)}/access`,
      errFn,
      anonymizedUrl: '/projects/*/access',
    });
  }

  setRepoAccessRights(repoName: RepositoryName, repoInfo: ProjectAccessInput) {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: `/projects/${encodeURIComponent(repoName)}/access`,
      body: repoInfo,
      anonymizedUrl: '/projects/*/access',
    });
  }

  setRepoAccessRightsForReview(
    projectName: ProjectName,
    projectInfo: ProjectAccessInput
  ): Promise<ChangeInfo> {
    return (this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeURIComponent(projectName)}/access:review`,
      body: projectInfo,
      parseResponse: true,
      anonymizedUrl: '/projects/*/access:review',
    }) as unknown) as Promise<ChangeInfo>;
  }

  getSuggestedGroups(inputVal: string, n?: number, errFn?: ErrorCallback) {
    const params = {s: inputVal};
    if (n) {
      params.n = n;
    }
    return this._restApiHelper.fetchJSON({
      url: '/groups/',
      errFn,
      params,
      reportUrlAsIs: true,
    });
  }

  getSuggestedProjects(
    inputVal: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<NameToProjectInfoMap | null | undefined> {
    const params = {
      m: inputVal,
      n: MAX_PROJECT_RESULTS,
      type: 'ALL',
    };
    if (n) {
      params.n = n;
    }
    return this._restApiHelper.fetchJSON({
      url: '/projects/',
      errFn,
      params,
      reportUrlAsIs: true,
    });
  }

  getSuggestedAccounts(inputVal: string, n?: number, errFn?: ErrorCallback) {
    if (!inputVal) {
      return Promise.resolve([]);
    }
    const params = {suggest: null, q: inputVal};
    if (n) {
      params.n = n;
    }
    return this._restApiHelper.fetchJSON({
      url: '/accounts/',
      errFn,
      params,
      anonymizedUrl: '/accounts/?n=*',
    });
  }

  addChangeReviewer(changeNum: ChangeNum, reviewerID) {
    return this._sendChangeReviewerRequest('POST', changeNum, reviewerID);
  }

  removeChangeReviewer(changeNum: ChangeNum, reviewerID) {
    return this._sendChangeReviewerRequest('DELETE', changeNum, reviewerID);
  }

  _sendChangeReviewerRequest(method, changeNum: ChangeNum, reviewerID) {
    return this.getChangeActionURL(changeNum, null, '/reviewers').then(url => {
      let body;
      switch (method) {
        case 'POST':
          body = {reviewer: reviewerID};
          break;
        case 'DELETE':
          url += '/' + encodeURIComponent(reviewerID);
          break;
        default:
          throw Error('Unsupported HTTP method: ' + method);
      }

      return this._restApiHelper.send({method, url, body});
    });
  }

  getRelatedChanges(changeNum, patchNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/related',
      patchNum,
      reportEndpointAsIs: true,
    });
  }

  getChangesSubmittedTogether(changeNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/submitted_together?o=NON_VISIBLE_CHANGES',
      reportEndpointAsIs: true,
    });
  }

  getChangeConflicts(changeNum: ChangeNum) {
    const options = listChangesOptionsToHex(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT
    );
    const params = {
      O: options,
      q: `status:open conflicts:${changeNum}`,
    };
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params,
      anonymizedUrl: '/changes/conflicts:*',
    });
  }

  getChangeCherryPicks(project, changeID, changeNum) {
    const options = listChangesOptionsToHex(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT
    );
    const query = [
      'project:' + project,
      'change:' + changeID,
      '-change:' + changeNum,
      '-is:abandoned',
    ].join(' ');
    const params = {
      O: options,
      q: query,
    };
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params,
      anonymizedUrl: '/changes/change:*',
    });
  }

  getChangesWithSameTopic(topic, changeNum) {
    const options = listChangesOptionsToHex(
      ListChangesOption.LABELS,
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT,
      ListChangesOption.DETAILED_LABELS
    );
    const query = [
      'status:open',
      '-change:' + changeNum,
      `topic:"${topic}"`,
    ].join(' ');
    const params = {
      O: options,
      q: query,
    };
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params,
      anonymizedUrl: '/changes/topic:*',
    });
  }

  getReviewedFiles(changeNum: ChangeNum, patchNum: PatchSetNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/files?reviewed',
      patchNum,
      reportEndpointAsIs: true,
    });
  }

  saveFileReviewed(
    changeNum: ChangeNum,
    patchNum: PatchSetNum,
    path: string,
    reviewed: boolean,
    errFn?: ErrorCallback
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: reviewed ? HttpMethod.PUT : HttpMethod.DELETE,
      patchNum,
      endpoint: `/files/${encodeURIComponent(path)}/reviewed`,
      errFn,
      anonymizedEndpoint: '/files/*/reviewed',
    });
  }

  saveChangeReview(
    changeNum: ChangeNum,
    patchNum: PatchSetNum,
    review,
    errFn?: ErrorCallback
  ) {
    const promises = [
      this.awaitPendingDiffDrafts(),
      this.getChangeActionURL(changeNum, patchNum, '/review'),
    ];
    return Promise.all(promises).then(([, url]) =>
      this._restApiHelper.send({
        method: HttpMethod.POST,
        url,
        body: review,
        errFn,
      })
    );
  }

  getChangeEdit(changeNum: ChangeNum, downloadCommands?: boolean) {
    const params = downloadCommands ? {'download-commands': true} : null;
    return this.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return false;
      }
      return this._getChangeURLAndFetch(
        {
          changeNum,
          endpoint: '/edit/',
          params,
          reportEndpointAsIs: true,
        },
        true
      );
    });
  }

  createChange(
    project,
    branch,
    subject,
    opt_topic,
    opt_isPrivate,
    opt_workInProgress,
    opt_baseChange,
    opt_baseCommit
  ) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/changes/',
      body: {
        project,
        branch,
        subject,
        topic: opt_topic,
        is_private: opt_isPrivate,
        work_in_progress: opt_workInProgress,
        base_change: opt_baseChange,
        base_commit: opt_baseCommit,
      },
      parseResponse: true,
      reportUrlAsIs: true,
    });
  }

  getFileContent(changeNum, path, patchNum) {
    // 404s indicate the file does not exist yet in the revision, so suppress
    // them.
    const suppress404s = res => {
      if (res && res.status !== 404) {
        this.dispatchEvent(
          new CustomEvent('server-error', {
            detail: {res},
            composed: true,
            bubbles: true,
          })
        );
      }
      return res;
    };
    const promise = patchNumEquals(patchNum, SPECIAL_PATCH_SET_NUM.EDIT)
      ? this._getFileInChangeEdit(changeNum, path)
      : this._getFileInRevision(changeNum, path, patchNum, suppress404s);

    return promise.then(res => {
      if (!res.ok) {
        return res;
      }

      // The file type (used for syntax highlighting) is identified in the
      // X-FYI-Content-Type header of the response.
      const type = res.headers.get('X-FYI-Content-Type');
      return this.getResponseObject(res).then(content => {
        return {content, type, ok: true};
      });
    });
  }

  /**
   * Gets a file in a specific change and revision.
   */
  _getFileInRevision(
    changeNum: ChangeNum,
    path: string,
    patchNum: PatchSetNum,
    errFn?: ErrorCallback
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.GET,
      patchNum,
      endpoint: `/files/${encodeURIComponent(path)}/content`,
      errFn,
      headers: {Accept: 'application/json'},
      anonymizedEndpoint: '/files/*/content',
    });
  }

  /**
   * Gets a file in a change edit.
   */
  _getFileInChangeEdit(changeNum: ChangeNum, path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.GET,
      endpoint: '/edit/' + encodeURIComponent(path),
      headers: {Accept: 'application/json'},
      anonymizedEndpoint: '/edit/*',
    });
  }

  rebaseChangeEdit(changeNum: ChangeNum) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit:rebase',
      reportEndpointAsIs: true,
    });
  }

  deleteChangeEdit(changeNum: ChangeNum) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/edit',
      reportEndpointAsIs: true,
    });
  }

  restoreFileInChangeEdit(changeNum: ChangeNum, restore_path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit',
      body: {restore_path},
      reportEndpointAsIs: true,
    });
  }

  renameFileInChangeEdit(
    changeNum: ChangeNum,
    old_path: string,
    new_path: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit',
      body: {old_path, new_path},
      reportEndpointAsIs: true,
    });
  }

  deleteFileInChangeEdit(changeNum: ChangeNum, path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/edit/' + encodeURIComponent(path),
      anonymizedEndpoint: '/edit/*',
    });
  }

  saveChangeEdit(changeNum: ChangeNum, path: string, contents) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit/' + encodeURIComponent(path),
      body: contents,
      contentType: 'text/plain',
      anonymizedEndpoint: '/edit/*',
    });
  }

  saveFileUploadChangeEdit(changeNum, path, content) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit/' + encodeURIComponent(path),
      body: {binary_content: content},
      anonymizedEndpoint: '/edit/*',
    });
  }

  getRobotCommentFixPreview(changeNum, patchNum, fixId) {
    return this._getChangeURLAndFetch({
      changeNum,
      patchNum,
      endpoint: `/fixes/${encodeURIComponent(fixId)}/preview`,
      reportEndpointAsId: true,
    });
  }

  applyFixSuggestion(changeNum, patchNum, fixId) {
    return this._getChangeURLAndSend({
      method: HttpMethod.POST,
      changeNum,
      patchNum,
      endpoint: `/fixes/${encodeURIComponent(fixId)}/apply`,
      reportEndpointAsId: true,
    });
  }

  // Deprecated, prefer to use putChangeCommitMessage instead.
  saveChangeCommitMessageEdit(changeNum, message) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit:message',
      body: {message},
      reportEndpointAsIs: true,
    });
  }

  publishChangeEdit(changeNum: ChangeNum) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit:publish',
      reportEndpointAsIs: true,
    });
  }

  putChangeCommitMessage(changeNum: ChangeNum, message) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/message',
      body: {message},
      reportEndpointAsIs: true,
    });
  }

  deleteChangeCommitMessage(changeNum: ChangeNum, messageId: ChangeMessageId) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/messages/${messageId}`,
      reportEndpointAsIs: true,
    });
  }

  saveChangeStarred(changeNum: ChangeNum, starred: boolean) {
    // Some servers may require the project name to be provided
    // alongside the change number, so resolve the project name
    // first.
    return this.getFromProjectLookup(changeNum).then(project => {
      const encodedProjectName = project
        ? encodeURIComponent(project) + '~'
        : '';
      const url = `/accounts/self/starred.changes/${encodedProjectName}${changeNum}`;
      return this._restApiHelper.send({
        method: starred ? HttpMethod.PUT : HttpMethod.DELETE,
        url,
        anonymizedUrl: '/accounts/self/starred.changes/*',
      });
    });
  }

  saveChangeReviewed(changeNum: ChangeNum, reviewed: boolean) {
    return this.getConfig().then(config => {
      const isAttentionSetEnabled =
        !!config && !!config.change && config.change.enable_attention_set;
      if (isAttentionSetEnabled) return Promise.resolve();
      return this._getChangeURLAndSend({
        changeNum,
        method: HttpMethod.PUT,
        endpoint: reviewed ? '/reviewed' : '/unreviewed',
      });
    });
  }

  /**
   * Public version of the _restApiHelper.send method preserved for plugins.
   *
   * @param body passed as null sometimes
   * and also apparently a number. TODO (beckysiegel) remove need for
   * number at least.
   */
  send(
    method: HttpMethod,
    url: string,
    body?: string | object,
    errFn?: ErrorCallback,
    contentType?: string,
    headers?: Record<string, string>
  ) {
    return this._restApiHelper.send({
      method,
      url,
      body,
      errFn,
      contentType,
      headers,
    });
  }

  /**
   * @param basePatchNum Negative values specify merge parent
   * index.
   * @param opt_whitespace the ignore-whitespace level for the diff
   * algorithm.
   */
  getDiff(changeNum, basePatchNum, patchNum, path, opt_whitespace, opt_errFn) {
    const params = {
      context: 'ALL',
      intraline: null,
      whitespace: opt_whitespace || 'IGNORE_NONE',
    };
    if (isMergeParent(basePatchNum)) {
      params.parent = getParentIndex(basePatchNum);
    } else if (!patchNumEquals(basePatchNum, SPECIAL_PATCH_SET_NUM.PARENT)) {
      params.base = basePatchNum;
    }
    const endpoint = `/files/${encodeURIComponent(path)}/diff`;
    const req = {
      changeNum,
      endpoint,
      patchNum,
      errFn: opt_errFn,
      params,
      anonymizedEndpoint: '/files/*/diff',
    };

    // Invalidate the cache if its edit patch to make sure we always get latest.
    if (patchNum === SPECIAL_PATCH_SET_NUM.EDIT) {
      if (!req.fetchOptions) req.fetchOptions = {};
      if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
      req.fetchOptions.headers.append('Cache-Control', 'no-cache');
    }

    return this._getChangeURLAndFetch(req);
  }

  getDiffComments(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
    return this._getDiffComments(
      changeNum,
      '/comments',
      opt_basePatchNum,
      opt_patchNum,
      opt_path
    );
  }

  getDiffRobotComments(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
    return this._getDiffComments(
      changeNum,
      '/robotcomments',
      opt_basePatchNum,
      opt_patchNum,
      opt_path
    );
  }

  /**
   * If the user is logged in, fetch the user's draft diff comments. If there
   * is no logged in user, the request is not made and the promise yields an
   * empty object.
   */
  getDiffDrafts(changeNum, opt_basePatchNum, opt_patchNum, opt_path) {
    return this.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return Promise.resolve({});
      }
      return this._getDiffComments(
        changeNum,
        '/drafts',
        opt_basePatchNum,
        opt_patchNum,
        opt_path
      );
    });
  }

  _setRange(comments, comment) {
    if (comment.in_reply_to && !comment.range) {
      for (let i = 0; i < comments.length; i++) {
        if (comments[i].id === comment.in_reply_to) {
          comment.range = comments[i].range;
          break;
        }
      }
    }
    return comment;
  }

  _setRanges(comments) {
    comments = comments || [];
    comments.sort((a, b) => parseDate(a.updated) - parseDate(b.updated));
    for (const comment of comments) {
      this._setRange(comments, comment);
    }
    return comments;
  }

  _getDiffComments(
    changeNum,
    endpoint,
    opt_basePatchNum,
    opt_patchNum,
    opt_path
  ) {
    /**
     * Fetches the comments for a given patchNum.
     * Helper function to make promises more legible.
     */
    // We don't want to add accept header, since preloading of comments is
    // working only without accept header.
    const noAcceptHeader = true;
    const fetchComments = opt_patchNum =>
      this._getChangeURLAndFetch(
        {
          changeNum,
          endpoint,
          patchNum: opt_patchNum,
          reportEndpointAsIs: true,
        },
        noAcceptHeader
      );

    if (!opt_basePatchNum && !opt_patchNum && !opt_path) {
      return fetchComments();
    }
    function onlyParent(c) {
      return c.side == SPECIAL_PATCH_SET_NUM.PARENT;
    }
    function withoutParent(c) {
      return c.side != SPECIAL_PATCH_SET_NUM.PARENT;
    }
    function setPath(c) {
      c.path = opt_path;
    }

    const promises = [];
    let comments;
    let baseComments;
    let fetchPromise;
    fetchPromise = fetchComments(opt_patchNum).then(response => {
      comments = response[opt_path] || [];
      // TODO(kaspern): Implement this on in the backend so this can
      // be removed.
      // Sort comments by date so that parent ranges can be propagated
      // in a single pass.
      comments = this._setRanges(comments);

      if (opt_basePatchNum == SPECIAL_PATCH_SET_NUM.PARENT) {
        baseComments = comments.filter(onlyParent);
        baseComments.forEach(setPath);
      }
      comments = comments.filter(withoutParent);

      comments.forEach(setPath);
    });
    promises.push(fetchPromise);

    if (opt_basePatchNum != SPECIAL_PATCH_SET_NUM.PARENT) {
      fetchPromise = fetchComments(opt_basePatchNum).then(response => {
        baseComments = (response[opt_path] || []).filter(withoutParent);
        baseComments = this._setRanges(baseComments);
        baseComments.forEach(setPath);
      });
      promises.push(fetchPromise);
    }

    return Promise.all(promises).then(() =>
      Promise.resolve({
        baseComments,
        comments,
      })
    );
  }

  _getDiffCommentsFetchURL(changeNum, endpoint, opt_patchNum) {
    return this._changeBaseURL(changeNum, opt_patchNum).then(
      url => url + endpoint
    );
  }

  saveDiffDraft(changeNum, patchNum, draft) {
    return this._sendDiffDraftRequest('PUT', changeNum, patchNum, draft);
  }

  deleteDiffDraft(changeNum, patchNum, draft) {
    return this._sendDiffDraftRequest('DELETE', changeNum, patchNum, draft);
  }

  /**
   * @returns Whether there are pending diff draft sends.
   */
  hasPendingDiffDrafts() {
    const promises = this._pendingRequests[Requests.SEND_DIFF_DRAFT];
    return promises && promises.length;
  }

  /**
   * @returns A promise that resolves when all pending
   * diff draft sends have resolved.
   */
  awaitPendingDiffDrafts() {
    return Promise.all(
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] || []
    ).then(() => {
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
    });
  }

  _sendDiffDraftRequest(method, changeNum, patchNum, draft) {
    const isCreate = !draft.id && method === 'PUT';
    let endpoint = '/drafts';
    let anonymizedEndpoint = endpoint;
    if (draft.id) {
      endpoint += '/' + draft.id;
      anonymizedEndpoint += '/*';
    }
    let body;
    if (method === 'PUT') {
      body = draft;
    }

    if (!this._pendingRequests[Requests.SEND_DIFF_DRAFT]) {
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
    }

    const req = {
      changeNum,
      method,
      patchNum,
      endpoint,
      body,
      anonymizedEndpoint,
    };

    const promise = this._getChangeURLAndSend(req);
    this._pendingRequests[Requests.SEND_DIFF_DRAFT].push(promise);

    if (isCreate) {
      return this._failForCreate200(promise);
    }

    return promise;
  }

  getCommitInfo(
    project: ProjectName,
    commit: CommitId
  ): Promise<CommitInfo | null | undefined> {
    return this._restApiHelper.fetchJSON({
      url:
        '/projects/' +
        encodeURIComponent(project) +
        '/commits/' +
        encodeURIComponent(commit),
      anonymizedUrl: '/projects/*/comments/*',
    }) as Promise<CommitInfo | null | undefined>;
  }

  _fetchB64File(url: string): Promise<Base64File> {
    return this._restApiHelper
      .fetch({url: getBaseUrl() + url})
      .then(response => {
        if (!response.ok) {
          return Promise.reject(new Error(response.statusText));
        }
        const type = response.headers.get('X-FYI-Content-Type');
        return response.text().then(text => {
          return {body: text, type};
        });
      });
  }

  getB64FileContents(
    changeId: ChangeId,
    patchNum: PatchSetNum,
    path: string,
    parentIndex?: number
  ) {
    const parent =
      typeof parentIndex === 'number' ? '?parent=' + parentIndex : '';
    return this._changeBaseURL(changeId, patchNum).then(url => {
      url = `${url}/files/${encodeURIComponent(path)}/content${parent}`;
      return this._fetchB64File(url);
    });
  }

  getImagesForDiff(changeNum, diff, patchRange) {
    let promiseA;
    let promiseB;

    if (diff.meta_a && diff.meta_a.content_type.startsWith('image/')) {
      if (patchRange.basePatchNum === 'PARENT') {
        // Note: we only attempt to get the image from the first parent.
        promiseA = this.getB64FileContents(
          changeNum,
          patchRange.patchNum,
          diff.meta_a.name,
          1
        );
      } else {
        promiseA = this.getB64FileContents(
          changeNum,
          patchRange.basePatchNum,
          diff.meta_a.name
        );
      }
    } else {
      promiseA = Promise.resolve(null);
    }

    if (diff.meta_b && diff.meta_b.content_type.startsWith('image/')) {
      promiseB = this.getB64FileContents(
        changeNum,
        patchRange.patchNum,
        diff.meta_b.name
      );
    } else {
      promiseB = Promise.resolve(null);
    }

    return Promise.all([promiseA, promiseB]).then(results => {
      const baseImage = results[0];
      const revisionImage = results[1];

      // Sometimes the server doesn't send back the content type.
      if (baseImage) {
        baseImage._expectedType = diff.meta_a.content_type;
        baseImage._name = diff.meta_a.name;
      }
      if (revisionImage) {
        revisionImage._expectedType = diff.meta_b.content_type;
        revisionImage._name = diff.meta_b.name;
      }

      return {baseImage, revisionImage};
    });
  }

  _changeBaseURL(
    changeNum: ChangeNum,
    patchNum?: PatchSetNum,
    project?: ProjectName
  ) {
    // TODO(kaspern): For full slicer migration, app should warn with a call
    // stack every time _changeBaseURL is called without a project.
    const projectPromise = project
      ? Promise.resolve(project)
      : this.getFromProjectLookup(changeNum);
    return projectPromise.then(project => {
      let url = `/changes/${encodeURIComponent(project)}~${changeNum}`;
      if (patchNum) {
        url += `/revisions/${patchNum}`;
      }
      return url;
    });
  }

  addToAttentionSet(changeNum, user, reason) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/attention',
      body: {user, reason},
      reportUrlAsIs: true,
    });
  }

  removeFromAttentionSet(changeNum, user, reason) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/attention/${user}`,
      anonymizedEndpoint: '/attention/*',
      body: {reason},
    });
  }

  setChangeTopic(changeNum, topic) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/topic',
      body: {topic},
      parseResponse: true,
      reportUrlAsIs: true,
    });
  }

  setChangeHashtag(changeNum, hashtag) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/hashtags',
      body: hashtag,
      parseResponse: true,
      reportUrlAsIs: true,
    });
  }

  deleteAccountHttpPassword() {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/password.http',
      reportUrlAsIs: true,
    });
  }

  generateAccountHttpPassword() {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/password.http',
      body: {generate: true},
      parseResponse: true,
      reportUrlAsIs: true,
    });
  }

  getAccountSSHKeys() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/sshkeys',
      reportUrlAsIs: true,
    });
  }

  addAccountSSHKey(key) {
    const req = {
      method: HttpMethod.POST,
      url: '/accounts/self/sshkeys',
      body: key,
      contentType: 'text/plain',
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(response => {
        if (response.status < 200 && response.status >= 300) {
          return Promise.reject(new Error('error'));
        }
        return this.getResponseObject(response);
      })
      .then(obj => {
        if (!obj.valid) {
          return Promise.reject(new Error('error'));
        }
        return obj;
      });
  }

  deleteAccountSSHKey(id: string) {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/sshkeys/' + id,
      anonymizedUrl: '/accounts/self/sshkeys/*',
    });
  }

  getAccountGPGKeys() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/gpgkeys',
      reportUrlAsIs: true,
    });
  }

  addAccountGPGKey(key: GpgKeyId) {
    const req = {
      method: HttpMethod.POST,
      url: '/accounts/self/gpgkeys',
      body: key,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(response => {
        if (!response || (response.status < 200 && response.status >= 300)) {
          return Promise.reject(new Error('error'));
        }
        return this.getResponseObject(response);
      })
      .then(obj => {
        if (!obj) {
          return Promise.reject(new Error('error'));
        }
        return obj;
      });
  }

  deleteAccountGPGKey(id: GpgKeyId) {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/gpgkeys/' + id,
      anonymizedUrl: '/accounts/self/gpgkeys/*',
    });
  }

  deleteVote(changeNum, account, label) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/reviewers/${account}/votes/${encodeURIComponent(label)}`,
      anonymizedEndpoint: '/reviewers/*/votes/*',
    });
  }

  setDescription(changeNum, patchNum, desc) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      patchNum,
      endpoint: '/description',
      body: {description: desc},
      reportUrlAsIs: true,
    });
  }

  confirmEmail(token) {
    const req = {
      method: HttpMethod.PUT,
      url: '/config/server/email.confirm',
      body: {token},
      reportUrlAsIs: true,
    };
    return this._restApiHelper.send(req).then(response => {
      if (response.status === 204) {
        return 'Email confirmed successfully.';
      }
      return null;
    });
  }

  getCapabilities(opt_errFn) {
    return this._restApiHelper.fetchJSON({
      url: '/config/server/capabilities',
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  getTopMenus(opt_errFn) {
    return this._fetchSharedCacheURL({
      url: '/config/server/top-menus',
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  setAssignee(changeNum, assignee) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/assignee',
      body: {assignee},
      reportUrlAsIs: true,
    });
  }

  deleteAssignee(changeNum) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/assignee',
      reportUrlAsIs: true,
    });
  }

  probePath(path) {
    return fetch(new Request(path, {method: HttpMethod.HEAD})).then(
      response => response.ok
    );
  }

  startWorkInProgress(changeNum, opt_message) {
    const body = {};
    if (opt_message) {
      body.message = opt_message;
    }
    const req = {
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/wip',
      body,
      reportUrlAsIs: true,
    };
    return this._getChangeURLAndSend(req).then(response => {
      if (response.status === 204) {
        return 'Change marked as Work In Progress.';
      }
    });
  }

  startReview(changeNum, opt_body, opt_errFn) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/ready',
      body: opt_body,
      errFn: opt_errFn,
      reportUrlAsIs: true,
    });
  }

  /**
   * @suppress {checkTypes}
   * Resulted in error: Promise.prototype.then does not match formal
   * parameter.
   */
  deleteComment(changeNum, patchNum, commentID, reason) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      patchNum,
      endpoint: `/comments/${commentID}/delete`,
      body: {reason},
      parseResponse: true,
      anonymizedEndpoint: '/comments/*/delete',
    });
  }

  /**
   * Given a changeNum, gets the change.
   */
  getChange(
    changeNum: ChangeNum,
    errFn: ErrorCallback
  ): Promise<ChangeInfo | null> {
    // Cannot use _changeBaseURL, as this function is used by _projectLookup.
    return this._restApiHelper
      .fetchJSON({
        url: `/changes/?q=change:${changeNum}`,
        errFn,
        anonymizedUrl: '/changes/?q=change:*',
      })
      .then(res => {
        const changeInfos = res as ChangeInfo[] | null;
        if (!changeInfos || !changeInfos.length) {
          return null;
        }
        return changeInfos[0];
      });
  }

  setInProjectLookup(changeNum, project) {
    if (
      this._projectLookup[changeNum] &&
      this._projectLookup[changeNum] !== project
    ) {
      console.warn(
        'Change set with multiple project nums.' +
          'One of them must be invalid.'
      );
    }
    this._projectLookup[changeNum] = project;
  }

  /**
   * Checks in _projectLookup for the changeNum. If it exists, returns the
   * project. If not, calls the restAPI to get the change, populates
   * _projectLookup with the project for that change, and returns the project.
   */
  getFromProjectLookup(changeNum) {
    const project = this._projectLookup[changeNum];
    if (project) {
      return Promise.resolve(project);
    }

    const onError = response => {
      // Fire a page error so that the visual 404 is displayed.
      this.dispatchEvent(
        new CustomEvent('page-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    };

    return this.getChange(changeNum, onError).then(change => {
      if (!change || !change.project) {
        return;
      }
      this.setInProjectLookup(changeNum, change.project);
      return change.project;
    });
  }

  /**
   * Alias for _changeBaseURL.then(send).
   */
  _getChangeURLAndSend(req) {
    const anonymizedBaseUrl = req.patchNum
      ? ANONYMIZED_REVISION_BASE_URL
      : ANONYMIZED_CHANGE_BASE_URL;
    const anonymizedEndpoint = req.reportEndpointAsIs
      ? req.endpoint
      : req.anonymizedEndpoint;

    return this._changeBaseURL(req.changeNum, req.patchNum).then(url =>
      this._restApiHelper.send({
        method: req.method,
        url: url + req.endpoint,
        body: req.body,
        errFn: req.errFn,
        contentType: req.contentType,
        headers: req.headers,
        parseResponse: req.parseResponse,
        anonymizedUrl: anonymizedEndpoint
          ? anonymizedBaseUrl + anonymizedEndpoint
          : undefined,
      })
    );
  }

  /**
   * Alias for _changeBaseURL.then(_fetchJSON).
   */
  _getChangeURLAndFetch(req, noAcceptHeader?: boolean) {
    const anonymizedEndpoint = req.reportEndpointAsIs
      ? req.endpoint
      : req.anonymizedEndpoint;
    const anonymizedBaseUrl = req.patchNum
      ? ANONYMIZED_REVISION_BASE_URL
      : ANONYMIZED_CHANGE_BASE_URL;
    return this._changeBaseURL(req.changeNum, req.patchNum).then(url =>
      this._restApiHelper.fetchJSON(
        {
          url: url + req.endpoint,
          errFn: req.errFn,
          params: req.params,
          fetchOptions: req.fetchOptions,
          anonymizedUrl: anonymizedEndpoint
            ? anonymizedBaseUrl + anonymizedEndpoint
            : undefined,
        },
        noAcceptHeader
      )
    );
  }

  /**
   * Execute a change action or revision action on a change.
   */
  executeChangeAction(
    changeNum: ChangeNum,
    method: HttpMethod,
    endpoint: string,
    patchNum?: PatchSetNum,
    payload?: unknown,
    errFn?: ErrorCallback
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method,
      patchNum,
      endpoint,
      body: payload,
      errFn,
    });
  }

  /**
   * Get blame information for the given diff.
   *
   * @param base If true, requests blame for the base of the
   *     diff, rather than the revision.
   */
  getBlame(
    changeNum: ChangeNum,
    patchNum: PatchSetNum,
    path: string,
    base?: boolean
  ) {
    const encodedPath = encodeURIComponent(path);
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/files/${encodedPath}/blame`,
      patchNum,
      params: base ? {base: 't'} : undefined,
      anonymizedEndpoint: '/files/*/blame',
    });
  }

  /**
   * Modify the given create draft request promise so that it fails and throws
   * an error if the response bears HTTP status 200 instead of HTTP 201.
   *
   * @see Issue 7763
   * @param promise The original promise.
   * @return The modified promise.
   */
  _failForCreate200(promise) {
    return promise.then(result => {
      if (result.status === 200) {
        // Read the response headers into an object representation.
        const headers = Array.from(result.headers.entries()).reduce(
          (obj, [key, val]) => {
            if (!HEADER_REPORTING_BLOCK_REGEX.test(key)) {
              obj[key] = val;
            }
            return obj;
          },
          {}
        );
        const err = new Error(
          [
            CREATE_DRAFT_UNEXPECTED_STATUS_MESSAGE,
            JSON.stringify(headers),
          ].join('\n')
        );
        // Throw the error so that it is caught by gr-reporting.
        throw err;
      }
      return result;
    });
  }

  /**
   * Fetch a project dashboard definition.
   * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#get-dashboard
   */
  getDashboard(
    project: ProjectName,
    dashboard: DashboardId,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo | null | undefined> {
    const url =
      '/projects/' +
      encodeURIComponent(project) +
      '/dashboards/' +
      encodeURIComponent(dashboard);
    return this._fetchSharedCacheURL({
      url,
      errFn,
      anonymizedUrl: '/projects/*/dashboards/*',
    }) as Promise<DashboardInfo | null | undefined>;
  }

  getDocumentationSearches(filter) {
    filter = filter.trim();
    const encodedFilter = encodeURIComponent(filter);

    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: `/Documentation/?q=${encodedFilter}`,
      anonymizedUrl: '/Documentation/?*',
    });
  }

  getMergeable(changeNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/revisions/current/mergeable',
      parseResponse: true,
      reportEndpointAsIs: true,
    });
  }

  deleteDraftComments(query: unknown) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/drafts:delete',
      body: {query},
    });
  }
}
