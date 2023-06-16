/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/* NB: Order is important, because of namespaced classes. */

import {GrEtagDecorator} from '../../elements/shared/gr-rest-api-interface/gr-etag-decorator';
import {
  FetchJSONRequest,
  FetchParams,
  FetchPromisesCache,
  GrRestApiHelper,
  parsePrefixedJSON,
  readResponsePayload,
  SendJSONRequest,
  SendRequest,
  SiteBasedCache,
} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {parseDate} from '../../utils/date-util';
import {getBaseUrl} from '../../utils/url-util';
import {Finalizable} from '../registry';
import {getParentIndex, isMergeParent} from '../../utils/patch-set-util';
import {
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../utils/change-util';
import {assertNever, hasOwnProperty} from '../../utils/common-util';
import {AuthService} from '../gr-auth/gr-auth';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  AccountExternalIdInfo,
  AccountId,
  AccountInfo,
  ActionNameToActionInfoMap,
  Base64File,
  Base64FileContent,
  Base64ImageFile,
  BasePatchSetNum,
  BlameInfo,
  BranchInfo,
  BranchInput,
  BranchName,
  CapabilityInfoMap,
  ChangeId,
  ChangeInfo,
  ChangeMessageId,
  ChangeViewChangeInfo,
  CommentInfo,
  CommentInput,
  CommitId,
  CommitInfo,
  ConfigInfo,
  ConfigInput,
  ContributorAgreementInfo,
  ContributorAgreementInput,
  DashboardId,
  DashboardInfo,
  DeleteDraftCommentsInput,
  DiffPreferenceInput,
  DocResult,
  EditInfo,
  EDIT,
  EditPreferencesInfo,
  EmailAddress,
  EmailInfo,
  EncodedGroupId,
  FileNameToFileInfoMap,
  FilePathToDiffInfoMap,
  FixId,
  GitRef,
  GpgKeyId,
  GpgKeyInfo,
  GpgKeysInput,
  GroupAuditEventInfo,
  GroupId,
  GroupInfo,
  GroupInput,
  GroupName,
  GroupNameToGroupInfoMap,
  GroupOptionsInput,
  Hashtag,
  HashtagsInput,
  ImagesForDiff,
  IncludedInInfo,
  MergeableInfo,
  NameToProjectInfoMap,
  NumericChangeId,
  PARENT,
  ParsedJSON,
  Password,
  PatchRange,
  PatchSetNum,
  PathToRobotCommentsInfoMap,
  PluginInfo,
  PreferencesInfo,
  PreferencesInput,
  ProjectAccessInfo,
  RepoAccessInfoMap,
  ProjectAccessInput,
  ProjectInfo,
  ProjectInfoWithName,
  ProjectInput,
  ProjectWatchInfo,
  RelatedChangesInfo,
  RepoName,
  RequestPayload,
  ReviewInput,
  RevisionId,
  ServerInfo,
  SshKeyInfo,
  SubmittedTogetherInfo,
  SuggestedReviewerInfo,
  TagInfo,
  TagInput,
  TopMenuEntryInfo,
  UrlEncodedCommentId,
  FixReplacementInfo,
  DraftInfo,
} from '../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  IgnoreWhitespaceType,
} from '../../types/diff';
import {
  CancelConditionCallback,
  GetDiffCommentsOutput,
  GetDiffRobotCommentsOutput,
  RestApiService,
} from './gr-rest-api';
import {
  CommentSide,
  createDefaultDiffPrefs,
  createDefaultEditPrefs,
  createDefaultPreferences,
  HttpMethod,
  ReviewerState,
} from '../../constants/constants';
import {firePageError, fireServerError} from '../../utils/event-util';
import {AuthRequestInit, ParsedChangeInfo} from '../../types/types';
import {ErrorCallback} from '../../api/rest';
import {addDraftProp} from '../../utils/comment-util';
import {BaseScheduler, Scheduler} from '../scheduler/scheduler';
import {MaxInFlightScheduler} from '../scheduler/max-in-flight-scheduler';
import {escapeAndWrapSearchOperatorValue} from '../../utils/string-util';

const MAX_PROJECT_RESULTS = 25;
export const PROBE_PATH = '/Documentation/index.html';
export const DOCS_BASE_PATH = '/Documentation';

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
let projectLookup: {[changeNum: string]: Promise<RepoName | undefined>} = {}; // Shared across instances.

function suppress404s(res?: Response | null) {
  if (!res || res.status === 404) return;
  // This is the default error handling behavior of the rest-api-helper.
  fireServerError(res);
}

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
  q: string;
  n?: number;
  o?: string;
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
  intraline?: boolean | null;
  whitespace?: IgnoreWhitespaceType;
  parent?: number;
  base?: PatchSetNum;
}

type SendChangeRequest = SendRawChangeRequest | SendJSONChangeRequest;

