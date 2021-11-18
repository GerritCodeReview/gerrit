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
  FetchParams,
  FetchPromisesCache,
  GrRestApiHelper,
  SendJSONRequest,
  SendRequest,
  SiteBasedCache,
} from './gr-rest-apis/gr-rest-api-helper';
import {
  GrReviewerUpdatesParser,
  ParsedChangeInfo,
} from './gr-reviewer-updates-parser';
import {parseDate} from '../../../utils/date-util';
import {getBaseUrl} from '../../../utils/url-util';
import {appContext} from '../../../services/app-context';
import {
  getParentIndex,
  isMergeParent,
  patchNumEquals,
} from '../../../utils/patch-set-util';
import {
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../../utils/change-util';
import {assertNever, hasOwnProperty} from '../../../utils/common-util';
import {customElement, property} from '@polymer/decorators';
import {AuthRequestInit, AuthService} from '../../../services/gr-auth/gr-auth';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  AccountExternalIdInfo,
  AccountId,
  AccountInfo,
  AssigneeInput,
  Base64File,
  Base64FileContent,
  Base64ImageFile,
  BranchInfo,
  BranchName,
  ChangeId,
  ChangeInfo,
  ChangeMessageId,
  CommentInfo,
  CommentInput,
  CommitId,
  CommitInfo,
  ConfigInfo,
  ConfigInput,
  DashboardId,
  DashboardInfo,
  DeleteDraftCommentsInput,
  DiffInfo,
  DiffPreferenceInput,
  DiffPreferencesInfo,
  EditPatchSetNum,
  EditPreferencesInfo,
  EncodedGroupId,
  GitRef,
  GpgKeyId,
  GroupId,
  GroupInfo,
  GroupInput,
  GroupOptionsInput,
  HashtagsInput,
  ImagesForDiff,
  NameToProjectInfoMap,
  ParentPatchSetNum,
  ParsedJSON,
  PatchRange,
  PatchSetNum,
  PathToCommentsInfoMap,
  PathToRobotCommentsInfoMap,
  PreferencesInfo,
  PreferencesInput,
  ProjectAccessInfoMap,
  ProjectAccessInput,
  ProjectInfo,
  ProjectInput,
  ProjectWatchInfo,
  RepoName,
  ReviewInput,
  ServerInfo,
  SshKeyInfo,
  UrlEncodedCommentId,
  EditInfo,
  FileNameToFileInfoMap,
  SuggestedReviewerInfo,
  GroupNameToGroupInfoMap,
  GroupAuditEventInfo,
  RequestPayload,
  Password,
  ContributorAgreementInput,
  ContributorAgreementInfo,
  BranchInput,
  IncludedInInfo,
  TagInput,
  PluginInfo,
  GpgKeyInfo,
  GpgKeysInput,
  DocResult,
  EmailInfo,
  ProjectAccessInfo,
  CapabilityInfoMap,
  ProjectInfoWithName,
  TagInfo,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
  NumericChangeId,
  EmailAddress,
  FixId,
  FilePathToDiffInfoMap,
  ChangeViewChangeInfo,
  BlameInfo,
  ActionNameToActionInfoMap,
  RevisionId,
  GroupName,
  Hashtag,
  TopMenuEntryInfo,
  MergeableInfo,
} from '../../../types/common';
import {
  CancelConditionCallback,
  ErrorCallback,
  RestApiService,
  GetDiffCommentsOutput,
  GetDiffRobotCommentsOutput,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {
  CommentSide,
  DiffViewMode,
  HttpMethod,
  IgnoreWhitespaceType,
  ReviewerState,
} from '../../../constants/constants';

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
let pendingRequest: {[promiseName: string]: Array<Promise<unknown>>} = {}; // Shared across instances.
let grEtagDecorator = new GrEtagDecorator(); // Shared across instances.
let projectLookup: {[changeNum: string]: RepoName} = {}; // Shared across instances.

interface FetchChangeJSON {
  reportEndpointAsIs?: boolean;
  endpoint: string;
  anonymizedEndpoint?: string;
  revision?: RevisionId;
  changeNum: NumericChangeId;
  errFn?: ErrorCallback;
  params?: FetchParams;
  fetchOptions?: AuthRequestInit;
  // TODO(TS): The following properties are not used, however some methods
  // set them to true. They should be either changed to reportEndpointAsIs: true
  // or deleted. This should be done carefully case by case.
  reportEndpointAsId?: true;
}

interface SendChangeRequestBase {
  patchNum?: PatchSetNum;
  reportEndpointAsIs?: boolean;
  endpoint: string;
  anonymizedEndpoint?: string;
  changeNum: NumericChangeId;
  method: HttpMethod | undefined;
  errFn?: ErrorCallback;
  headers?: Record<string, string>;
  contentType?: string;
  body?: string | object;

  // TODO(TS): The following properties are not used, however some methods
  // set them to true. They should be either changed to reportEndpointAsIs: true
  // or deleted. This should be done carefully case by case.
  reportUrlAsIs?: true;
  reportEndpointAsId?: true;
}

interface SendRawChangeRequest extends SendChangeRequestBase {
  parseResponse?: false | null;
}

interface SendJSONChangeRequest extends SendChangeRequestBase {
  parseResponse: true;
}

interface QueryChangesParams {
  [paramName: string]: string | undefined | number | string[];
  O?: string; // options
  S: number; // start
  n?: number; // changes per page
  q?: string | string[]; // query/queries
}

interface QueryAccountsParams {
  [paramName: string]: string | undefined | null | number;
  suggest: null;
  q: string;
  n?: number;
}

interface QueryGroupsParams {
  [paramName: string]: string | undefined | null | number;
  s: string;
  n?: number;
  p?: string;
}

interface QuerySuggestedReviewersParams {
  [paramName: string]: string | undefined | null | number;
  n: number;
  q?: string;
  'reviewer-state': ReviewerState;
}

interface GetDiffParams {
  [paramName: string]: string | undefined | null | number | boolean;
  context?: number | 'ALL';
  intraline?: boolean | null;
  whitespace?: IgnoreWhitespaceType;
  parent?: number;
  base?: PatchSetNum;
}

type SendChangeRequest = SendRawChangeRequest | SendJSONChangeRequest;

export function _testOnlyResetGrRestApiSharedObjects() {
  // TODO(TS): The commented code below didn't do anything.
  // It is impossible to reject an existing promise. Should be rewritten in a
  // different way
  // const fetchPromisesCacheData = fetchPromisesCache.testOnlyGetData();
  // for (const key in fetchPromisesCacheData) {
  //   if (hasOwnProperty(fetchPromisesCacheData, key)) {
  //     // reject already fulfilled promise does nothing
  //     fetchPromisesCacheData[key]!.reject();
  //   }
  // }
  //
  // for (const key in pendingRequest) {
  //   if (!hasOwnProperty(pendingRequest, key)) {
  //     continue;
  //   }
  //   for (const req of pendingRequest[key]) {
  //     // reject already fulfilled promise does nothing
  //     req.reject();
  //   }
  // }

  siteBasedCache = new SiteBasedCache();
  fetchPromisesCache = new FetchPromisesCache();
  pendingRequest = {};
  grEtagDecorator = new GrEtagDecorator();
  projectLookup = {};
  appContext.authService.clearCache();
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-rest-api-interface': GrRestApiInterface;
  }
}