export function testOnlyResetGrRestApiSharedObjects(authService: AuthService) {
  siteBasedCache = new SiteBasedCache();
  fetchPromisesCache = new FetchPromisesCache();
  pendingRequest = {};
  grEtagDecorator = new GrEtagDecorator();
  projectLookup = {};
  authService.clearCache();
}

function createReadScheduler() {
  return new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 10);
}

function createWriteScheduler() {
  return new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 5);
}

function createSerializingScheduler() {
  return new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 1);
}

export class GrRestApiServiceImpl implements RestApiService, Finalizable {
  readonly _cache = siteBasedCache; // Shared across instances.

  readonly _sharedFetchPromises = fetchPromisesCache; // Shared across instances.

  readonly _pendingRequests = pendingRequest; // Shared across instances.

  readonly _etags = grEtagDecorator; // Shared across instances.

  getDocsBaseUrlCachedPromise: Promise<string | null> | undefined;

  // readonly, but set in tests.
  _projectLookup = projectLookup; // Shared across instances.

  // The value is set in created, before any other actions
  // Private, but used in tests.
  readonly _restApiHelper: GrRestApiHelper;

  // Used to serialize requests for certain RPCs
  readonly _serialScheduler: Scheduler<Response>;

  constructor(private readonly authService: AuthService) {
    this._restApiHelper = new GrRestApiHelper(
      this._cache,
      this.authService,
      this._sharedFetchPromises,
      createReadScheduler(),
      createWriteScheduler()
    );
    this._serialScheduler = createSerializingScheduler();
  }

  finalize() {}

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

  getRepoAccess(repo: RepoName): Promise<RepoAccessInfoMap | undefined> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    return this._fetchSharedCacheURL({
      url: '/access/?project=' + encodeURIComponent(repo),
      anonymizedUrl: '/access/?project=*',
    }) as Promise<RepoAccessInfoMap | undefined>;
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