@customElement('gr-rest-api-interface')
export class GrRestApiInterface
  extends GestureEventListeners(LegacyElementMixin(PolymerElement))
  implements RestApiService {
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

  // The value is set in created, before any other actions
  private authService: AuthService;

  // The value is set in created, before any other actions
  private readonly _restApiHelper: GrRestApiHelper;

  constructor() {
    super();
    this.authService = appContext.authService;
    this._restApiHelper = new GrRestApiHelper(
      this._cache,
      this.authService,
      this._sharedFetchPromises,
      this
    );
  }

  _fetchSharedCacheURL(req: FetchJSONRequest): Promise<ParsedJSON | undefined> {
    // Cache is shared across instances
    return this._restApiHelper.fetchCacheURL(req);
  }

  getResponseObject(response: Response): Promise<ParsedJSON> {
    return this._restApiHelper.getResponseObject(response);
  }

  getConfig(noCache?: boolean): Promise<ServerInfo | undefined> {
    if (!noCache) {
      return this._fetchSharedCacheURL({
        url: '/config/server/info',
        reportUrlAsIs: true,
      }) as Promise<ServerInfo | undefined>;
    }

    return this._restApiHelper.fetchJSON({
      url: '/config/server/info',
      reportUrlAsIs: true,
    }) as Promise<ServerInfo | undefined>;
  }

  getRepo(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<ProjectInfo | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/projects/' + encodeURIComponent(repo),
      errFn,
      anonymizedUrl: '/projects/*',
    }) as Promise<ProjectInfo | undefined>;
  }

  getProjectConfig(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<ConfigInfo | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/projects/' + encodeURIComponent(repo) + '/config',
      errFn,
      anonymizedUrl: '/projects/*/config',
    }) as Promise<ConfigInfo | undefined>;
  }

  getRepoAccess(repo: RepoName): Promise<ProjectAccessInfoMap | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/access/?project=' + encodeURIComponent(repo),
      anonymizedUrl: '/access/?project=*',
    }) as Promise<ProjectAccessInfoMap | undefined>;
  }

  getRepoDashboards(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo[] | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: `/projects/${encodeURIComponent(repo)}/dashboards?inherited`,
      errFn,
      anonymizedUrl: '/projects/*/dashboards?inherited',
    }) as Promise<DashboardInfo[] | undefined>;
  }

  saveRepoConfig(repo: RepoName, config: ConfigInput): Promise<Response>;

  saveRepoConfig(
    repo: RepoName,
    config: ConfigInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  saveRepoConfig(
    repo: RepoName,
    config: ConfigInput,
    errFn?: ErrorCallback
  ): Promise<Response | undefined> {
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

  runRepoGC(repo: RepoName): Promise<Response>;

  runRepoGC(
    repo: RepoName,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  runRepoGC(repo: RepoName, errFn?: ErrorCallback) {
    if (!repo) {
      // TODO(TS): fix return value
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

  createRepo(config: ProjectInput & {name: RepoName}): Promise<Response>;

  createRepo(
    config: ProjectInput & {name: RepoName},
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  createRepo(config: ProjectInput, errFn?: ErrorCallback) {
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

  createGroup(config: GroupInput & {name: string}): Promise<Response>;

  createGroup(
    config: GroupInput & {name: string},
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  createGroup(config: GroupInput, errFn?: ErrorCallback) {
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
    group: GroupId | GroupName,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeURIComponent(group)}/detail`,
      errFn,
      anonymizedUrl: '/groups/*/detail',
    }) as Promise<GroupInfo | undefined>;
  }

  deleteRepoBranches(repo: RepoName, ref: GitRef): Promise<Response>;

  deleteRepoBranches(
    repo: RepoName,
    ref: GitRef,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  deleteRepoBranches(repo: RepoName, ref: GitRef, errFn?: ErrorCallback) {
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

  deleteRepoTags(repo: RepoName, ref: GitRef): Promise<Response>;

  deleteRepoTags(
    repo: RepoName,
    ref: GitRef,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  deleteRepoTags(repo: RepoName, ref: GitRef, errFn?: ErrorCallback) {
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
    name: RepoName,
    branch: BranchName,
    revision: BranchInput
  ): Promise<Response>;

  createRepoBranch(
    name: RepoName,
    branch: BranchName,
    revision: BranchInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  createRepoBranch(
    name: RepoName,
    branch: BranchName,
    revision: BranchInput,
    errFn?: ErrorCallback
  ) {
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
    name: RepoName,
    tag: string,
    revision: TagInput
  ): Promise<Response>;

  createRepoTag(
    name: RepoName,
    tag: string,
    revision: TagInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  createRepoTag(
    name: RepoName,
    tag: string,
    revision: TagInput,
    errFn?: ErrorCallback
  ) {
    if (!name || !tag || !revision) {
      // TODO(TS): Fix return value
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

  getIsGroupOwner(groupName: GroupName): Promise<boolean> {
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
    groupName: GroupId | GroupName,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | undefined> {
    const encodeName = encodeURIComponent(groupName);
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeName}/members/`,
      errFn,
      anonymizedUrl: '/groups/*/members',
    }) as Promise<AccountInfo[] | undefined>;
  }

  getIncludedGroup(
    groupName: GroupId | GroupName
  ): Promise<GroupInfo[] | undefined> {
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeURIComponent(groupName)}/groups/`,
      anonymizedUrl: '/groups/*/groups',
    }) as Promise<GroupInfo[] | undefined>;
  }

  saveGroupName(groupId: GroupId | GroupName, name: string): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/name`,
      body: {name},
      anonymizedUrl: '/groups/*/name',
    });
  }

  saveGroupOwner(
    groupId: GroupId | GroupName,
    ownerId: string
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/owner`,
      body: {owner: ownerId},
      anonymizedUrl: '/groups/*/owner',
    });
  }

  saveGroupDescription(
    groupId: GroupId | GroupName,
    description: string
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/description`,
      body: {description},
      anonymizedUrl: '/groups/*/description',
    });
  }

  saveGroupOptions(
    groupId: GroupId | GroupName,
    options: GroupOptionsInput
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeId}/options`,
      body: options,
      anonymizedUrl: '/groups/*/options',
    });
  }

  getGroupAuditLog(
    group: EncodedGroupId,
    errFn?: ErrorCallback
  ): Promise<GroupAuditEventInfo[] | undefined> {
    return this._fetchSharedCacheURL({
      url: `/groups/${group}/log.audit`,
      errFn,
      anonymizedUrl: '/groups/*/log.audit',
    }) as Promise<GroupAuditEventInfo[] | undefined>;
  }

  saveGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<AccountInfo> {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(`${groupMember}`);
    return (this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}/members/${encodeMember}`,
      parseResponse: true,
      anonymizedUrl: '/groups/*/members/*',
    }) as unknown) as Promise<AccountInfo>;
  }

  saveIncludedGroup(
    groupName: GroupId | GroupName,
    includedGroup: GroupId,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | undefined> {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    const req = {
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      errFn,
      anonymizedUrl: '/groups/*/groups/*',
    };
    return this._restApiHelper.send(req).then(response => {
      if (response?.ok) {
        return (this.getResponseObject(response) as unknown) as Promise<
          GroupInfo
        >;
      }
      return undefined;
    });
  }

  deleteGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<Response> {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(`${groupMember}`);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/groups/${encodeName}/members/${encodeMember}`,
      anonymizedUrl: '/groups/*/members/*',
    });
  }

  deleteIncludedGroup(
    groupName: GroupId,
    includedGroup: GroupId | GroupName
  ): Promise<Response> {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      anonymizedUrl: '/groups/*/groups/*',
    });
  }

  getVersion(): Promise<string | undefined> {
    return this._fetchSharedCacheURL({
      url: '/config/server/version',
      reportUrlAsIs: true,
    }) as Promise<string | undefined>;
  }

  getDiffPreferences(): Promise<DiffPreferencesInfo | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return this._fetchSharedCacheURL({
          url: '/accounts/self/preferences.diff',
          reportUrlAsIs: true,
        }) as Promise<DiffPreferencesInfo | undefined>;
      }
      const anonymousResult: DiffPreferencesInfo = {
        auto_hide_diff_table_header: true,
        context: 10,
        cursor_blink_rate: 0,
        font_size: 12,
        ignore_whitespace: IgnoreWhitespaceType.IGNORE_NONE,
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

  getEditPreferences(): Promise<EditPreferencesInfo | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return this._fetchSharedCacheURL({
          url: '/accounts/self/preferences.edit',
          reportUrlAsIs: true,
        }) as Promise<EditPreferencesInfo | undefined>;
      }
      const result: EditPreferencesInfo = {
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
      };
      // These defaults should match the defaults in
      // java/com/google/gerrit/extensions/client/EditPreferencesInfo.java
      return Promise.resolve(result);
    });
  }

  savePreferences(prefs: PreferencesInput): Promise<Response>;

  savePreferences(
    prefs: PreferencesInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  savePreferences(prefs: PreferencesInput, errFn?: ErrorCallback) {
    // Note (Issue 5142): normalize the download scheme with lower case before
    // saving.
    if (prefs.download_scheme) {
      prefs.download_scheme = prefs.download_scheme.toLowerCase();
    }

    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences',
      body: prefs,
      errFn,
      reportUrlAsIs: true,
    });
  }

  saveDiffPreferences(prefs: DiffPreferenceInput): Promise<Response>;

  saveDiffPreferences(
    prefs: DiffPreferenceInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  saveDiffPreferences(prefs: DiffPreferenceInput, errFn?: ErrorCallback) {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.diff');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.diff',
      body: prefs,
      errFn,
      reportUrlAsIs: true,
    });
  }

  saveEditPreferences(prefs: EditPreferencesInfo): Promise<Response>;

  saveEditPreferences(
    prefs: EditPreferencesInfo,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  saveEditPreferences(prefs: EditPreferencesInfo, errFn?: ErrorCallback) {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.edit');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.edit',
      body: prefs,
      errFn,
      reportUrlAsIs: true,
    });
  }

  getAccount(): Promise<AccountDetailInfo | undefined> {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/detail',
      reportUrlAsIs: true,
      errFn: resp => {
        if (!resp || resp.status === 403) {
          this._cache.delete('/accounts/self/detail');
        }
      },
    }) as Promise<AccountDetailInfo | undefined>;
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
    }) as Promise<string | undefined>;
  }

  getExternalIds() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/external.ids',
      reportUrlAsIs: true,
    }) as Promise<AccountExternalIdInfo[] | undefined>;
  }

  deleteAccountIdentity(id: string[]) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/external.ids:delete',
      body: id,
      parseResponse: true,
      reportUrlAsIs: true,
    }) as Promise<unknown>;
  }

  getAccountDetails(userId: AccountId): Promise<AccountDetailInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      url: `/accounts/${encodeURIComponent(userId)}/detail`,
      anonymizedUrl: '/accounts/*/detail',
    }) as Promise<AccountDetailInfo | undefined>;
  }

  getAccountEmails() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/emails',
      reportUrlAsIs: true,
    }) as Promise<EmailInfo[] | undefined>;
  }

  addAccountEmail(email: string): Promise<Response>;

  addAccountEmail(
    email: string,
    errFn?: ErrorCallback
  ): Promise<Response | undefined>;

  addAccountEmail(email: string, errFn?: ErrorCallback) {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      errFn,
      anonymizedUrl: '/account/self/emails/*',
    });
  }

  deleteAccountEmail(email: string): Promise<Response>;

  deleteAccountEmail(
    email: string,
    errFn?: ErrorCallback
  ): Promise<Response | undefined>;

  deleteAccountEmail(email: string, errFn?: ErrorCallback) {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      errFn,
      anonymizedUrl: '/accounts/self/email/*',
    });
  }

  setPreferredAccountEmail(
    email: string,
    errFn?: ErrorCallback
  ): Promise<void> {
    // TODO(TS): add correct error handling
    const encodedEmail = encodeURIComponent(email);
    const req = {
      method: HttpMethod.PUT,
      url: `/accounts/self/emails/${encodedEmail}/preferred`,
      errFn,
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

  _updateCachedAccount(obj: Partial<AccountDetailInfo>): void {
    // If result of getAccount is in cache, update it in the cache
    // so we don't have to invalidate it.
    const cachedAccount = this._cache.get('/accounts/self/detail');
    if (cachedAccount) {
      // Replace object in cache with new object to force UI updates.
      this._cache.set('/accounts/self/detail', {...cachedAccount, ...obj});
    }
  }

  setAccountName(name: string, errFn?: ErrorCallback): Promise<void> {
    // TODO(TS): add correct error handling
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
      .then(newName =>
        this._updateCachedAccount({name: (newName as unknown) as string})
      );
  }

  setAccountUsername(username: string, errFn?: ErrorCallback): Promise<void> {
    // TODO(TS): add correct error handling
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
      .then(newName =>
        this._updateCachedAccount({username: (newName as unknown) as string})
      );
  }

  setAccountDisplayName(
    displayName: string,
    errFn?: ErrorCallback
  ): Promise<void> {
    // TODO(TS): add correct error handling
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/displayname',
      body: {display_name: displayName},
      errFn,
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper.send(req).then(newName =>
      this._updateCachedAccount({
        display_name: (newName as unknown) as string,
      })
    );
  }

  setAccountStatus(status: string, errFn?: ErrorCallback): Promise<void> {
    // TODO(TS): add correct error handling
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
      .then(newStatus =>
        this._updateCachedAccount({status: (newStatus as unknown) as string})
      );
  }

  getAccountStatus(userId: AccountId) {
    return this._restApiHelper.fetchJSON({
      url: `/accounts/${encodeURIComponent(userId)}/status`,
      anonymizedUrl: '/accounts/*/status',
    }) as Promise<string | undefined>;
  }

  // https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#list-groups
  getAccountGroups() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/groups',
      reportUrlAsIs: true,
    }) as Promise<GroupInfo[] | undefined>;
  }

  getAccountAgreements() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/agreements',
      reportUrlAsIs: true,
    }) as Promise<ContributorAgreementInfo[] | undefined>;
  }

  saveAccountAgreement(name: ContributorAgreementInput): Promise<Response> {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/agreements',
      body: name,
      reportUrlAsIs: true,
    });
  }

  getAccountCapabilities(
    params?: string[]
  ): Promise<AccountCapabilityInfo | undefined> {
    let queryString = '';
    if (params) {
      queryString =
        '?q=' + params.map(param => encodeURIComponent(param)).join('&q=');
    }
    return this._fetchSharedCacheURL({
      url: '/accounts/self/capabilities' + queryString,
      anonymizedUrl: '/accounts/self/capabilities?q=*',
    }) as Promise<AccountCapabilityInfo | undefined>;
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
          return;
        }
      })
      .then(
        (capabilities: AccountCapabilityInfo | undefined) =>
          capabilities && capabilities.administrateServer
      );
  }

  getDefaultPreferences(): Promise<PreferencesInfo | undefined> {
    return this._fetchSharedCacheURL({
      url: '/config/server/preferences',
      reportUrlAsIs: true,
    }) as Promise<PreferencesInfo | undefined>;
  }

  getPreferences(): Promise<PreferencesInfo | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        const req = {url: '/accounts/self/preferences', reportUrlAsIs: true};
        return this._fetchSharedCacheURL(req).then(res => {
          if (!res) {
            return res;
          }
          const prefInfo = (res as unknown) as PreferencesInfo;
          if (this._isNarrowScreen()) {
            // Note that this can be problematic, because the diff will stay
            // unified even after increasing the window width.
            prefInfo.default_diff_view = DiffViewMode.UNIFIED;
          } else {
            prefInfo.default_diff_view = prefInfo.diff_view;
          }
          return prefInfo;
        });
      }

      // TODO(TS): Many properties are omitted here, but they are required.
      // Add default values for missed properties
      const anonymousPrefs = {
        changes_per_page: 25,
        default_diff_view: this._isNarrowScreen()
          ? DiffViewMode.UNIFIED
          : DiffViewMode.SIDE_BY_SIDE,
        diff_view: DiffViewMode.SIDE_BY_SIDE,
        size_bar_in_change_table: true,
      } as PreferencesInfo;

      return anonymousPrefs;
    });
  }

  getWatchedProjects() {
    return (this._fetchSharedCacheURL({
      url: '/accounts/self/watched.projects',
      reportUrlAsIs: true,
    }) as unknown) as Promise<ProjectWatchInfo[] | undefined>;
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

  deleteWatchedProjects(
    projects: ProjectWatchInfo[]
  ): Promise<Response | undefined>;

  deleteWatchedProjects(
    projects: ProjectWatchInfo[],
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

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

  getChanges(
    changesPerPage?: number,
    query?: string,
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[] | undefined>;

  getChanges(
    changesPerPage?: number,
    query?: string[],
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[][] | undefined>;

  /**
   * @return If opt_query is an
   * array, _fetchJSON will return an array of arrays of changeInfos. If it
   * is unspecified or a string, _fetchJSON will return an array of
   * changeInfos.
   */
  getChanges(
    changesPerPage?: number,
    query?: string | string[],
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[] | ChangeInfo[][] | undefined> {
    return this.getConfig(false)
      .then(config => {
        // TODO(TS): config can be null/undefined. Need some checks
        options = options || this._getChangesOptionsHex(config);
        // Issue 4524: respect legacy token with max sortkey.
        if (offset === 'n,z') {
          offset = 0;
        }
        const params: QueryChangesParams = {
          O: options,
          S: offset || 0,
        };
        if (changesPerPage) {
          params.n = changesPerPage;
        }
        if (query && query.length > 0) {
          params.q = query;
        }
        return {
          url: '/changes/',
          params,
          reportUrlAsIs: true,
        };
      })
      .then(
        req =>
          this._restApiHelper.fetchJSON(req, true) as Promise<
            ChangeInfo[] | ChangeInfo[][] | undefined
          >
      )
      .then(response => {
        if (!response) {
          return;
        }
        const iterateOverChanges = (arr: ChangeInfo[]) => {
          for (const change of arr) {
            this._maybeInsertInLookup(change);
          }
        };
        // Response may be an array of changes OR an array of arrays of
        // changes.
        if (query instanceof Array) {
          // Normalize the response to look like a multi-query response
          // when there is only one query.
          const responseArray: Array<ChangeInfo[]> =
            query.length === 1
              ? [response as ChangeInfo[]]
              : (response as ChangeInfo[][]);
          for (const arr of responseArray) {
            iterateOverChanges(arr);
          }
          return responseArray;
        } else {
          iterateOverChanges(response as ChangeInfo[]);
          return response as ChangeInfo[];
        }
      });
  }

  /**
   * Inserts a change into _projectLookup iff it has a valid structure.
   */
  _maybeInsertInLookup(change: ChangeInfo): void {
    if (change?.project && change._number) {
      this.setInProjectLookup(change._number, change.project);
    }
  }

  getChangeActionURL(
    changeNum: NumericChangeId,
    revisionId: RevisionId | undefined,
    endpoint: string
  ): Promise<string> {
    return this._changeBaseURL(changeNum, revisionId).then(
      url => url + endpoint
    );
  }

  getChangeDetail(
    changeNum: NumericChangeId,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ): Promise<ParsedChangeInfo | null | undefined> {
    return this.getConfig(false).then(config => {
      const optionsHex = this._getChangeOptionsHex(config);
      return this._getChangeDetail(
        changeNum,
        optionsHex,
        errFn,
        cancelCondition
      ).then(detail =>
        // detail has ChangeViewChangeInfo type because the optionsHex always
        // includes ALL_REVISIONS flag.
        GrReviewerUpdatesParser.parse(detail as ChangeViewChangeInfo)
      );
    });
  }

  _getChangesOptionsHex(config?: ServerInfo) {
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
    if (!config?.change?.enable_attention_set) {
      options.push(ListChangesOption.REVIEWED);
    }

    return listChangesOptionsToHex(...options);
  }

  _getChangeOptionsHex(config?: ServerInfo) {
    if (
      window.DEFAULT_DETAIL_HEXES &&
      window.DEFAULT_DETAIL_HEXES.changePage &&
      (!config || !(config.receive && config.receive.enable_signed_push))
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
    if (config?.receive?.enable_signed_push) {
      options.push(ListChangesOption.PUSH_CERTIFICATES);
    }
    return listChangesOptionsToHex(...options);
  }

  getDiffChangeDetail(
    changeNum: NumericChangeId,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ) {
    let optionsHex = '';
    if (window.DEFAULT_DETAIL_HEXES?.diffPage) {
      optionsHex = window.DEFAULT_DETAIL_HEXES.diffPage;
    } else {
      optionsHex = listChangesOptionsToHex(
        ListChangesOption.ALL_COMMITS,
        ListChangesOption.ALL_REVISIONS,
        ListChangesOption.SKIP_DIFFSTAT
      );
    }
    return this._getChangeDetail(changeNum, optionsHex, errFn, cancelCondition);
  }

  /**
   * @param optionsHex list changes options in hex
   */
  _getChangeDetail(
    changeNum: NumericChangeId,
    optionsHex: string,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ): Promise<ChangeInfo | undefined | null> {
    return this.getChangeActionURL(changeNum, undefined, '/detail').then(
      url => {
        const params: FetchParams = {O: optionsHex};
        const urlWithParams = this._restApiHelper.urlWithParams(url, params);
        const req: FetchJSONRequest = {
          url,
          errFn,
          cancelCondition,
          params,
          fetchOptions: this._etags.getOptions(urlWithParams),
          anonymizedUrl: '/changes/*~*/detail?O=' + optionsHex,
        };
        return this._restApiHelper.fetchRawJSON(req).then(response => {
          if (response?.status === 304) {
            return (this._restApiHelper.parsePrefixedJSON(
              // urlWithParams already cached
              this._etags.getCachedPayload(urlWithParams)!
            ) as unknown) as ChangeInfo;
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
            return undefined;
          }

          if (!response) {
            return Promise.resolve(null);
          }

          return this._restApiHelper
            .readResponsePayload(response)
            .then(payload => {
              if (!payload) {
                return null;
              }
              this._etags.collect(urlWithParams, response, payload.raw);
              // TODO(TS): Why it is always change info?
              this._maybeInsertInLookup(
                (payload.parsed as unknown) as ChangeInfo
              );

              return (payload.parsed as unknown) as ChangeInfo;
            });
        });
      }
    );
  }

  getChangeCommitInfo(changeNum: NumericChangeId, patchNum: PatchSetNum) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/commit?links',
      revision: patchNum,
      reportEndpointAsIs: true,
    }) as Promise<CommitInfo | undefined>;
  }

  getChangeFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined> {
    let params = undefined;
    if (isMergeParent(patchRange.basePatchNum)) {
      params = {parent: getParentIndex(patchRange.basePatchNum)};
    } else if (!patchNumEquals(patchRange.basePatchNum, ParentPatchSetNum)) {
      params = {base: patchRange.basePatchNum};
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/files',
      revision: patchRange.patchNum,
      params,
      reportEndpointAsIs: true,
    }) as Promise<FileNameToFileInfoMap | undefined>;
  }

  // TODO(TS): The output type is unclear
  getChangeEditFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<{files: FileNameToFileInfoMap} | undefined> {
    let endpoint = '/edit?list';
    let anonymizedEndpoint = endpoint;
    if (patchRange.basePatchNum !== ParentPatchSetNum) {
      endpoint += '&base=' + encodeURIComponent(`${patchRange.basePatchNum}`);
      anonymizedEndpoint += '&base=*';
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint,
      anonymizedEndpoint,
    }) as Promise<{files: FileNameToFileInfoMap} | undefined>;
  }

  queryChangeFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    query: string
  ) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/files?q=${encodeURIComponent(query)}`,
      revision: patchNum,
      anonymizedEndpoint: '/files?q=*',
    }) as Promise<string[] | undefined>;
  }

  getChangeOrEditFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined> {
    if (patchNumEquals(patchRange.patchNum, EditPatchSetNum)) {
      return this.getChangeEditFiles(changeNum, patchRange).then(
        res => res && res.files
      );
    }
    return this.getChangeFiles(changeNum, patchRange);
  }

  getChangeRevisionActions(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<ActionNameToActionInfoMap | undefined> {
    const req: FetchChangeJSON = {
      changeNum,
      endpoint: '/actions',
      revision: patchNum,
      reportEndpointAsIs: true,
    };
    return this._getChangeURLAndFetch(req) as Promise<
      ActionNameToActionInfoMap | undefined
    >;
  }

  getChangeSuggestedReviewers(
    changeNum: NumericChangeId,
    inputVal: string,
    errFn?: ErrorCallback
  ) {
    return this._getChangeSuggestedGroup(
      ReviewerState.REVIEWER,
      changeNum,
      inputVal,
      errFn
    );
  }

  getChangeSuggestedCCs(
    changeNum: NumericChangeId,
    inputVal: string,
    errFn?: ErrorCallback
  ) {
    return this._getChangeSuggestedGroup(
      ReviewerState.CC,
      changeNum,
      inputVal,
      errFn
    );
  }

  _getChangeSuggestedGroup(
    reviewerState: ReviewerState,
    changeNum: NumericChangeId,
    inputVal: string,
    errFn?: ErrorCallback
  ): Promise<SuggestedReviewerInfo[] | undefined> {
    // More suggestions may obscure content underneath in the reply dialog,
    // see issue 10793.
    const params: QuerySuggestedReviewersParams = {
      n: 6,
      'reviewer-state': reviewerState,
    };
    if (inputVal) {
      params.q = inputVal;
    }
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/suggest_reviewers',
      errFn,
      params,
      reportEndpointAsIs: true,
    }) as Promise<SuggestedReviewerInfo[] | undefined>;
  }

  getChangeIncludedIn(
    changeNum: NumericChangeId
  ): Promise<IncludedInInfo | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/in',
      reportEndpointAsIs: true,
    }) as Promise<IncludedInInfo | undefined>;
  }

  _computeFilter(filter: string) {
    if (filter?.startsWith('^')) {
      filter = '&r=' + encodeURIComponent(filter);
    } else if (filter) {
      filter = '&m=' + encodeURIComponent(filter);
    } else {
      filter = '';
    }
    return filter;
  }

  _getGroupsUrl(filter: string, groupsPerPage: number, offset?: number) {
    offset = offset || 0;

    return (
      `/groups/?n=${groupsPerPage + 1}&S=${offset}` +
      this._computeFilter(filter)
    );
  }

  _getReposUrl(
    filter: string | undefined,
    reposPerPage: number,
    offset?: number
  ) {
    const defaultFilter = 'state:active OR state:read-only';
    const namePartDelimiters = /[@.\-\s/_]/g;
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

  invalidateAccountsDetailCache() {
    this._restApiHelper.invalidateFetchPromisesPrefix('/accounts/self/detail');
  }

  getGroups(filter: string, groupsPerPage: number, offset?: number) {
    const url = this._getGroupsUrl(filter, groupsPerPage, offset);

    return this._fetchSharedCacheURL({
      url,
      anonymizedUrl: '/groups/?*',
    }) as Promise<GroupNameToGroupInfoMap | undefined>;
  }

  getRepos(
    filter: string | undefined,
    reposPerPage: number,
    offset?: number
  ): Promise<ProjectInfoWithName[] | undefined> {
    const url = this._getReposUrl(filter, reposPerPage, offset);

    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url, // The url contains query,so the response is an array, not map
      anonymizedUrl: '/projects/?*',
    }) as Promise<ProjectInfoWithName[] | undefined>;
  }

  setRepoHead(repo: RepoName, ref: GitRef) {
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
    repo: RepoName,
    reposBranchesPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<BranchInfo[] | undefined> {
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
    }) as Promise<BranchInfo[] | undefined>;
  }

  getRepoTags(
    filter: string,
    repo: RepoName,
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
    return (this._restApiHelper.fetchJSON({
      url,
      errFn,
      anonymizedUrl: '/projects/*/tags',
    }) as unknown) as Promise<TagInfo[]>;
  }

  getPlugins(
    filter: string,
    pluginsPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<{[pluginName: string]: PluginInfo} | undefined> {
    offset = offset || 0;
    const encodedFilter = this._computeFilter(filter);
    const n = pluginsPerPage + 1;
    const url = `/plugins/?all&n=${n}&S=${offset}${encodedFilter}`;
    return this._restApiHelper.fetchJSON({
      url,
      errFn,
      anonymizedUrl: '/plugins/?all',
    });
  }

  getRepoAccessRights(
    repoName: RepoName,
    errFn?: ErrorCallback
  ): Promise<ProjectAccessInfo | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._restApiHelper.fetchJSON({
      url: `/projects/${encodeURIComponent(repoName)}/access`,
      errFn,
      anonymizedUrl: '/projects/*/access',
    }) as Promise<ProjectAccessInfo | undefined>;
  }

  setRepoAccessRights(
    repoName: RepoName,
    repoInfo: ProjectAccessInput
  ): Promise<Response> {
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
    projectName: RepoName,
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

  getSuggestedGroups(
    inputVal: string,
    project?: RepoName,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<GroupNameToGroupInfoMap | undefined> {
    const params: QueryGroupsParams = {s: inputVal};
    if (n) {
      params.n = n;
    }
    if (project) {
      params.p = project;
    }
    return this._restApiHelper.fetchJSON({
      url: '/groups/',
      errFn,
      params,
      reportUrlAsIs: true,
    }) as Promise<GroupNameToGroupInfoMap | undefined>;
  }

  getSuggestedProjects(
    inputVal: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<NameToProjectInfoMap | undefined> {
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

  getSuggestedAccounts(
    inputVal: string,
    n?: number,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | undefined> {
    if (!inputVal) {
      return Promise.resolve([]);
    }
    const params: QueryAccountsParams = {suggest: null, q: inputVal};
    if (n) {
      params.n = n;
    }
    return this._restApiHelper.fetchJSON({
      url: '/accounts/',
      errFn,
      params,
      anonymizedUrl: '/accounts/?n=*',
    }) as Promise<AccountInfo[] | undefined>;
  }

  addChangeReviewer(
    changeNum: NumericChangeId,
    reviewerID: AccountId | EmailAddress | GroupId
  ) {
    return this._sendChangeReviewerRequest(
      HttpMethod.POST,
      changeNum,
      reviewerID
    );
  }

  removeChangeReviewer(
    changeNum: NumericChangeId,
    reviewerID: AccountId | EmailAddress | GroupId
  ) {
    return this._sendChangeReviewerRequest(
      HttpMethod.DELETE,
      changeNum,
      reviewerID
    );
  }

  _sendChangeReviewerRequest(
    method: HttpMethod.POST | HttpMethod.DELETE,
    changeNum: NumericChangeId,
    reviewerID: AccountId | EmailAddress | GroupId
  ) {
    return this.getChangeActionURL(changeNum, undefined, '/reviewers').then(
      url => {
        let body;
        switch (method) {
          case HttpMethod.POST:
            body = {reviewer: reviewerID};
            break;
          case HttpMethod.DELETE:
            url += '/' + encodeURIComponent(reviewerID);
            break;
          default:
            assertNever(method, `Unsupported HTTP method: ${method}`);
        }

        return this._restApiHelper.send({method, url, body});
      }
    );
  }

  getRelatedChanges(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<RelatedChangesInfo | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/related',
      revision: patchNum,
      reportEndpointAsIs: true,
    }) as Promise<RelatedChangesInfo | undefined>;
  }

  getChangesSubmittedTogether(
    changeNum: NumericChangeId
  ): Promise<SubmittedTogetherInfo | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/submitted_together?o=NON_VISIBLE_CHANGES',
      reportEndpointAsIs: true,
    }) as Promise<SubmittedTogetherInfo | undefined>;
  }

  getChangeConflicts(
    changeNum: NumericChangeId
  ): Promise<ChangeInfo[] | undefined> {
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
    }) as Promise<ChangeInfo[] | undefined>;
  }

  getChangeCherryPicks(
    project: RepoName,
    changeID: ChangeId,
    changeNum: NumericChangeId
  ): Promise<ChangeInfo[] | undefined> {
    const options = listChangesOptionsToHex(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT
    );
    const query = [
      `project:${project}`,
      `change:${changeID}`,
      `-change:${changeNum}`,
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
    }) as Promise<ChangeInfo[] | undefined>;
  }

  getChangesWithSameTopic(
    topic: string,
    changeNum: NumericChangeId
  ): Promise<ChangeInfo[] | undefined> {
    const options = listChangesOptionsToHex(
      ListChangesOption.LABELS,
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT,
      ListChangesOption.DETAILED_LABELS
    );
    const query = [
      'status:open',
      `-change:${changeNum}`,
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
    }) as Promise<ChangeInfo[] | undefined>;
  }

  getReviewedFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<string[] | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/files?reviewed',
      revision: patchNum,
      reportEndpointAsIs: true,
    }) as Promise<string[] | undefined>;
  }

  saveFileReviewed(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    reviewed: boolean
  ): Promise<Response>;

  saveFileReviewed(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    reviewed: boolean,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  saveFileReviewed(
    changeNum: NumericChangeId,
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
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    review: ReviewInput
  ): Promise<Response>;

  saveChangeReview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    review: ReviewInput,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  saveChangeReview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    review: ReviewInput,
    errFn?: ErrorCallback
  ) {
    const promises: [Promise<void>, Promise<string>] = [
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

  getChangeEdit(
    changeNum: NumericChangeId,
    downloadCommands?: boolean
  ): Promise<false | EditInfo | undefined> {
    const params = downloadCommands ? {'download-commands': true} : undefined;
    return this.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return Promise.resolve(false);
      }
      return this._getChangeURLAndFetch(
        {
          changeNum,
          endpoint: '/edit/',
          params,
          reportEndpointAsIs: true,
        },
        true
      ) as Promise<EditInfo | false | undefined>;
    });
  }

  createChange(
    project: RepoName,
    branch: BranchName,
    subject: string,
    topic?: string,
    isPrivate?: boolean,
    workInProgress?: boolean,
    baseChange?: ChangeId,
    baseCommit?: string
  ) {
    return (this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/changes/',
      body: {
        project,
        branch,
        subject,
        topic,
        is_private: isPrivate,
        work_in_progress: workInProgress,
        base_change: baseChange,
        base_commit: baseCommit,
      },
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown) as Promise<ChangeInfo | undefined>;
  }

  getFileContent(
    changeNum: NumericChangeId,
    path: string,
    patchNum: PatchSetNum
  ): Promise<Response | Base64FileContent | undefined> {
    // 404s indicate the file does not exist yet in the revision, so suppress
    // them.
    const suppress404s: ErrorCallback = res => {
      if (res?.status !== 404) {
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
    const promise = patchNumEquals(patchNum, EditPatchSetNum)
      ? this._getFileInChangeEdit(changeNum, path)
      : this._getFileInRevision(changeNum, path, patchNum, suppress404s);

    return promise.then(res => {
      if (!res || !res.ok) {
        return res;
      }

      // The file type (used for syntax highlighting) is identified in the
      // X-FYI-Content-Type header of the response.
      const type = res.headers.get('X-FYI-Content-Type');
      return this.getResponseObject(res).then(content => {
        const strContent = (content as unknown) as string | null;
        return {content: strContent, type, ok: true};
      });
    });
  }

  /**
   * Gets a file in a specific change and revision.
   */
  _getFileInRevision(
    changeNum: NumericChangeId,
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
  _getFileInChangeEdit(changeNum: NumericChangeId, path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.GET,
      endpoint: '/edit/' + encodeURIComponent(path),
      headers: {Accept: 'application/json'},
      anonymizedEndpoint: '/edit/*',
    });
  }

  rebaseChangeEdit(changeNum: NumericChangeId) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit:rebase',
      reportEndpointAsIs: true,
    });
  }

  deleteChangeEdit(changeNum: NumericChangeId) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/edit',
      reportEndpointAsIs: true,
    });
  }

  restoreFileInChangeEdit(changeNum: NumericChangeId, restore_path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit',
      body: {restore_path},
      reportEndpointAsIs: true,
    });
  }

  renameFileInChangeEdit(
    changeNum: NumericChangeId,
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

  deleteFileInChangeEdit(changeNum: NumericChangeId, path: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/edit/' + encodeURIComponent(path),
      anonymizedEndpoint: '/edit/*',
    });
  }

  saveChangeEdit(changeNum: NumericChangeId, path: string, contents: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit/' + encodeURIComponent(path),
      body: contents,
      contentType: 'text/plain',
      anonymizedEndpoint: '/edit/*',
    });
  }

  saveFileUploadChangeEdit(
    changeNum: NumericChangeId,
    path: string,
    content: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit/' + encodeURIComponent(path),
      body: {binary_content: content},
      anonymizedEndpoint: '/edit/*',
    });
  }

  getRobotCommentFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: FixId
  ): Promise<FilePathToDiffInfoMap | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      revision: patchNum,
      endpoint: `/fixes/${encodeURIComponent(fixId)}/preview`,
      reportEndpointAsId: true,
    }) as Promise<FilePathToDiffInfoMap | undefined>;
  }

  applyFixSuggestion(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: string
  ): Promise<Response> {
    return this._getChangeURLAndSend({
      method: HttpMethod.POST,
      changeNum,
      patchNum,
      endpoint: `/fixes/${encodeURIComponent(fixId)}/apply`,
      reportEndpointAsId: true,
    });
  }

  // Deprecated, prefer to use putChangeCommitMessage instead.
  saveChangeCommitMessageEdit(changeNum: NumericChangeId, message: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/edit:message',
      body: {message},
      reportEndpointAsIs: true,
    });
  }

  publishChangeEdit(changeNum: NumericChangeId) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/edit:publish',
      reportEndpointAsIs: true,
    });
  }

  putChangeCommitMessage(changeNum: NumericChangeId, message: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/message',
      body: {message},
      reportEndpointAsIs: true,
    });
  }

  deleteChangeCommitMessage(
    changeNum: NumericChangeId,
    messageId: ChangeMessageId
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/messages/${messageId}`,
      reportEndpointAsIs: true,
    });
  }

  saveChangeStarred(
    changeNum: NumericChangeId,
    starred: boolean
  ): Promise<Response> {
    // Some servers may require the project name to be provided
    // alongside the change number, so resolve the project name
    // first.
    return this.getFromProjectLookup(changeNum).then(project => {
      const encodedRepoName = project ? encodeURIComponent(project) + '~' : '';
      const url = `/accounts/self/starred.changes/${encodedRepoName}${changeNum}`;
      return this._restApiHelper.send({
        method: starred ? HttpMethod.PUT : HttpMethod.DELETE,
        url,
        anonymizedUrl: '/accounts/self/starred.changes/*',
      });
    });
  }

  saveChangeReviewed(
    changeNum: NumericChangeId,
    reviewed: boolean
  ): Promise<Response | undefined> {
    return this.getConfig().then(config => {
      const isAttentionSetEnabled =
        !!config && !!config.change && config.change.enable_attention_set;
      if (isAttentionSetEnabled) return;
      return this._getChangeURLAndSend({
        changeNum,
        method: HttpMethod.PUT,
        endpoint: reviewed ? '/reviewed' : '/unreviewed',
      });
    });
  }

  send(
    method: HttpMethod,
    url: string,
    body?: RequestPayload,
    errFn?: undefined,
    contentType?: string,
    headers?: Record<string, string>
  ): Promise<Response>;

  send(
    method: HttpMethod,
    url: string,
    body: RequestPayload | undefined,
    errFn: ErrorCallback,
    contentType?: string,
    headers?: Record<string, string>
  ): Promise<Response | undefined>;

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
    body?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string,
    headers?: Record<string, string>
  ): Promise<Response | undefined> {
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
   * @param whitespace the ignore-whitespace level for the diff
   * algorithm.
   */
  getDiff(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string,
    whitespace?: IgnoreWhitespaceType,
    errFn?: ErrorCallback
  ) {
    const params: GetDiffParams = {
      context: 'ALL',
      intraline: null,
      whitespace: whitespace || IgnoreWhitespaceType.IGNORE_NONE,
    };
    if (isMergeParent(basePatchNum)) {
      params.parent = getParentIndex(basePatchNum);
    } else if (!patchNumEquals(basePatchNum, ParentPatchSetNum)) {
      // TODO (TS): fix as PatchSetNum in the condition above
      params.base = basePatchNum;
    }
    const endpoint = `/files/${encodeURIComponent(path)}/diff`;
    const req: FetchChangeJSON = {
      changeNum,
      endpoint,
      revision: patchNum,
      errFn,
      params,
      anonymizedEndpoint: '/files/*/diff',
    };

    // Invalidate the cache if its edit patch to make sure we always get latest.
    if (patchNum === EditPatchSetNum) {
      if (!req.fetchOptions) req.fetchOptions = {};
      if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
      req.fetchOptions.headers.append('Cache-Control', 'no-cache');
    }

    return this._getChangeURLAndFetch(req) as Promise<DiffInfo | undefined>;
  }

  getDiffComments(
    changeNum: NumericChangeId
  ): Promise<PathToCommentsInfoMap | undefined>;

  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffCommentsOutput>;

  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    if (!basePatchNum && !patchNum && !path) {
      return this._getDiffComments(changeNum, '/comments');
    }
    return this._getDiffComments(
      changeNum,
      '/comments',
      basePatchNum,
      patchNum,
      path
    );
  }

  getDiffRobotComments(
    changeNum: NumericChangeId
  ): Promise<PathToRobotCommentsInfoMap | undefined>;

  getDiffRobotComments(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffRobotCommentsOutput>;

  getDiffRobotComments(
    changeNum: NumericChangeId,
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    if (!basePatchNum && !patchNum && !path) {
      return this._getDiffComments(changeNum, '/robotcomments');
    }

    return this._getDiffComments(
      changeNum,
      '/robotcomments',
      basePatchNum,
      patchNum,
      path
    );
  }

  /**
   * If the user is logged in, fetch the user's draft diff comments. If there
   * is no logged in user, the request is not made and the promise yields an
   * empty object.
   */
  getDiffDrafts(
    changeNum: NumericChangeId
  ): Promise<PathToCommentsInfoMap | undefined>;

  getDiffDrafts(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffCommentsOutput>;

  getDiffDrafts(
    changeNum: NumericChangeId,
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    return this.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return {};
      }
      if (!basePatchNum && !patchNum && !path) {
        return this._getDiffComments(changeNum, '/drafts');
      }
      return this._getDiffComments(
        changeNum,
        '/drafts',
        basePatchNum,
        patchNum,
        path
      );
    });
  }

  _setRange(comments: CommentInfo[], comment: CommentInfo) {
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

  _setRanges(comments?: CommentInfo[]) {
    comments = comments || [];
    comments.sort(
      (a, b) => parseDate(a.updated).valueOf() - parseDate(b.updated).valueOf()
    );
    for (const comment of comments) {
      this._setRange(comments, comment);
    }
    return comments;
  }

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/comments' | '/drafts'
  ): Promise<PathToCommentsInfoMap | undefined>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/robotcomments'
  ): Promise<PathToRobotCommentsInfoMap | undefined>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/comments' | '/drafts',
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<GetDiffCommentsOutput>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/robotcomments',
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<GetDiffRobotCommentsOutput>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: string,
    basePatchNum?: PatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<
    | GetDiffCommentsOutput
    | GetDiffRobotCommentsOutput
    | PathToCommentsInfoMap
    | PathToRobotCommentsInfoMap
    | undefined
  > {
    /**
     * Fetches the comments for a given patchNum.
     * Helper function to make promises more legible.
     */
    // We don't want to add accept header, since preloading of comments is
    // working only without accept header.
    const noAcceptHeader = true;
    const fetchComments = (patchNum?: PatchSetNum) =>
      this._getChangeURLAndFetch(
        {
          changeNum,
          endpoint,
          revision: patchNum,
          reportEndpointAsIs: true,
        },
        noAcceptHeader
      ) as Promise<
        PathToCommentsInfoMap | PathToRobotCommentsInfoMap | undefined
      >;

    if (!basePatchNum && !patchNum && !path) {
      return fetchComments();
    }
    function onlyParent(c: CommentInfo) {
      return c.side === CommentSide.PARENT;
    }
    function withoutParent(c: CommentInfo) {
      return c.side !== CommentSide.PARENT;
    }
    function setPath(c: CommentInfo) {
      c.path = path;
    }

    const promises = [];
    let comments: CommentInfo[];
    let baseComments: CommentInfo[];
    let fetchPromise;
    fetchPromise = fetchComments(patchNum).then(response => {
      comments = (response && path && response[path]) || [];
      // TODO(kaspern): Implement this on in the backend so this can
      // be removed.
      // Sort comments by date so that parent ranges can be propagated
      // in a single pass.
      comments = this._setRanges(comments);

      if (basePatchNum === ParentPatchSetNum) {
        baseComments = comments.filter(onlyParent);
        baseComments.forEach(setPath);
      }
      comments = comments.filter(withoutParent);

      comments.forEach(setPath);
    });
    promises.push(fetchPromise);

    if (basePatchNum !== ParentPatchSetNum) {
      fetchPromise = fetchComments(basePatchNum).then(response => {
        baseComments = ((response && path && response[path]) || []).filter(
          withoutParent
        );
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

  _getDiffCommentsFetchURL(
    changeNum: NumericChangeId,
    endpoint: string,
    patchNum?: RevisionId
  ) {
    return this._changeBaseURL(changeNum, patchNum).then(url => url + endpoint);
  }

  saveDiffDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: CommentInput
  ) {
    return this._sendDiffDraftRequest(
      HttpMethod.PUT,
      changeNum,
      patchNum,
      draft
    );
  }

  deleteDiffDraft(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: {id: UrlEncodedCommentId}
  ) {
    return this._sendDiffDraftRequest(
      HttpMethod.DELETE,
      changeNum,
      patchNum,
      draft
    );
  }

  /**
   * @returns Whether there are pending diff draft sends.
   */
  hasPendingDiffDrafts(): number {
    const promises = this._pendingRequests[Requests.SEND_DIFF_DRAFT];
    return promises && promises.length;
  }

  /**
   * @returns A promise that resolves when all pending
   * diff draft sends have resolved.
   */
  awaitPendingDiffDrafts(): Promise<void> {
    return Promise.all(
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] || []
    ).then(() => {
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
    });
  }

  _sendDiffDraftRequest(
    method: HttpMethod.PUT,
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: CommentInput
  ): Promise<Response>;

  _sendDiffDraftRequest(
    method: HttpMethod.GET | HttpMethod.DELETE,
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: {id?: UrlEncodedCommentId}
  ): Promise<Response>;

  _sendDiffDraftRequest(
    method: HttpMethod,
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    draft: CommentInput | {id: UrlEncodedCommentId}
  ): Promise<Response> {
    const isCreate = !draft.id && method === HttpMethod.PUT;
    let endpoint = '/drafts';
    let anonymizedEndpoint = endpoint;
    if (draft.id) {
      endpoint += `/${draft.id}`;
      anonymizedEndpoint += '/*';
    }
    let body;
    if (method === HttpMethod.PUT) {
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
    project: RepoName,
    commit: CommitId
  ): Promise<CommitInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      url:
        '/projects/' +
        encodeURIComponent(project) +
        '/commits/' +
        encodeURIComponent(commit),
      anonymizedUrl: '/projects/*/comments/*',
    }) as Promise<CommitInfo | undefined>;
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
    changeId: NumericChangeId,
    patchNum: RevisionId,
    path: string,
    parentIndex?: number
  ) {
    const parent =
      typeof parentIndex === 'number' ? `?parent=${parentIndex}` : '';
    return this._changeBaseURL(changeId, patchNum).then(url => {
      url = `${url}/files/${encodeURIComponent(path)}/content${parent}`;
      return this._fetchB64File(url);
    });
  }

  getImagesForDiff(
    changeNum: NumericChangeId,
    diff: DiffInfo,
    patchRange: PatchRange
  ): Promise<ImagesForDiff> {
    let promiseA;
    let promiseB;

    if (diff.meta_a?.content_type.startsWith('image/')) {
      if (patchRange.basePatchNum === ParentPatchSetNum) {
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

    if (diff.meta_b?.content_type.startsWith('image/')) {
      promiseB = this.getB64FileContents(
        changeNum,
        patchRange.patchNum,
        diff.meta_b.name
      );
    } else {
      promiseB = Promise.resolve(null);
    }

    return Promise.all([promiseA, promiseB]).then(results => {
      // Sometimes the server doesn't send back the content type.
      const baseImage: Base64ImageFile | null = results[0]
        ? {
            ...results[0],
            _expectedType: diff.meta_a.content_type,
            _name: diff.meta_a.name,
          }
        : null;
      const revisionImage: Base64ImageFile | null = results[1]
        ? {
            ...results[1],
            _expectedType: diff.meta_b.content_type,
            _name: diff.meta_b.name,
          }
        : null;
      const imagesForDiff: ImagesForDiff = {baseImage, revisionImage};
      return imagesForDiff;
    });
  }

  _changeBaseURL(
    changeNum: NumericChangeId,
    revisionId?: RevisionId,
    project?: RepoName
  ): Promise<string> {
    // TODO(kaspern): For full slicer migration, app should warn with a call
    // stack every time _changeBaseURL is called without a project.
    const projectPromise = project
      ? Promise.resolve(project)
      : this.getFromProjectLookup(changeNum);
    return projectPromise.then(project => {
      // TODO(TS): unclear why project can't be null here. Fix it
      let url = `/changes/${encodeURIComponent(
        project as RepoName
      )}~${changeNum}`;
      if (revisionId) {
        url += `/revisions/${revisionId}`;
      }
      return url;
    });
  }

  addToAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId | undefined | null,
    reason: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/attention',
      body: {user, reason},
      reportUrlAsIs: true,
    });
  }

  removeFromAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId,
    reason: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/attention/${user}`,
      anonymizedEndpoint: '/attention/*',
      body: {reason},
    });
  }

  setChangeTopic(
    changeNum: NumericChangeId,
    topic: string | null
  ): Promise<string> {
    return (this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/topic',
      body: {topic},
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown) as Promise<string>;
  }

  setChangeHashtag(
    changeNum: NumericChangeId,
    hashtag: HashtagsInput
  ): Promise<Hashtag[]> {
    return (this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/hashtags',
      body: hashtag,
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown) as Promise<Hashtag[]>;
  }

  deleteAccountHttpPassword() {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/password.http',
      reportUrlAsIs: true,
    });
  }

  generateAccountHttpPassword(): Promise<Password> {
    return (this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/password.http',
      body: {generate: true},
      parseResponse: true,
      reportUrlAsIs: true,
    }) as Promise<unknown>) as Promise<Password>;
  }

  getAccountSSHKeys() {
    return (this._fetchSharedCacheURL({
      url: '/accounts/self/sshkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown>) as Promise<SshKeyInfo[] | undefined>;
  }

  addAccountSSHKey(key: string): Promise<SshKeyInfo> {
    const req = {
      method: HttpMethod.POST,
      url: '/accounts/self/sshkeys',
      body: key,
      contentType: 'text/plain',
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then((response: Response | undefined) => {
        if (!response || (response.status < 200 && response.status >= 300)) {
          return Promise.reject(new Error('error'));
        }
        return (this.getResponseObject(response) as unknown) as Promise<
          SshKeyInfo
        >;
      })
      .then(obj => {
        if (!obj || !obj.valid) {
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
    return (this._restApiHelper.fetchJSON({
      url: '/accounts/self/gpgkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown>) as Promise<Record<string, GpgKeyInfo>>;
  }

  addAccountGPGKey(key: GpgKeysInput) {
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
      url: `/accounts/self/gpgkeys/${id}`,
      anonymizedUrl: '/accounts/self/gpgkeys/*',
    });
  }

  deleteVote(changeNum: NumericChangeId, account: AccountId, label: string) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: `/reviewers/${account}/votes/${encodeURIComponent(label)}`,
      anonymizedEndpoint: '/reviewers/*/votes/*',
    });
  }

  setDescription(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    desc: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      patchNum,
      endpoint: '/description',
      body: {description: desc},
      reportUrlAsIs: true,
    });
  }

  confirmEmail(token: string): Promise<string | null> {
    const req = {
      method: HttpMethod.PUT,
      url: '/config/server/email.confirm',
      body: {token},
      reportUrlAsIs: true,
    };
    return this._restApiHelper.send(req).then(response => {
      if (response?.status === 204) {
        return 'Email confirmed successfully.';
      }
      return null;
    });
  }

  getCapabilities(
    errFn?: ErrorCallback
  ): Promise<CapabilityInfoMap | undefined> {
    return this._restApiHelper.fetchJSON({
      url: '/config/server/capabilities',
      errFn,
      reportUrlAsIs: true,
    }) as Promise<CapabilityInfoMap | undefined>;
  }

  getTopMenus(errFn?: ErrorCallback): Promise<TopMenuEntryInfo[] | undefined> {
    return this._fetchSharedCacheURL({
      url: '/config/server/top-menus',
      errFn,
      reportUrlAsIs: true,
    }) as Promise<TopMenuEntryInfo[] | undefined>;
  }

  setAssignee(
    changeNum: NumericChangeId,
    assignee: AccountId
  ): Promise<Response> {
    const body: AssigneeInput = {assignee};
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/assignee',
      body,
      reportUrlAsIs: true,
    });
  }

  deleteAssignee(changeNum: NumericChangeId): Promise<Response> {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.DELETE,
      endpoint: '/assignee',
      reportUrlAsIs: true,
    });
  }

  probePath(path: string) {
    return fetch(new Request(path, {method: HttpMethod.HEAD})).then(
      response => response.ok
    );
  }

  startWorkInProgress(
    changeNum: NumericChangeId,
    message?: string
  ): Promise<string | undefined> {
    const body = message ? {message} : {};
    const req: SendRawChangeRequest = {
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/wip',
      body,
      reportUrlAsIs: true,
    };
    return this._getChangeURLAndSend(req).then(response => {
      if (response?.status === 204) {
        return 'Change marked as Work In Progress.';
      }
      return undefined;
    });
  }

  startReview(
    changeNum: NumericChangeId,
    body?: RequestPayload,
    errFn?: ErrorCallback
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/ready',
      body,
      errFn,
      reportUrlAsIs: true,
    });
  }

  deleteComment(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    commentID: UrlEncodedCommentId,
    reason: string
  ) {
    return (this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      patchNum,
      endpoint: `/comments/${commentID}/delete`,
      body: {reason},
      parseResponse: true,
      anonymizedEndpoint: '/comments/*/delete',
    }) as unknown) as Promise<CommentInfo>;
  }

  /**
   * Given a changeNum, gets the change.
   */
  getChange(
    changeNum: ChangeId | NumericChangeId,
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
        const changeInfos = res as ChangeInfo[] | undefined;
        if (!changeInfos || !changeInfos.length) {
          return null;
        }
        return changeInfos[0];
      });
  }

  setInProjectLookup(changeNum: NumericChangeId, project: RepoName) {
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
  getFromProjectLookup(
    changeNum: NumericChangeId
  ): Promise<RepoName | undefined> {
    const project = this._projectLookup[`${changeNum}`];
    if (project) {
      return Promise.resolve(project);
    }

    const onError = (response?: Response | null) => {
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

  // if errFn is not set, then only Response possible
  _getChangeURLAndSend(
    req: SendRawChangeRequest & {errFn?: undefined}
  ): Promise<Response>;

  _getChangeURLAndSend(
    req: SendRawChangeRequest
  ): Promise<Response | undefined>;

  _getChangeURLAndSend(req: SendJSONChangeRequest): Promise<ParsedJSON>;

  /**
   * Alias for _changeBaseURL.then(send).
   */
  _getChangeURLAndSend(
    req: SendChangeRequest
  ): Promise<ParsedJSON | Response | undefined> {
    const anonymizedBaseUrl = req.patchNum
      ? ANONYMIZED_REVISION_BASE_URL
      : ANONYMIZED_CHANGE_BASE_URL;
    const anonymizedEndpoint = req.reportEndpointAsIs
      ? req.endpoint
      : req.anonymizedEndpoint;

    return this._changeBaseURL(req.changeNum, req.patchNum).then(url => {
      const request: SendRequest = {
        method: req.method,
        url: url + req.endpoint,
        body: req.body,
        errFn: req.errFn,
        contentType: req.contentType,
        headers: req.headers,
        parseResponse: req.parseResponse,
        anonymizedUrl: anonymizedEndpoint
          ? `${anonymizedBaseUrl}${anonymizedEndpoint}`
          : undefined,
      };
      return this._restApiHelper.send(request);
    });
  }

  /**
   * Alias for _changeBaseURL.then(_fetchJSON).
   */
  _getChangeURLAndFetch(
    req: FetchChangeJSON,
    noAcceptHeader?: boolean
  ): Promise<ParsedJSON | undefined> {
    const anonymizedEndpoint = req.reportEndpointAsIs
      ? req.endpoint
      : req.anonymizedEndpoint;
    const anonymizedBaseUrl = req.revision
      ? ANONYMIZED_REVISION_BASE_URL
      : ANONYMIZED_CHANGE_BASE_URL;
    return this._changeBaseURL(req.changeNum, req.revision).then(url =>
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

  executeChangeAction(
    changeNum: NumericChangeId,
    method: HttpMethod | undefined,
    endpoint: string,
    patchNum?: PatchSetNum,
    payload?: RequestPayload
  ): Promise<Response>;

  executeChangeAction(
    changeNum: NumericChangeId,
    method: HttpMethod | undefined,
    endpoint: string,
    patchNum: PatchSetNum | undefined,
    payload: RequestPayload | undefined,
    errFn: ErrorCallback
  ): Promise<Response | undefined>;

  /**
   * Execute a change action or revision action on a change.
   */
  executeChangeAction(
    changeNum: NumericChangeId,
    method: HttpMethod | undefined,
    endpoint: string,
    patchNum?: PatchSetNum,
    payload?: RequestPayload,
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
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    base?: boolean
  ) {
    const encodedPath = encodeURIComponent(path);
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/files/${encodedPath}/blame`,
      revision: patchNum,
      params: base ? {base: 't'} : undefined,
      anonymizedEndpoint: '/files/*/blame',
    }) as Promise<BlameInfo[] | undefined>;
  }

  /**
   * Modify the given create draft request promise so that it fails and throws
   * an error if the response bears HTTP status 200 instead of HTTP 201.
   *
   * @see Issue 7763
   * @param promise The original promise.
   * @return The modified promise.
   */
  _failForCreate200(promise: Promise<Response>): Promise<Response> {
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
          {} as Record<string, string>
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
    project: RepoName,
    dashboard: DashboardId,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo | undefined> {
    const url =
      '/projects/' +
      encodeURIComponent(project) +
      '/dashboards/' +
      encodeURIComponent(dashboard);
    return this._fetchSharedCacheURL({
      url,
      errFn,
      anonymizedUrl: '/projects/*/dashboards/*',
    }) as Promise<DashboardInfo | undefined>;
  }

  getDocumentationSearches(filter: string): Promise<DocResult[] | undefined> {
    filter = filter.trim();
    const encodedFilter = encodeURIComponent(filter);

    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: `/Documentation/?q=${encodedFilter}`,
      anonymizedUrl: '/Documentation/?*',
    }) as Promise<DocResult[] | undefined>;
  }

  getMergeable(changeNum: NumericChangeId) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/revisions/current/mergeable',
      reportEndpointAsIs: true,
    }) as Promise<MergeableInfo | undefined>;
  }

  deleteDraftComments(query: string): Promise<Response> {
    const body: DeleteDraftCommentsInput = {query};
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/drafts:delete',
      body,
    });
  }
}