  saveRepoConfig(repo: RepoName, config: ConfigInput): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const url = `/projects/${encodeURIComponent(repo)}/config`;
    this._cache.delete(url);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url,
      body: config,
      anonymizedUrl: '/projects/*/config',
    });
  }

  runRepoGC(repo: RepoName): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: `/projects/${encodeName}/gc`,
      body: '',
      anonymizedUrl: '/projects/*/gc',
    });
  }

  createRepo(config: ProjectInput & {name: RepoName}): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}`,
      body: config,
      anonymizedUrl: '/projects/*',
    });
  }

  createGroup(config: GroupInput & {name: string}): Promise<Response> {
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}`,
      body: config,
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

  deleteRepoBranches(repo: RepoName, ref: GitRef): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/projects/${encodeName}/branches/${encodeRef}`,
      body: '',
      anonymizedUrl: '/projects/*/branches/*',
    });
  }

  deleteRepoTags(repo: RepoName, ref: GitRef): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: `/projects/${encodeName}/tags/${encodeRef}`,
      body: '',
      anonymizedUrl: '/projects/*/tags/*',
    });
  }

  createRepoBranch(
    name: RepoName,
    branch: BranchName,
    revision: BranchInput
  ): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(name);
    const encodeBranch = encodeURIComponent(branch);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}/branches/${encodeBranch}`,
      body: revision,
      anonymizedUrl: '/projects/*/branches/*',
    });
  }

  createRepoTag(
    name: RepoName,
    tag: string,
    revision: TagInput
  ): Promise<Response> {
    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.
    const encodeName = encodeURIComponent(name);
    const encodeTag = encodeURIComponent(tag);
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeName}/tags/${encodeTag}`,
      body: revision,
      anonymizedUrl: '/projects/*/tags/*',
    });
  }

  getIsGroupOwner(groupName?: GroupName): Promise<boolean> {
    if (!groupName) return Promise.resolve(false);
    const encodeName = encodeURIComponent(groupName);
    const req = {
      url: `/groups/?owned&g=${encodeName}`,
      anonymizedUrl: '/groups/owned&g=*',
    };
    return this._fetchSharedCacheURL(req).then(configs =>
      hasOwnProperty(configs, groupName)
    );
  }

  getGroupMembers(groupName: GroupId | GroupName): Promise<AccountInfo[]> {
    const encodeName = encodeURIComponent(groupName);
    return this._restApiHelper.fetchJSON({
      url: `/groups/${encodeName}/members/`,
      anonymizedUrl: '/groups/*/members',
    }) as unknown as Promise<AccountInfo[]>;
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
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/groups/${encodeName}/members/${encodeMember}`,
      parseResponse: true,
      anonymizedUrl: '/groups/*/members/*',
    }) as unknown as Promise<AccountInfo>;
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
        return this.getResponseObject(
          response
        ) as unknown as Promise<GroupInfo>;
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
      return Promise.resolve(createDefaultDiffPrefs());
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
      return Promise.resolve(createDefaultEditPrefs());
    });
  }

  savePreferences(
    prefs: PreferencesInput
  ): Promise<PreferencesInfo | undefined> {
    // Note (Issue 5142): normalize the download scheme with lower case before
    // saving.
    if (prefs.download_scheme) {
      prefs.download_scheme = prefs.download_scheme.toLowerCase();
    }

    return this._restApiHelper
      .send({
        method: HttpMethod.PUT,
        url: '/accounts/self/preferences',
        body: prefs,
        reportUrlAsIs: true,
      })
      .then((response: Response) =>
        this.getResponseObject(response).then(
          obj => obj as unknown as PreferencesInfo
        )
      );
  }

  saveDiffPreferences(prefs: DiffPreferenceInput): Promise<Response> {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.diff');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.diff',
      body: prefs,
      reportUrlAsIs: true,
    });
  }

  saveEditPreferences(prefs: EditPreferencesInfo): Promise<Response> {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.edit');
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/preferences.edit',
      body: prefs,
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

  getAccountDetails(
    userId: AccountId | EmailAddress,
    errFn?: ErrorCallback
  ): Promise<AccountDetailInfo | undefined> {
    return this._fetchSharedCacheURL({
      url: `/accounts/${encodeURIComponent(userId)}/detail`,
      anonymizedUrl: '/accounts/*/detail',
      errFn,
    }) as Promise<AccountDetailInfo | undefined>;
  }

  getAccountEmails() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/emails',
      reportUrlAsIs: true,
    }) as Promise<EmailInfo[] | undefined>;
  }

  addAccountEmail(email: string): Promise<Response> {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      anonymizedUrl: '/account/self/emails/*',
    });
  }

  deleteAccountEmail(email: string): Promise<Response> {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      anonymizedUrl: '/accounts/self/email/*',
    });
  }

  setPreferredAccountEmail(email: string): Promise<void> {
    // TODO(TS): add correct error handling
    const encodedEmail = encodeURIComponent(email);
    const req = {
      method: HttpMethod.PUT,
      url: `/accounts/self/emails/${encodedEmail}/preferred`,
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

  setAccountName(name: string): Promise<void> {
    // TODO(TS): add correct error handling
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/name',
      body: {name},
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newName =>
        this._updateCachedAccount({name: newName as unknown as string})
      );
  }

  setAccountUsername(username: string): Promise<void> {
    // TODO(TS): add correct error handling
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/username',
      body: {username},
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newName =>
        this._updateCachedAccount({username: newName as unknown as string})
      );
  }

  setAccountDisplayName(displayName: string): Promise<void> {
    // TODO(TS): add correct error handling
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/displayname',
      body: {display_name: displayName},
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper.send(req).then(newName =>
      this._updateCachedAccount({
        display_name: newName as unknown as string,
      })
    );
  }

  setAccountStatus(status: string): Promise<void> {
    // TODO(TS): add correct error handling
    const req: SendJSONRequest = {
      method: HttpMethod.PUT,
      url: '/accounts/self/status',
      body: {status},
      parseResponse: true,
      reportUrlAsIs: true,
    };
    return this._restApiHelper
      .send(req)
      .then(newStatus =>
        this._updateCachedAccount({status: newStatus as unknown as string})
      );
  }

  getAccountStatus(userId: AccountId) {
    return this._restApiHelper.fetchJSON({
      url: `/accounts/${encodeURIComponent(userId)}/status`,
      anonymizedUrl: '/accounts/*/status',
    }) as Promise<string | undefined>;
  }

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
          const prefInfo = res as unknown as PreferencesInfo;
          return prefInfo;
        });
      }
      return createDefaultPreferences();
    });
  }

  getWatchedProjects() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/watched.projects',
      reportUrlAsIs: true,
    }) as unknown as Promise<ProjectWatchInfo[] | undefined>;
  }

  saveWatchedProjects(
    projects: ProjectWatchInfo[]
  ): Promise<ProjectWatchInfo[]> {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/watched.projects',
      body: projects,
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown as Promise<ProjectWatchInfo[]>;
  }

  deleteWatchedProjects(projects: ProjectWatchInfo[]): Promise<Response> {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/accounts/self/watched.projects:delete',
      body: projects,
      reportUrlAsIs: true,
    });
  }

  getRequestForGetChanges(
    changesPerPage?: number,
    query?: string[] | string,
    offset?: 'n,z' | number,
    options?: string
  ) {
    options = options || this._getChangesOptionsHex();
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
    const request = {
      url: '/changes/',
      params,
      reportUrlAsIs: true,
    };
    return request;
  }

  getChangesForMultipleQueries(
    changesPerPage?: number,
    query?: string[],
    offset?: 'n,z' | number,
    options?: string
  ): Promise<ChangeInfo[][] | undefined> {
    if (!query) return Promise.resolve(undefined);

    const request = this.getRequestForGetChanges(
      changesPerPage,
      query,
      offset,
      options
    );

    return Promise.resolve(
      this._restApiHelper.fetchJSON(request, true) as Promise<
        ChangeInfo[] | ChangeInfo[][] | undefined
      >
    ).then(response => {
      if (!response) {
        return;
      }
      const iterateOverChanges = (arr: ChangeInfo[]) => {
        for (const change of arr) {
          this._maybeInsertInLookup(change);
        }
      };
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
    });
  }

  getChanges(
    changesPerPage?: number,
    query?: string,
    offset?: 'n,z' | number,
    options?: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined> {
    const request = this.getRequestForGetChanges(
      changesPerPage,
      query,
      offset,
      options
    );

    return Promise.resolve(
      this._restApiHelper.fetchJSON(
        {
          ...request,
          errFn,
        },
        true
      ) as Promise<ChangeInfo[] | undefined>
    ).then(response => {
      if (!response) {
        return;
      }
      const iterateOverChanges = (arr: ChangeInfo[]) => {
        for (const change of arr) {
          this._maybeInsertInLookup(change);
        }
      };
      iterateOverChanges(response);
      return response;
    });
  }

  async getDetailedChangesWithActions(changeNums: NumericChangeId[]) {
    const query = changeNums.map(num => `change:${num}`).join(' OR ');
    const changeDetails = await this.getChanges(
      undefined,
      query,
      undefined,
      listChangesOptionsToHex(
        ListChangesOption.CHANGE_ACTIONS,
        ListChangesOption.CURRENT_ACTIONS,
        ListChangesOption.CURRENT_REVISION,
        ListChangesOption.DETAILED_LABELS,
        // TODO: remove this option and merge requirements from dashboard req
        ListChangesOption.SUBMIT_REQUIREMENTS
      )
    );
    return changeDetails;
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
    changeNum?: NumericChangeId,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ): Promise<ParsedChangeInfo | undefined> {
    if (!changeNum) return Promise.resolve(undefined);
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

  _getChangesOptionsHex() {
    if (
      window.DEFAULT_DETAIL_HEXES &&
      window.DEFAULT_DETAIL_HEXES.dashboardPage
    ) {
      return window.DEFAULT_DETAIL_HEXES.dashboardPage;
    }
    const options = [
      ListChangesOption.LABELS,
      ListChangesOption.DETAILED_ACCOUNTS,
      ListChangesOption.SUBMIT_REQUIREMENTS,
    ];

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
      ListChangesOption.SUBMIT_REQUIREMENTS,
    ];
    if (config?.receive?.enable_signed_push) {
      options.push(ListChangesOption.PUSH_CERTIFICATES);
    }
    return listChangesOptionsToHex(...options);
  }

  /**
   * @param optionsHex list changes options in hex
   */
  _getChangeDetail(
    changeNum: NumericChangeId,
    optionsHex: string,
    errFn?: ErrorCallback,
    cancelCondition?: CancelConditionCallback
  ): Promise<ChangeInfo | undefined> {
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
            return parsePrefixedJSON(
              // urlWithParams already cached
              this._etags.getCachedPayload(urlWithParams)!
            ) as unknown as ChangeInfo;
          }

          if (response && !response.ok) {
            if (errFn) {
              errFn.call(null, response);
            } else {
              fireServerError(response, req);
            }
            return undefined;
          }

          if (!response) {
            return Promise.resolve(undefined);
          }

          return readResponsePayload(response).then(payload => {
            if (!payload) {
              return undefined;
            }
            this._etags.collect(urlWithParams, response, payload.raw);
            // TODO(TS): Why it is always change info?
            this._maybeInsertInLookup(payload.parsed as unknown as ChangeInfo);

            return payload.parsed as unknown as ChangeInfo;
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
      errFn: suppress404s,
    }) as Promise<CommitInfo | undefined>;
  }

  getChangeFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined> {
    let params = undefined;
    if (isMergeParent(patchRange.basePatchNum)) {
      params = {parent: getParentIndex(patchRange.basePatchNum)};
    } else if (patchRange.basePatchNum !== PARENT) {
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
    if (patchRange.basePatchNum !== PARENT) {
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
    query: string,
    errFn?: ErrorCallback
  ) {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/files?q=${encodeURIComponent(query)}`,
      revision: patchNum,
      anonymizedEndpoint: '/files?q=*',
      errFn,
    }) as Promise<string[] | undefined>;
  }

  getChangeOrEditFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined> {
    if (patchRange.patchNum === EDIT) {
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
      params,
      reportEndpointAsIs: true,
      errFn,
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
  ): [boolean, string] {
    const defaultFilter = '';
    offset = offset || 0;
    filter ??= defaultFilter;
    const encodedFilter = encodeURIComponent(filter);

    if (filter.includes(':')) {
      // If the filter includes a semicolon, the user is using a more complex
      // query so we trust them and don't do any magic under the hood.
      return [
        true,
        `/projects/?n=${reposPerPage + 1}&S=${offset}` +
          `&query=${encodedFilter}`,
      ];
    }

    return [
      false,
      `/projects/?n=${reposPerPage + 1}&S=${offset}` + `&d=&m=${encodedFilter}`,
    ];
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

  async getRepos(
    filter: string | undefined,
    reposPerPage: number,
    offset?: number,
    errFn?: ErrorCallback
  ): Promise<ProjectInfoWithName[] | undefined> {
    const [isQuery, url] = this._getReposUrl(filter, reposPerPage, offset);

    // TODO(kaspern): Rename rest api from /projects/ to /repos/ once backend
    // supports it.

    // If the request is a query then return the response directly as the result
    // will already be the expected array. If it is not a query, transform the
    // map to an array.
    if (isQuery) {
      return this._fetchSharedCacheURL({
        url,
        anonymizedUrl: '/projects/?*',
        errFn,
      }) as Promise<ProjectInfoWithName[] | undefined>;
    } else {
      const result = await (this._fetchSharedCacheURL({
        url,
        anonymizedUrl: '/projects/?*',
        errFn,
      }) as Promise<NameToProjectInfoMap | undefined>);
      if (result === undefined) return [];
      return Object.entries(result).map(([name, project]) => {
        return {
          ...project,
          name: name as RepoName,
        };
      });
    }
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
    return this._restApiHelper.fetchJSON({
      url,
      errFn,
      anonymizedUrl: '/projects/*/tags',
    }) as unknown as Promise<TagInfo[]>;
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
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: `/projects/${encodeURIComponent(projectName)}/access:review`,
      body: projectInfo,
      parseResponse: true,
      anonymizedUrl: '/projects/*/access:review',
    }) as unknown as Promise<ChangeInfo>;
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
      params,
      reportUrlAsIs: true,
      errFn,
    }) as Promise<GroupNameToGroupInfoMap | undefined>;
  }

  getSuggestedRepos(
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
      params,
      reportUrlAsIs: true,
      errFn,
    });
  }

  getSuggestedAccounts(
    inputVal: string,
    n?: number,
    canSee?: NumericChangeId,
    filterActive?: boolean,
    errFn?: ErrorCallback
  ): Promise<AccountInfo[] | undefined> {
    const params: QueryAccountsParams = {o: 'DETAILS', q: ''};
    const queryParams = [];
    inputVal = inputVal?.trim() ?? '';
    if (inputVal.length > 0) {
      // Wrap in quotes so that reserved keywords do not throw an error such
      // as typing "and"
      // Espace quotes in user input since we are wrapping input in quotes
      // explicitly
      queryParams.push(`${escapeAndWrapSearchOperatorValue(inputVal)}`);
    }
    if (canSee) {
      queryParams.push(`cansee:${canSee}`);
    }
    if (filterActive) {
      queryParams.push('is:active');
    }
    params.q = queryParams.join(' and ');
    if (!params.q) return Promise.resolve([]);
    if (n) {
      params.n = n;
    }
    return this._restApiHelper.fetchJSON({
      url: '/accounts/',
      params,
      anonymizedUrl: '/accounts/?n=*',
      errFn,
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
    const options = '?o=SUBMITTABLE';
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/related${options}`,
      revision: patchNum,
      reportEndpointAsIs: true,
    }) as Promise<RelatedChangesInfo | undefined>;
  }

  getChangesSubmittedTogether(
    changeNum: NumericChangeId,
    options: string[] = ['NON_VISIBLE_CHANGES']
  ): Promise<SubmittedTogetherInfo | undefined> {
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: `/submitted_together?o=${options.join('&o=')}`,
      reportEndpointAsIs: true,
    }) as Promise<SubmittedTogetherInfo | undefined>;
  }

  async getChangeConflicts(
    changeNum: NumericChangeId
  ): Promise<ChangeInfo[] | undefined> {
    const config = await this.getConfig(false);
    if (!config?.change?.conflicts_predicate_enabled) {
      return [];
    }
    const options = listChangesOptionsToHex(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT,
      ListChangesOption.SUBMITTABLE
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
    repo: RepoName,
    changeID: ChangeId,
    branch: BranchName
  ): Promise<ChangeInfo[] | undefined> {
    const options = listChangesOptionsToHex(
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT
    );
    const query = [
      `project:${repo}`,
      `change:${changeID}`,
      `-branch:${branch}`,
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
    options?: {
      openChangesOnly?: boolean;
      changeToExclude?: NumericChangeId;
    }
  ): Promise<ChangeInfo[] | undefined> {
    const requestOptions = listChangesOptionsToHex(
      ListChangesOption.LABELS,
      ListChangesOption.CURRENT_REVISION,
      ListChangesOption.CURRENT_COMMIT,
      ListChangesOption.DETAILED_LABELS,
      ListChangesOption.SUBMITTABLE
    );
    const queryTerms = [`topic:${escapeAndWrapSearchOperatorValue(topic)}`];
    if (options?.openChangesOnly) {
      queryTerms.push('status:open');
    }
    if (options?.changeToExclude !== undefined) {
      queryTerms.push(`-change:${options.changeToExclude}`);
    }
    const params = {
      O: requestOptions,
      q: queryTerms.join(' '),
    };
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params,
      anonymizedUrl: '/changes/topic:*',
    }) as Promise<ChangeInfo[] | undefined>;
  }

  getChangesWithSimilarTopic(
    topic: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined> {
    const query = `intopic:${escapeAndWrapSearchOperatorValue(topic)}`;
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params: {q: query},
      anonymizedUrl: '/changes/intopic:*',
      errFn,
    }) as Promise<ChangeInfo[] | undefined>;
  }

  getChangesWithSimilarHashtag(
    hashtag: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo[] | undefined> {
    const query = `inhashtag:${escapeAndWrapSearchOperatorValue(hashtag)}`;
    return this._restApiHelper.fetchJSON({
      url: '/changes/',
      params: {q: query},
      anonymizedUrl: '/changes/inhashtag:*',
      errFn,
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
  ): Promise<Response> {
    return this._getChangeURLAndSend({
      changeNum,
      method: reviewed ? HttpMethod.PUT : HttpMethod.DELETE,
      patchNum,
      endpoint: `/files/${encodeURIComponent(path)}/reviewed`,
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

  getChangeEdit(changeNum?: NumericChangeId): Promise<EditInfo | undefined> {
    if (!changeNum) return Promise.resolve(undefined);
    const params = {'download-commands': true};
    return this.getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        return Promise.resolve(undefined);
      }
      return this._getChangeURLAndFetch(
        {
          changeNum,
          endpoint: '/edit/',
          params,
          reportEndpointAsIs: true,
        },
        true
      ) as Promise<EditInfo | undefined>;
    });
  }

  createChange(
    repo: RepoName,
    branch: BranchName,
    subject: string,
    topic?: string,
    isPrivate?: boolean,
    workInProgress?: boolean,
    baseChange?: ChangeId,
    baseCommit?: string
  ) {
    return this._restApiHelper.send({
      method: HttpMethod.POST,
      url: '/changes/',
      body: {
        project: repo,
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
    }) as unknown as Promise<ChangeInfo | undefined>;
  }

  getFileContent(
    changeNum: NumericChangeId,
    path: string,
    patchNum: PatchSetNum
  ): Promise<Response | Base64FileContent | undefined> {
    // 404s indicate the file does not exist yet in the revision, so suppress
    // them.
    const promise =
      patchNum === EDIT
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
        const strContent = content as unknown as string | null;
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

  getFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixReplacementInfos: FixReplacementInfo[]
  ): Promise<FilePathToDiffInfoMap | undefined> {
    return this._getChangeURLAndSend({
      method: HttpMethod.POST,
      changeNum,
      patchNum,
      endpoint: '/fix:preview',
      reportEndpointAsId: true,
      headers: {Accept: 'application/json'},
      parseResponse: true,
      body: {fix_replacement_infos: fixReplacementInfos},
    }) as Promise<FilePathToDiffInfoMap | undefined>;
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
    fixReplacementInfos: FixReplacementInfo[]
  ): Promise<Response> {
    return this._getChangeURLAndSend({
      method: HttpMethod.POST,
      changeNum,
      patchNum,
      endpoint: '/fix:apply',
      reportEndpointAsId: true,
      headers: {Accept: 'application/json'},
      body: {fix_replacement_infos: fixReplacementInfos},
    });
  }

  applyRobotFixSuggestion(
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
      return this._serialScheduler.schedule(() =>
        this._restApiHelper.send({
          method: starred ? HttpMethod.PUT : HttpMethod.DELETE,
          url,
          anonymizedUrl: '/accounts/self/starred.changes/*',
        })
      );
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

  getDiff(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string,
    whitespace?: IgnoreWhitespaceType,
    errFn?: ErrorCallback
  ) {
    const params: GetDiffParams = {
      intraline: null,
      whitespace: whitespace || 'IGNORE_NONE',
    };
    if (isMergeParent(basePatchNum)) {
      params.parent = getParentIndex(basePatchNum);
    } else if (basePatchNum !== PARENT) {
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
    if (patchNum === EDIT) {
      if (!req.fetchOptions) req.fetchOptions = {};
      if (!req.fetchOptions.headers) req.fetchOptions.headers = new Headers();
      req.fetchOptions.headers.append('Cache-Control', 'no-cache');
    }

    return this._getChangeURLAndFetch(req) as Promise<DiffInfo | undefined>;
  }

  getDiffComments(
    changeNum: NumericChangeId
  ): Promise<{[path: string]: CommentInfo[]} | undefined>;

  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum: BasePatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffCommentsOutput>;

  getDiffComments(
    changeNum: NumericChangeId,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    if (!basePatchNum && !patchNum && !path) {
      return this._getDiffComments(changeNum, '/comments', {
        'enable-context': true,
        'context-padding': 3,
      });
    }
    return this._getDiffComments(
      changeNum,
      '/comments',
      {'enable-context': true, 'context-padding': 3},
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
    basePatchNum: BasePatchSetNum,
    patchNum: PatchSetNum,
    path: string
  ): Promise<GetDiffRobotCommentsOutput>;

  getDiffRobotComments(
    changeNum: NumericChangeId,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ) {
    if (!basePatchNum && !patchNum && !path) {
      return this._getDiffComments(changeNum, '/robotcomments');
    }

    return this._getDiffComments(
      changeNum,
      '/robotcomments',
      undefined,
      basePatchNum,
      patchNum,
      path
    );
  }

  async getDiffDrafts(
    changeNum: NumericChangeId
  ): Promise<{[path: string]: DraftInfo[]} | undefined> {
    const loggedIn = await this.getLoggedIn();
    if (!loggedIn) return {};
    const comments = await this._getDiffComments(changeNum, '/drafts', {
      'enable-context': true,
      'context-padding': 3,
    });
    return addDraftProp(comments);
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
    endpoint: '/comments' | '/drafts',
    params?: FetchParams
  ): Promise<{[path: string]: CommentInfo[]} | undefined>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/robotcomments'
  ): Promise<PathToRobotCommentsInfoMap | undefined>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/comments' | '/drafts',
    params?: FetchParams,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<GetDiffCommentsOutput>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: '/robotcomments',
    params?: FetchParams,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<GetDiffRobotCommentsOutput>;

  _getDiffComments(
    changeNum: NumericChangeId,
    endpoint: string,
    params?: FetchParams,
    basePatchNum?: BasePatchSetNum,
    patchNum?: PatchSetNum,
    path?: string
  ): Promise<
    | GetDiffCommentsOutput
    | GetDiffRobotCommentsOutput
    | {[path: string]: CommentInfo[]}
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
          params,
        },
        noAcceptHeader
      ) as Promise<
        {[path: string]: CommentInfo[]} | PathToRobotCommentsInfoMap | undefined
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

      if (basePatchNum === PARENT) {
        baseComments = comments.filter(onlyParent);
        baseComments.forEach(setPath);
      }
      comments = comments.filter(withoutParent);

      comments.forEach(setPath);
    });
    promises.push(fetchPromise);

    if (basePatchNum !== PARENT) {
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

  getPortedComments(
    changeNum: NumericChangeId,
    revision: RevisionId
  ): Promise<{[path: string]: CommentInfo[]} | undefined> {
    // maintaining a custom error function so that errors do not surface in UI
    const errFn: ErrorCallback = (response?: Response | null) => {
      if (response)
        console.info(`Fetching ported comments failed, ${response.status}`);
    };
    return this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/ported_comments/',
      revision,
      errFn,
    });
  }

  async getPortedDrafts(
    changeNum: NumericChangeId,
    revision: RevisionId
  ): Promise<{[path: string]: DraftInfo[]} | undefined> {
    // maintaining a custom error function so that errors do not surface in UI
    const errFn: ErrorCallback = (response?: Response | null) => {
      if (response)
        console.info(`Fetching ported drafts failed, ${response.status}`);
    };
    const loggedIn = await this.getLoggedIn();
    if (!loggedIn) return {};
    const comments = (await this._getChangeURLAndFetch({
      changeNum,
      endpoint: '/ported_drafts/',
      revision,
      errFn,
    })) as {[path: string]: CommentInfo[]} | undefined;
    return addDraftProp(comments);
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

  hasPendingDiffDrafts(): number {
    const promises = this._pendingRequests[Requests.SEND_DIFF_DRAFT];
    return promises && promises.length;
  }

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
    repo: RepoName,
    commit: CommitId
  ): Promise<CommitInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      url:
        '/projects/' +
        encodeURIComponent(repo) +
        '/commits/' +
        encodeURIComponent(commit),
      anonymizedUrl: '/projects/*/commits/*',
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
      if (patchRange.basePatchNum === PARENT) {
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
      const baseImage: Base64ImageFile | null =
        results[0] && diff.meta_a
          ? {
              ...results[0],
              _expectedType: diff.meta_a.content_type,
              _name: diff.meta_a.name,
            }
          : null;
      const revisionImage: Base64ImageFile | null =
        results[1] && diff.meta_b
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
    revisionId?: RevisionId
  ): Promise<string> {
    return this.getFromProjectLookup(changeNum).then(project => {
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

  setChangeTopic(changeNum: NumericChangeId, topic?: string): Promise<string> {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.PUT,
      endpoint: '/topic',
      body: {topic},
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown as Promise<string>;
  }

  setChangeHashtag(
    changeNum: NumericChangeId,
    hashtag: HashtagsInput
  ): Promise<Hashtag[]> {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      endpoint: '/hashtags',
      body: hashtag,
      parseResponse: true,
      reportUrlAsIs: true,
    }) as unknown as Promise<Hashtag[]>;
  }

  deleteAccountHttpPassword() {
    return this._restApiHelper.send({
      method: HttpMethod.DELETE,
      url: '/accounts/self/password.http',
      reportUrlAsIs: true,
    });
  }

  generateAccountHttpPassword(): Promise<Password> {
    return this._restApiHelper.send({
      method: HttpMethod.PUT,
      url: '/accounts/self/password.http',
      body: {generate: true},
      parseResponse: true,
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<Password>;
  }

  getAccountSSHKeys() {
    return this._fetchSharedCacheURL({
      url: '/accounts/self/sshkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<SshKeyInfo[] | undefined>;
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
        return this.getResponseObject(
          response
        ) as unknown as Promise<SshKeyInfo>;
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
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/gpgkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<Record<string, GpgKeyInfo>>;
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

  getTopMenus(): Promise<TopMenuEntryInfo[] | undefined> {
    return this._fetchSharedCacheURL({
      url: '/config/server/top-menus',
      reportUrlAsIs: true,
    }) as Promise<TopMenuEntryInfo[] | undefined>;
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

  deleteComment(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    commentID: UrlEncodedCommentId,
    reason: string
  ) {
    return this._getChangeURLAndSend({
      changeNum,
      method: HttpMethod.POST,
      patchNum,
      endpoint: `/comments/${commentID}/delete`,
      body: {reason},
      parseResponse: true,
      anonymizedEndpoint: '/comments/*/delete',
    }) as unknown as Promise<CommentInfo>;
  }

  getChange(
    changeNum: ChangeId | NumericChangeId,
    errFn: ErrorCallback
  ): Promise<ChangeInfo | null> {
    // Cannot use _changeBaseURL, as this function is used by _projectLookup.
    return this._restApiHelper
      .fetchJSON(
        {
          url: `/changes/?q=change:${changeNum}`,
          errFn,
          anonymizedUrl: '/changes/?q=change:*',
        },
        /* noAcceptHeader */ true
      )
      .then(res => {
        const changeInfos = res as ChangeInfo[] | undefined;
        if (!changeInfos || !changeInfos.length) {
          return null;
        }
        return changeInfos[0];
      });
  }

  async setInProjectLookup(changeNum: NumericChangeId, project: RepoName) {
    const lookupProject = await this._projectLookup[changeNum];
    if (lookupProject && lookupProject !== project) {
      console.warn(
        'Change set with multiple project nums.' +
          'One of them must be invalid.'
      );
    }
    this._projectLookup[changeNum] = Promise.resolve(project);
  }

  getFromProjectLookup(
    changeNum: NumericChangeId
  ): Promise<RepoName | undefined> {
    const project = this._projectLookup[`${changeNum}`];
    if (project) {
      return project;
    }

    const onError = (response?: Response | null) => firePageError(response);

    const projectPromise = this.getChange(changeNum, onError).then(change => {
      if (!change || !change.project) {
        return;
      }
      this.setInProjectLookup(changeNum, change.project);
      return change.project;
    });

    this._projectLookup[changeNum] = projectPromise;

    return projectPromise;
  }

  // if errFn is not set, then only Response possible
  _getChangeURLAndSend(
    req: SendRawChangeRequest & {errFn?: undefined}
  ): Promise<Response>;

  _getChangeURLAndSend(
    req: SendRawChangeRequest
  ): Promise<Response | undefined>;

  _getChangeURLAndSend(req: SendJSONChangeRequest): Promise<ParsedJSON>;

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

  getDashboard(
    repo: RepoName,
    dashboard: DashboardId,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo | undefined> {
    const url =
      '/projects/' +
      encodeURIComponent(repo) +
      '/dashboards/' +
      encodeURIComponent(dashboard);
    return this._fetchSharedCacheURL({
      url,
      errFn,
      anonymizedUrl: '/projects/*/dashboards/*',
    }) as Promise<DashboardInfo | undefined>;
  }

  /**
   * Get the docs base URL from either the server config or by probing.
   *
   * @return A promise that resolves with the docs base URL.
   */
  getDocsBaseUrl(config: ServerInfo | undefined): Promise<string | null> {
    if (!this.getDocsBaseUrlCachedPromise) {
      this.getDocsBaseUrlCachedPromise = new Promise(resolve => {
        if (config?.gerrit?.doc_url) {
          resolve(config.gerrit.doc_url);
        } else {
          this.probePath(getBaseUrl() + PROBE_PATH).then(ok => {
            resolve(ok ? getBaseUrl() + DOCS_BASE_PATH : null);
          });
        }
      });
    }
    return this.getDocsBaseUrlCachedPromise;
  }

  testOnly_clearDocsBaseUrlCache() {
    this.getDocsBaseUrlCachedPromise = undefined;
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
