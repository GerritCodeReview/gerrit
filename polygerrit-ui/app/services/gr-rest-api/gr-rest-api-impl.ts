/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/* NB: Order is important, because of namespaced classes. */

import {GrEtagDecorator} from '../../elements/shared/gr-rest-api-interface/gr-etag-decorator';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {parseDate} from '../../utils/date-util';
import {getParentIndex, isMergeParent} from '../../utils/patch-set-util';
import {listChangesOptionsToHex} from '../../utils/change-util';
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
  DraftInfo,
  ListChangesOption,
  ReviewResult,
} from '../../types/common';
import {
  DiffInfo,
  DiffPreferencesInfo,
  IgnoreWhitespaceType,
} from '../../types/diff';
import {
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
import {Finalizable, ParsedChangeInfo} from '../../types/types';
import {ErrorCallback} from '../../api/rest';
import {addDraftProp} from '../../utils/comment-util';
import {BaseScheduler, Scheduler} from '../scheduler/scheduler';
import {MaxInFlightScheduler} from '../scheduler/max-in-flight-scheduler';
import {escapeAndWrapSearchOperatorValue} from '../../utils/string-util';
import {FlagsService, KnownExperimentId} from '../flags/flags';
import {RetryScheduler} from '../scheduler/retry-scheduler';
import {FixReplacementInfo} from '../../api/rest-api';
import {
  FetchParams,
  FetchPromisesCache,
  FetchRequest,
  getFetchOptions,
  GrRestApiHelper,
  parsePrefixedJSON,
  readJSONResponsePayload,
  SiteBasedCache,
  throwingErrorCallback,
} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

const MAX_PROJECT_RESULTS = 25;

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
// TODO: consider changing this to Map()
let projectLookup: {[changeNum: string]: Promise<RepoName> | undefined} = {}; // Shared across instances.

function suppress404s(res?: Response | null) {
  if (!res || res.status === 404) return;
  // This is the default error handling behavior of the rest-api-helper.
  fireServerError(res);
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

export function testOnlyResetGrRestApiSharedObjects(authService: AuthService) {
  siteBasedCache = new SiteBasedCache();
  fetchPromisesCache = new FetchPromisesCache();
  pendingRequest = {};
  grEtagDecorator = new GrEtagDecorator();
  projectLookup = {};
  authService.clearCache();
}

function createReadScheduler() {
  return new RetryScheduler<Response>(
    new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 10),
    3 /* maxRetry */,
    50 /* backoffIntervalMs */
  );
}

function createWriteScheduler() {
  return new RetryScheduler<Response>(
    new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 5),
    3 /* maxRetry */,
    50 /* backoffIntervalMs */
  );
}

function createSerializingScheduler() {
  return new MaxInFlightScheduler<Response>(new BaseScheduler<Response>(), 1);
}

export class GrRestApiServiceImpl implements RestApiService, Finalizable {
  readonly _cache = siteBasedCache; // Shared across instances.

  readonly _sharedFetchPromises = fetchPromisesCache; // Shared across instances.

  readonly _pendingRequests = pendingRequest; // Shared across instances.

  readonly _etags = grEtagDecorator; // Shared across instances.

  // readonly, but set in tests.
  _projectLookup = projectLookup; // Shared across instances.

  // The value is set in created, before any other actions
  // Private, but used in tests.
  readonly _restApiHelper: GrRestApiHelper;

  // Used to serialize requests for certain RPCs
  readonly _serialScheduler: Scheduler<Response>;

  constructor(
    private readonly authService: AuthService,
    private readonly flagService: FlagsService
  ) {
    const readScheduler = createReadScheduler();
    const writeScheduler = createWriteScheduler();
    this._restApiHelper = new GrRestApiHelper(
      this._cache,
      this.authService,
      this._sharedFetchPromises,
      readScheduler,
      writeScheduler
    );
    this._serialScheduler = createSerializingScheduler();
  }

  finalize() {}

  async getResponseObject(response: Response): Promise<ParsedJSON> {
    return (await readJSONResponsePayload(response)).parsed;
  }

  getConfig(noCache?: boolean): Promise<ServerInfo | undefined> {
    if (!noCache) {
      return this._restApiHelper.fetchCacheJSON({
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
    return this._restApiHelper.fetchCacheJSON({
      url: '/projects/' + encodeURIComponent(repo),
      errFn,
      anonymizedUrl: '/projects/*',
    }) as Promise<ProjectInfo | undefined>;
  }

  getProjectConfig(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<ConfigInfo | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: '/projects/' + encodeURIComponent(repo) + '/config',
      errFn,
      anonymizedUrl: '/projects/*/config',
    }) as Promise<ConfigInfo | undefined>;
  }

  getRepoAccess(repo: RepoName): Promise<RepoAccessInfoMap | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: '/access/?project=' + encodeURIComponent(repo),
      anonymizedUrl: '/access/?project=*',
    }) as Promise<RepoAccessInfoMap | undefined>;
  }

  getRepoDashboards(
    repo: RepoName,
    errFn?: ErrorCallback
  ): Promise<DashboardInfo[] | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: `/projects/${encodeURIComponent(repo)}/dashboards?inherited`,
      errFn,
      anonymizedUrl: '/projects/*/dashboards?inherited',
    }) as Promise<DashboardInfo[] | undefined>;
  }

  saveRepoConfig(repo: RepoName, config: ConfigInput): Promise<Response> {
    const url = `/projects/${encodeURIComponent(repo)}/config`;
    this._cache.delete(url);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: config,
      }),
      url,
      anonymizedUrl: '/projects/*/config',
      reportServerError: true,
    });
  }

  runRepoGC(repo: RepoName): Promise<Response> {
    const encodeName = encodeURIComponent(repo);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: '',
      }),
      url: `/projects/${encodeName}/gc`,
      anonymizedUrl: '/projects/*/gc',
      reportServerError: true,
    });
  }

  createRepo(config: ProjectInput & {name: RepoName}): Promise<Response> {
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: config,
      }),
      url: `/projects/${encodeName}`,
      anonymizedUrl: '/projects/*',
      reportServerError: true,
    });
  }

  createGroup(config: GroupInput & {name: string}): Promise<Response> {
    const encodeName = encodeURIComponent(config.name);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: config,
      }),
      url: `/groups/${encodeName}`,
      anonymizedUrl: '/groups/*',
      reportServerError: true,
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
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.DELETE,
        body: '',
      }),
      url: `/projects/${encodeName}/branches/${encodeRef}`,
      anonymizedUrl: '/projects/*/branches/*',
      reportServerError: true,
    });
  }

  deleteRepoTags(repo: RepoName, ref: GitRef): Promise<Response> {
    const encodeName = encodeURIComponent(repo);
    const encodeRef = encodeURIComponent(ref);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.DELETE,
        body: '',
      }),
      url: `/projects/${encodeName}/tags/${encodeRef}`,
      anonymizedUrl: '/projects/*/tags/*',
      reportServerError: true,
    });
  }

  createRepoBranch(
    name: RepoName,
    branch: BranchName,
    revision: BranchInput
  ): Promise<Response> {
    const encodeName = encodeURIComponent(name);
    const encodeBranch = encodeURIComponent(branch);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: revision,
      }),
      url: `/projects/${encodeName}/branches/${encodeBranch}`,
      anonymizedUrl: '/projects/*/branches/*',
      reportServerError: true,
    });
  }

  createRepoTag(
    name: RepoName,
    tag: string,
    revision: TagInput
  ): Promise<Response> {
    const encodeName = encodeURIComponent(name);
    const encodeTag = encodeURIComponent(tag);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: revision,
      }),
      url: `/projects/${encodeName}/tags/${encodeTag}`,
      anonymizedUrl: '/projects/*/tags/*',
      reportServerError: true,
    });
  }

  getIsGroupOwner(groupName?: GroupName): Promise<boolean> {
    if (!groupName) return Promise.resolve(false);
    const encodeName = encodeURIComponent(groupName);
    const req = {
      url: `/groups/?owned&g=${encodeName}`,
      anonymizedUrl: '/groups/owned&g=*',
    };
    return this._restApiHelper
      .fetchCacheJSON(req)
      .then(configs => hasOwnProperty(configs, groupName));
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
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {name},
      }),
      url: `/groups/${encodeId}/name`,
      anonymizedUrl: '/groups/*/name',
      reportServerError: true,
    });
  }

  saveGroupOwner(
    groupId: GroupId | GroupName,
    ownerId: string
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {owner: ownerId},
      }),
      url: `/groups/${encodeId}/owner`,
      anonymizedUrl: '/groups/*/owner',
      reportServerError: true,
    });
  }

  saveGroupDescription(
    groupId: GroupId | GroupName,
    description: string
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {description},
      }),
      url: `/groups/${encodeId}/description`,
      anonymizedUrl: '/groups/*/description',
      reportServerError: true,
    });
  }

  saveGroupOptions(
    groupId: GroupId | GroupName,
    options: GroupOptionsInput
  ): Promise<Response> {
    const encodeId = encodeURIComponent(groupId);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: options,
      }),
      url: `/groups/${encodeId}/options`,
      anonymizedUrl: '/groups/*/options',
      reportServerError: true,
    });
  }

  getGroupAuditLog(
    group: EncodedGroupId,
    errFn?: ErrorCallback
  ): Promise<GroupAuditEventInfo[] | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: `/groups/${group}/log.audit`,
      errFn,
      anonymizedUrl: '/groups/*/log.audit',
    }) as Promise<GroupAuditEventInfo[] | undefined>;
  }

  saveGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<AccountInfo | undefined> {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(`${groupMember}`);
    return this._restApiHelper.fetchJSON({
      fetchOptions: {
        method: HttpMethod.PUT,
      },
      url: `/groups/${encodeName}/members/${encodeMember}`,
      anonymizedUrl: '/groups/*/members/*',
    }) as unknown as Promise<AccountInfo | undefined>;
  }

  saveIncludedGroup(
    groupName: GroupId | GroupName,
    includedGroup: GroupId,
    errFn?: ErrorCallback
  ): Promise<GroupInfo | undefined> {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    const req = {
      fetchOptions: {
        method: HttpMethod.PUT,
      },
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      errFn,
      anonymizedUrl: '/groups/*/groups/*',
    };
    return this._restApiHelper.fetchJSON(req) as unknown as Promise<
      GroupInfo | undefined
    >;
  }

  deleteGroupMember(
    groupName: GroupId | GroupName,
    groupMember: AccountId
  ): Promise<Response> {
    const encodeName = encodeURIComponent(groupName);
    const encodeMember = encodeURIComponent(`${groupMember}`);
    return this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.DELETE,
      },
      url: `/groups/${encodeName}/members/${encodeMember}`,
      anonymizedUrl: '/groups/*/members/*',
      reportServerError: true,
    });
  }

  deleteIncludedGroup(
    groupName: GroupId,
    includedGroup: GroupId | GroupName
  ): Promise<Response> {
    const encodeName = encodeURIComponent(groupName);
    const encodeIncludedGroup = encodeURIComponent(includedGroup);
    return this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.DELETE,
      },
      url: `/groups/${encodeName}/groups/${encodeIncludedGroup}`,
      anonymizedUrl: '/groups/*/groups/*',
      reportServerError: true,
    });
  }

  getVersion(): Promise<string | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: '/config/server/version',
      reportUrlAsIs: true,
    }) as Promise<string | undefined>;
  }

  getDiffPreferences(): Promise<DiffPreferencesInfo | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        return this._restApiHelper.fetchCacheJSON({
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
        return this._restApiHelper.fetchCacheJSON({
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
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences');

    // Note (Issue 5142): normalize the download scheme with lower case before
    // saving.
    if (prefs.download_scheme) {
      prefs.download_scheme = prefs.download_scheme.toLowerCase();
    }

    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: prefs,
      }),
      url: '/accounts/self/preferences',
      reportUrlAsIs: true,
    }) as unknown as Promise<PreferencesInfo | undefined>;
  }

  saveDiffPreferences(prefs: DiffPreferenceInput): Promise<Response> {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.diff');
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: prefs,
      }),
      url: '/accounts/self/preferences.diff',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  saveEditPreferences(prefs: EditPreferencesInfo): Promise<Response> {
    // Invalidate the cache.
    this._cache.delete('/accounts/self/preferences.edit');
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: prefs,
      }),
      url: '/accounts/self/preferences.edit',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  getAccount(): Promise<AccountDetailInfo | undefined> {
    return this._restApiHelper.fetchCacheJSON({
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
    return this._restApiHelper.fetchCacheJSON({
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

  deleteAccount(): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.DELETE,
      },
      url: '/accounts/self',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  deleteAccountIdentity(id: string[]): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: id,
      }),
      url: '/accounts/self/external.ids:delete',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  getAccountDetails(
    userId: AccountId | EmailAddress,
    errFn?: ErrorCallback
  ): Promise<AccountDetailInfo | undefined> {
    return this._restApiHelper.fetchCacheJSON({
      url: `/accounts/${encodeURIComponent(userId)}/detail`,
      anonymizedUrl: '/accounts/*/detail',
      errFn,
    }) as Promise<AccountDetailInfo | undefined>;
  }

  async getAccountEmails() {
    const isloggedIn = await this.getLoggedIn();
    if (isloggedIn) {
      return this._restApiHelper.fetchCacheJSON({
        url: '/accounts/self/emails',
        reportUrlAsIs: true,
      }) as Promise<EmailInfo[] | undefined>;
    } else return;
  }

  getAccountEmailsFor(email: string, errFn?: ErrorCallback) {
    return this.getLoggedIn()
      .then(isLoggedIn => {
        if (isLoggedIn) {
          return this.getAccountCapabilities();
        } else {
          return undefined;
        }
      })
      .then((capabilities: AccountCapabilityInfo | undefined) => {
        if (capabilities && capabilities.viewSecondaryEmails) {
          return this._restApiHelper.fetchCacheJSON({
            url: '/accounts/' + email + '/emails',
            reportUrlAsIs: true,
            errFn,
          }) as Promise<EmailInfo[] | undefined>;
        }
        return undefined;
      });
  }

  addAccountEmail(email: string): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.PUT,
      },
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      anonymizedUrl: '/account/self/emails/*',
      reportServerError: true,
    });
  }

  deleteAccountEmail(email: string): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.DELETE,
      },
      url: '/accounts/self/emails/' + encodeURIComponent(email),
      anonymizedUrl: '/accounts/self/email/*',
      reportServerError: true,
    });
  }

  async setPreferredAccountEmail(email: string): Promise<void> {
    await this._restApiHelper.fetch({
      fetchOptions: {
        method: HttpMethod.PUT,
      },
      url: `/accounts/self/emails/${encodeURIComponent(email)}/preferred`,
      anonymizedUrl: '/accounts/self/emails/*/preferred',
      reportServerError: true,
    });
    // If result of getAccountEmails is in cache, update it in the cache
    // so we don't have to invalidate it.
    const cachedEmails = this._cache.get(
      '/accounts/self/emails'
    ) as unknown as EmailInfo[];
    if (cachedEmails) {
      const emails = cachedEmails.map(entry => {
        if (entry.email === email) {
          return {email: entry.email, preferred: true};
        } else {
          return {email: entry.email, preferred: false};
        }
      });
      this._cache.set('/accounts/self/emails', emails as unknown as ParsedJSON);
    }
  }

  _updateCachedAccount(obj: Partial<AccountDetailInfo>): void {
    // If result of getAccount is in cache, update it in the cache
    // so we don't have to invalidate it.
    const cachedAccount = this._cache.get('/accounts/self/detail');
    if (cachedAccount) {
      // Replace object in cache with new object to force UI updates.
      this._cache.set('/accounts/self/detail', {
        ...cachedAccount,
        ...obj,
      });
    }
  }

  async setAccountName(name: string): Promise<void> {
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {name},
      }),
      url: '/accounts/self/name',
      reportUrlAsIs: true,
      reportServerError: true,
    });
    if (!response.ok) {
      return;
    }
    let newName = undefined;
    // If the name was deleted server returns 204
    if (response.status !== 204) {
      newName = (await readJSONResponsePayload(response))
        .parsed as unknown as string;
    }
    this._updateCachedAccount({name: newName});
  }

  async setAccountUsername(username: string): Promise<void> {
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {username},
      }),
      url: '/accounts/self/username',
      reportUrlAsIs: true,
    });
    if (!response.ok) {
      return;
    }
    let newName = undefined;
    // If the name was deleted server returns 204
    if (response.status !== 204) {
      newName = (await readJSONResponsePayload(response))
        .parsed as unknown as string;
    }
    this._updateCachedAccount({username: newName});
  }

  async setAccountDisplayName(displayName: string): Promise<void> {
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {display_name: displayName},
      }),
      url: '/accounts/self/displayname',
      reportUrlAsIs: true,
      reportServerError: true,
    });
    if (!response.ok) {
      return;
    }
    let newName = undefined;
    // If the name was deleted server returns 204
    if (response.status !== 204) {
      newName = (await readJSONResponsePayload(response))
        .parsed as unknown as string;
    }
    this._updateCachedAccount({display_name: newName});
  }

  async setAccountStatus(status: string): Promise<void> {
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {status},
      }),
      url: '/accounts/self/status',
      reportUrlAsIs: true,
    });
    if (!response.ok) {
      return;
    }
    let newStatus = undefined;
    // If the status was deleted server returns 204
    if (response.status !== 204) {
      newStatus = (await readJSONResponsePayload(response))
        .parsed as unknown as string;
    }
    this._updateCachedAccount({status: newStatus});
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
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: name,
      }),
      url: '/accounts/self/agreements',
      reportUrlAsIs: true,
      reportServerError: true,
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
    return this._restApiHelper.fetchCacheJSON({
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
    return this._restApiHelper.fetchCacheJSON({
      url: '/config/server/preferences',
      reportUrlAsIs: true,
    }) as Promise<PreferencesInfo | undefined>;
  }

  getPreferences(): Promise<PreferencesInfo | undefined> {
    return this.getLoggedIn().then(loggedIn => {
      if (loggedIn) {
        const req = {url: '/accounts/self/preferences', reportUrlAsIs: true};
        return this._restApiHelper.fetchCacheJSON(req).then(res => {
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
    return this._restApiHelper.fetchCacheJSON({
      url: '/accounts/self/watched.projects',
      reportUrlAsIs: true,
    }) as unknown as Promise<ProjectWatchInfo[] | undefined>;
  }

  saveWatchedProjects(
    projects: ProjectWatchInfo[]
  ): Promise<ProjectWatchInfo[] | undefined> {
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: projects,
      }),
      url: '/accounts/self/watched.projects',
      reportUrlAsIs: true,
    }) as unknown as Promise<ProjectWatchInfo[] | undefined>;
  }

  deleteWatchedProjects(projects: ProjectWatchInfo[]): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: projects,
      }),
      url: '/accounts/self/watched.projects:delete',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  /**
   * Construct the uri to get list of changes.
   *
   * If options is undefined then default options (see getListChangesOptionsHex) is
   * used.
   */
  getRequestForGetChanges(
    changesPerPage?: number,
    query?: string[] | string,
    offset?: 'n,z' | number,
    options?: string
  ) {
    options = options || this.getListChangesOptionsHex();
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
      params: {...params, 'allow-incomplete-results': true},
      reportUrlAsIs: true,
    };
    return request;
  }

  /**
   * For every query fetches the matching changes.
   *
   * If options is undefined then default options (see getListChangesOptionsHex) is
   * used.
   */
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

  /**
   * Fetches changes that match the query.
   *
   * If options is undefined then default options (see getListChangesOptionsHex) is
   * used.
   */
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
      this.addRepoNameToCache(change._number, change.project);
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

  async getChangeDetail(
    changeNum?: NumericChangeId,
    errFn?: ErrorCallback
  ): Promise<ParsedChangeInfo | undefined> {
    if (!changeNum) return;
    const optionsHex = await this.getChangeOptionsHex();

    return this._getChangeDetail(changeNum, optionsHex, errFn).then(detail =>
      // detail has ChangeViewChangeInfo type because the optionsHex always
      // includes ALL_REVISIONS flag.
      GrReviewerUpdatesParser.parse(detail as ChangeViewChangeInfo)
    );
  }

  private getListChangesOptionsHex() {
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
      ListChangesOption.STAR,
    ];

    return listChangesOptionsToHex(...options);
  }

  async getChangeOptionsHex(): Promise<string> {
    if (window.DEFAULT_DETAIL_HEXES && window.DEFAULT_DETAIL_HEXES.changePage) {
      return window.DEFAULT_DETAIL_HEXES.changePage;
    }
    return listChangesOptionsToHex(...(await this.getChangeOptions()));
  }

  async getChangeOptions(): Promise<number[]> {
    const config = await this.getConfig(false);

    // This list MUST be kept in sync with
    // ChangeIT#changeDetailsDoesNotRequireIndex and IndexPreloadingUtil#CHANGE_DETAIL_OPTIONS
    // This list MUST be kept in sync with getResponseFormatOptions
    const options = [
      ListChangesOption.ALL_COMMITS,
      ListChangesOption.ALL_REVISIONS,
      ListChangesOption.CHANGE_ACTIONS,
      ListChangesOption.DETAILED_ACCOUNTS,
      ListChangesOption.DETAILED_LABELS,
      ListChangesOption.DOWNLOAD_COMMANDS,
      ListChangesOption.MESSAGES,
      ListChangesOption.REVIEWER_UPDATES,
      ListChangesOption.SUBMITTABLE,
      ListChangesOption.WEB_LINKS,
      ListChangesOption.SKIP_DIFFSTAT,
      ListChangesOption.SUBMIT_REQUIREMENTS,
    ];
    if (this.flagService.isEnabled(KnownExperimentId.REVISION_PARENTS_DATA)) {
      options.push(ListChangesOption.PARENTS);
    }
    if (config?.receive?.enable_signed_push) {
      options.push(ListChangesOption.PUSH_CERTIFICATES);
    }
    return options;
  }

  async getResponseFormatOptions(): Promise<string[]> {
    const config = await this.getConfig(false);

    // This list MUST be kept in sync with
    // ChangeIT#changeDetailsDoesNotRequireIndex and IndexPreloadingUtil#CHANGE_DETAIL_OPTIONS
    // This list MUST be kept in sync with getChangeOptions
    const options = [
      'ALL_COMMITS',
      'ALL_REVISIONS',
      'CHANGE_ACTIONS',
      'DETAILED_LABELS',
      'DETAILED_ACCOUNTS',
      'DOWNLOAD_COMMANDS',
      'MESSAGES',
      'REVIEWER_UPDATES',
      'SUBMITTABLE',
      'WEB_LINKS',
      'SKIP_DIFFSTAT',
      'SUBMIT_REQUIREMENTS',
    ];
    if (this.flagService.isEnabled(KnownExperimentId.REVISION_PARENTS_DATA)) {
      options.push('PARENTS');
    }
    if (config?.receive?.enable_signed_push) {
      options.push('PUSH_CERTIFICATES');
    }
    return options;
  }

  /**
   * @param optionsHex list changes options in hex
   */
  _getChangeDetail(
    changeNum: NumericChangeId,
    optionsHex: string,
    errFn?: ErrorCallback
  ): Promise<ChangeInfo | undefined> {
    return this.getChangeActionURL(changeNum, undefined, '/detail').then(
      url => {
        const params: FetchParams = {O: optionsHex};
        const urlWithParams = this._restApiHelper.urlWithParams(url, params);
        const req: FetchRequest = {
          url,
          errFn,
          params,
          fetchOptions: this._etags.getOptions(urlWithParams),
          anonymizedUrl: '/changes/*~*/detail?O=' + optionsHex,
        };
        return this._restApiHelper.fetch(req).then(response => {
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

          return readJSONResponsePayload(response)
            .then(payload => {
              this._etags.collect(urlWithParams, response, payload.raw);
              this._maybeInsertInLookup(
                payload.parsed as unknown as ChangeInfo
              );

              return payload.parsed as unknown as ChangeInfo;
            })
            .catch(() => undefined);
        });
      }
    );
  }

  async getChangeCommitInfo(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<CommitInfo | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/commit?links`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/commit?links`,
      errFn: suppress404s,
    }) as Promise<CommitInfo | undefined>;
  }

  async getChangeFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<FileNameToFileInfoMap | undefined> {
    let params = undefined;
    if (isMergeParent(patchRange.basePatchNum)) {
      params = {parent: getParentIndex(patchRange.basePatchNum)};
    } else if (patchRange.basePatchNum !== PARENT) {
      params = {base: patchRange.basePatchNum};
    }
    const url = await this._changeBaseURL(changeNum, patchRange.patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/files`,
      params,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files`,
    }) as Promise<FileNameToFileInfoMap | undefined>;
  }

  async getChangeEditFiles(
    changeNum: NumericChangeId,
    patchRange: PatchRange
  ): Promise<{files: FileNameToFileInfoMap} | undefined> {
    const changeUrl = await this._changeBaseURL(changeNum);
    let url = `${changeUrl}/edit?list`;
    let anonymizedUrl = `${changeUrl}/edit?list`;
    if (patchRange.basePatchNum !== PARENT) {
      url += '&base=' + encodeURIComponent(`${patchRange.basePatchNum}`);
      anonymizedUrl += '&base=*';
    }

    const response = await this._restApiHelper.fetch({url, anonymizedUrl});
    if (!response.ok || response.status === 204) {
      return undefined;
    }
    return (await readJSONResponsePayload(response)).parsed as unknown as
      | {files: FileNameToFileInfoMap}
      | undefined;
  }

  async queryChangeFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    query: string,
    errFn?: ErrorCallback
  ): Promise<string[] | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/files?q=${encodeURIComponent(query)}`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files?q=*`,
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

  async getChangeRevisionActions(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<ActionNameToActionInfoMap | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/actions`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/actions`,
    }) as Promise<ActionNameToActionInfoMap | undefined>;
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

  async _getChangeSuggestedGroup(
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
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/suggest_reviewers`,
      params,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/suggest_reviewers`,
      errFn,
    }) as Promise<SuggestedReviewerInfo[] | undefined>;
  }

  async getChangeIncludedIn(
    changeNum: NumericChangeId
  ): Promise<IncludedInInfo | undefined> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/in`,
      anonymizedUrl: `${url}/in`,
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

    return this._restApiHelper.fetchCacheJSON({
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
    // If the request is a query then return the response directly as the result
    // will already be the expected array. If it is not a query, transform the
    // map to an array.
    if (isQuery) {
      return this._restApiHelper.fetchCacheJSON({
        url,
        anonymizedUrl: '/projects/?*',
        errFn,
      }) as Promise<ProjectInfoWithName[] | undefined>;
    } else {
      const result = await (this._restApiHelper.fetchCacheJSON({
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
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {ref},
      }),
      url: `/projects/${encodeURIComponent(repo)}/HEAD`,
      anonymizedUrl: '/projects/*/HEAD',
      reportServerError: true,
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
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: repoInfo,
      }),
      url: `/projects/${encodeURIComponent(repoName)}/access`,
      anonymizedUrl: '/projects/*/access',
      reportServerError: true,
    });
  }

  setRepoAccessRightsForReview(
    projectName: RepoName,
    projectInfo: ProjectAccessInput
  ): Promise<ChangeInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: projectInfo,
      }),
      url: `/projects/${encodeURIComponent(projectName)}/access:review`,
      anonymizedUrl: '/projects/*/access:review',
    }) as unknown as Promise<ChangeInfo | undefined>;
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

  async queryAccounts(
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
      const project = await this.getRepoName(canSee);
      queryParams.push(`cansee:${project}~${canSee}`);
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

  getAccountSuggestions(inputVal: string): Promise<AccountInfo[] | undefined> {
    const params: QueryAccountsParams = {suggest: undefined, q: ''};
    inputVal = inputVal?.trim() ?? '';
    if (inputVal.length > 0) {
      params.q = inputVal;
    }
    if (!params.q) return Promise.resolve([]);
    return this._restApiHelper.fetchJSON({
      url: '/accounts/',
      params,
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

        return this._restApiHelper.fetch({
          fetchOptions: getFetchOptions({method, body}),
          url,
          reportServerError: true,
        });
      }
    );
  }

  async getRelatedChanges(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<RelatedChangesInfo | undefined> {
    const options = '?o=SUBMITTABLE';
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/related${options}`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/related${options}`,
    }) as Promise<RelatedChangesInfo | undefined>;
  }

  async getChangesSubmittedTogether(
    changeNum: NumericChangeId,
    options: string[] = ['NON_VISIBLE_CHANGES']
  ): Promise<SubmittedTogetherInfo | undefined> {
    const url = await this._changeBaseURL(changeNum);
    const endpoint = `/submitted_together?o=${options.join('&o=')}`;
    return this._restApiHelper.fetchJSON({
      url: `${url}${endpoint}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}${endpoint}`,
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

  async getReviewedFiles(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum
  ): Promise<string[] | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/files?reviewed`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files?reviewed`,
    }) as Promise<string[] | undefined>;
  }

  async saveFileReviewed(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    reviewed: boolean
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: reviewed ? HttpMethod.PUT : HttpMethod.DELETE},
      url: `${url}/files/${encodeURIComponent(path)}/reviewed`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files/*/reviewed`,
      reportServerError: true,
    });
  }

  async saveChangeReview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    review: ReviewInput,
    errFn?: ErrorCallback,
    fetchDetail?: boolean
  ): Promise<ReviewResult | undefined> {
    if (fetchDetail) {
      review.response_format_options = await this.getResponseFormatOptions();
    }
    const promises: [Promise<void>, Promise<string>] = [
      this.awaitPendingDiffDrafts(),
      this.getChangeActionURL(changeNum, patchNum, '/review'),
    ];
    return Promise.all(promises).then(([, url]) =>
      this._restApiHelper.fetchJSON({
        fetchOptions: getFetchOptions({
          method: HttpMethod.POST,
          body: review,
        }),
        url,
        errFn,
      })
    ) as unknown as Promise<ReviewResult | undefined>;
  }

  async getChangeEdit(
    changeNum?: NumericChangeId
  ): Promise<EditInfo | undefined> {
    if (!changeNum) return undefined;
    const params = {'download-commands': true};
    const loggedIn = await this.getLoggedIn();
    if (!loggedIn) {
      return undefined;
    }
    const url = await this._changeBaseURL(changeNum);
    const response = await this._restApiHelper.fetch({
      url: `${url}/edit/`,
      params,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit/`,
    });
    // If there is no edit patchset 204 is returned.
    if (!response.ok || response.status === 204) {
      return undefined;
    }
    return (await readJSONResponsePayload(response))
      .parsed as unknown as EditInfo;
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
  ): Promise<ChangeInfo | undefined> {
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
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
      }),
      url: '/changes/',
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
      // A 204 is returned if the file is empty so we have
      // to return early.
      if (!res.ok || res.status === 204) {
        return res;
      }

      // The file type (used for syntax highlighting) is identified in the
      // X-FYI-Content-Type header of the response.
      const type = res.headers.get('X-FYI-Content-Type');
      return readJSONResponsePayload(res).then(content => {
        const strContent = content.parsed as unknown as string | null;
        return {content: strContent, type, ok: true};
      });
    });
  }

  /**
   * Gets a file in a specific change and revision.
   */
  async _getFileInRevision(
    changeNum: NumericChangeId,
    path: string,
    patchNum: PatchSetNum,
    errFn?: ErrorCallback
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        headers: {Accept: 'application/json'},
      }),
      url: `${url}/files/${encodeURIComponent(path)}/content`,
      errFn,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files/*/content`,
      reportServerError: true,
    });
  }

  /**
   * Gets a file in a change edit.
   */
  async _getFileInChangeEdit(
    changeNum: NumericChangeId,
    path: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        headers: {Accept: 'application/json'},
      }),
      url: `${url}/edit/${encodeURIComponent(path)}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit/*`,
      reportServerError: true,
    });
  }

  async rebaseChangeEdit(changeNum: NumericChangeId): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.POST},
      url: `${url}/edit:rebase`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit:rebase`,
      reportServerError: true,
    });
  }

  async deleteChangeEdit(changeNum: NumericChangeId): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: `${url}/edit`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit`,
      reportServerError: true,
    });
  }

  async restoreFileInChangeEdit(
    changeNum: NumericChangeId,
    restore_path: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: {restore_path},
      }),
      url: `${url}/edit`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit`,
      reportServerError: true,
    });
  }

  async renameFileInChangeEdit(
    changeNum: NumericChangeId,
    old_path: string,
    new_path: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: {old_path, new_path},
      }),
      url: `${url}/edit`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit`,
      reportServerError: true,
    });
  }

  async deleteFileInChangeEdit(
    changeNum: NumericChangeId,
    path: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: `${url}/edit/${encodeURIComponent(path)}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit/*`,
      reportServerError: true,
    });
  }

  async saveChangeEdit(
    changeNum: NumericChangeId,
    path: string,
    contents: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: contents,
        contentType: 'text/plain',
      }),
      url: `${url}/edit/${encodeURIComponent(path)}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit/*`,
      reportServerError: true,
    });
  }

  async saveFileUploadChangeEdit(
    changeNum: NumericChangeId,
    path: string,
    content: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {binary_content: content},
      }),
      url: `${url}/edit/${encodeURIComponent(path)}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit/*`,
      reportServerError: true,
    });
  }

  async getFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixReplacementInfos: FixReplacementInfo[]
  ): Promise<FilePathToDiffInfoMap | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: {fix_replacement_infos: fixReplacementInfos},
      }),
      url: `${url}/fix:preview`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/fix:preview`,
    }) as Promise<FilePathToDiffInfoMap | undefined>;
  }

  async getRobotCommentFixPreview(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: FixId
  ): Promise<FilePathToDiffInfoMap | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    const endpoint = `/fixes/${encodeURIComponent(fixId)}/preview`;
    return this._restApiHelper.fetchJSON({
      url: `${url}${endpoint}`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}${endpoint}`,
    }) as Promise<FilePathToDiffInfoMap | undefined>;
  }

  async applyFixSuggestion(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixReplacementInfos: FixReplacementInfo[]
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        headers: {Accept: 'application/json'},
        body: {fix_replacement_infos: fixReplacementInfos},
      }),
      url: `${url}/fix:apply`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/fix:apply`,
      reportServerError: true,
    });
  }

  async applyRobotFixSuggestion(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    fixId: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    const endpoint = `/fixes/${encodeURIComponent(fixId)}/apply`;
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.POST},
      url: `${url}${endpoint}`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}${endpoint}`,
      reportServerError: true,
    });
  }

  async publishChangeEdit(changeNum: NumericChangeId) {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.POST},
      url: `${url}/edit:publish`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit:publish`,
      reportServerError: true,
    });
  }

  async putChangeCommitMessage(
    changeNum: NumericChangeId,
    message: string,
    committerEmail: string | null
  ) {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {message, committer_email: committerEmail},
      }),
      url: `${url}/message`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/message`,
      reportServerError: true,
    });
  }

  async updateIdentityInChangeEdit(
    changeNum: NumericChangeId,
    name: string,
    email: string,
    type: string
  ) {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {name, email, type},
      }),
      url: `${url}/edit:identity`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/edit:identity`,
      reportServerError: true,
    });
  }

  async deleteChangeCommitMessage(
    changeNum: NumericChangeId,
    messageId: ChangeMessageId
  ) {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: `${url}/messages/${messageId}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/messages/${messageId}`,
      reportServerError: true,
    });
  }

  saveChangeStarred(
    changeNum: NumericChangeId,
    starred: boolean
  ): Promise<Response> {
    // Some servers may require the project name to be provided
    // alongside the change number, so resolve the project name
    // first.
    return this.getRepoName(changeNum).then(project => {
      const encodedRepoName = encodeURIComponent(project) + '~';
      const url = `/accounts/self/starred.changes/${encodedRepoName}${changeNum}`;
      return this._serialScheduler.schedule(() =>
        this._restApiHelper.fetch({
          fetchOptions: {method: starred ? HttpMethod.PUT : HttpMethod.DELETE},
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
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method,
        body,
        contentType,
        headers,
      }),
      url,
      errFn,
      reportServerError: true,
    });
  }

  async getDiff(
    changeNum: NumericChangeId,
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    path: string,
    whitespace?: IgnoreWhitespaceType,
    errFn?: ErrorCallback
  ): Promise<DiffInfo | undefined> {
    const params: GetDiffParams = {
      intraline: null,
      whitespace: whitespace || 'IGNORE_NONE',
    };
    if (isMergeParent(basePatchNum)) {
      params.parent = getParentIndex(basePatchNum);
    } else if (basePatchNum !== PARENT) {
      params.base = basePatchNum;
    }

    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      // Invalidate the cache if this is the edit patch to make sure we always
      // get latest.
      fetchOptions: getFetchOptions({
        headers: patchNum === EDIT ? {'Cache-Control': 'no-cache'} : undefined,
      }),
      url: `${url}/files/${encodeURIComponent(path)}/diff`,
      params,
      errFn,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}'/files/*/diff`,
    }) as Promise<DiffInfo | undefined>;
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

  /**
   * Fetches the comments for a given patchNum.
   * Helper function to make promises more legible.
   */
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
    // We don't want to add accept header, since preloading of comments is
    // working only without accept header.
    const noAcceptHeader = true;
    const fetchComments = (patchNum?: PatchSetNum) =>
      this._changeBaseURL(changeNum, patchNum).then(url =>
        this._restApiHelper.fetchJSON(
          {
            url: `${url}${endpoint}`,
            anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}${endpoint}`,
            params,
          },
          noAcceptHeader
        )
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

  async getPortedComments(
    changeNum: NumericChangeId,
    revision: RevisionId
  ): Promise<{[path: string]: CommentInfo[]} | undefined> {
    // maintaining a custom error function so that errors do not surface in UI
    const errFn: ErrorCallback = (response?: Response | null) => {
      if (response)
        console.info(`Fetching ported comments failed, ${response.status}`);
    };
    const url = await this._changeBaseURL(changeNum, revision);
    return this._restApiHelper.fetchJSON({
      url: `${url}/ported_comments/`,
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
    const url = await this._changeBaseURL(changeNum, revision);
    const comments = (await this._restApiHelper.fetchJSON({
      url: `${url}/ported_drafts/`,
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

    if (!this._pendingRequests[Requests.SEND_DIFF_DRAFT]) {
      this._pendingRequests[Requests.SEND_DIFF_DRAFT] = [];
    }

    const fetchOptions =
      method === HttpMethod.PUT
        ? getFetchOptions({method, body: draft})
        : {method};

    const promise = this._changeBaseURL(changeNum, patchNum).then(url =>
      this._restApiHelper.fetch({
        fetchOptions,
        url: `${url}${endpoint}`,
        anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}${anonymizedEndpoint}`,
        reportServerError: true,
      })
    );

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
    return this._restApiHelper.fetch({url}).then(response => {
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
    return this.getRepoName(changeNum).then(project => {
      let url = `/changes/${encodeURIComponent(project)}~${changeNum}`;
      if (revisionId) {
        url += `/revisions/${revisionId}`;
      }
      return url;
    });
  }

  async addToAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId | undefined | null,
    reason: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: {user, reason},
      }),
      url: `${url}/attention`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/attention`,
      reportServerError: true,
    });
  }

  async removeFromAttentionSet(
    changeNum: NumericChangeId,
    user: AccountId,
    reason: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.DELETE,
        body: {reason},
      }),
      url: `${url}/attention/${user}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/attention/*`,
      reportServerError: true,
    });
  }

  async setChangeTopic(
    changeNum: NumericChangeId,
    topic?: string,
    errFn?: ErrorCallback
  ): Promise<string | undefined> {
    const url = await this._changeBaseURL(changeNum);
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {topic},
      }),
      url: `${url}/topic`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/topic`,
      errFn,
    });
    if (!response.ok) {
      return undefined;
    }
    if (response.status === 204) {
      return '';
    }
    return (await readJSONResponsePayload(response))
      .parsed as unknown as string;
  }

  removeChangeTopic(
    changeNum: NumericChangeId,
    errFn?: ErrorCallback
  ): Promise<string | undefined> {
    return this.setChangeTopic(changeNum, '', errFn);
  }

  async setChangeHashtag(
    changeNum: NumericChangeId,
    hashtag: HashtagsInput,
    errFn?: ErrorCallback
  ): Promise<Hashtag[] | undefined> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: hashtag,
      }),
      url: `${url}/hashtags`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/hashtags`,
      errFn,
    }) as unknown as Promise<Hashtag[] | undefined>;
  }

  deleteAccountHttpPassword(): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: '/accounts/self/password.http',
      reportUrlAsIs: true,
      reportServerError: true,
    });
  }

  generateAccountHttpPassword(): Promise<Password | undefined> {
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {generate: true},
      }),
      url: '/accounts/self/password.http',
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<Password>;
  }

  getAccountSSHKeys() {
    return this._restApiHelper.fetchCacheJSON({
      url: '/accounts/self/sshkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<SshKeyInfo[] | undefined>;
  }

  addAccountSSHKey(key: string): Promise<SshKeyInfo> {
    // By passing throwingErrorCallback we guarantee that response is not-null.
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: key,
        contentType: 'text/plain',
      }),
      url: '/accounts/self/sshkeys',
      reportUrlAsIs: true,
      errFn: throwingErrorCallback,
    }) as Promise<unknown> as Promise<SshKeyInfo>;
  }

  deleteAccountSSHKey(id: string): Promise<Response> {
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: '/accounts/self/sshkeys/' + id,
      anonymizedUrl: '/accounts/self/sshkeys/*',
      reportServerError: true,
    });
  }

  getAccountGPGKeys() {
    return this._restApiHelper.fetchJSON({
      url: '/accounts/self/gpgkeys',
      reportUrlAsIs: true,
    }) as Promise<unknown> as Promise<Record<string, GpgKeyInfo>>;
  }

  addAccountGPGKey(key: GpgKeysInput): Promise<Record<string, GpgKeyInfo>> {
    // By passing throwingErrorCallback we guarantee that response is not-null.
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: key,
      }),
      url: '/accounts/self/gpgkeys',
      reportUrlAsIs: true,
      errFn: throwingErrorCallback,
    }) as Promise<unknown> as Promise<Record<string, GpgKeyInfo>>;
  }

  deleteAccountGPGKey(id: GpgKeyId) {
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: `/accounts/self/gpgkeys/${id}`,
      anonymizedUrl: '/accounts/self/gpgkeys/*',
      reportServerError: true,
    });
  }

  async deleteVote(
    changeNum: NumericChangeId,
    account: AccountId,
    label: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetch({
      fetchOptions: {method: HttpMethod.DELETE},
      url: `${url}/reviewers/${account}/votes/${encodeURIComponent(label)}`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/reviewers/*/votes/*`,
      reportServerError: true,
    });
  }

  async setDescription(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    desc: string
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {description: desc},
      }),
      url: `${url}/description`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/description`,
      reportServerError: true,
    });
  }

  async confirmEmail(token: string): Promise<string | null> {
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.PUT,
        body: {token},
      }),
      url: '/config/server/email.confirm',
      reportUrlAsIs: true,
      reportServerError: true,
    });
    if (response?.status === 204) {
      return 'Email confirmed successfully.';
    }
    return null;
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
    return this._restApiHelper.fetchCacheJSON({
      url: '/config/server/top-menus',
      reportUrlAsIs: true,
    }) as Promise<TopMenuEntryInfo[] | undefined>;
  }

  probePath(path: string) {
    return fetch(new Request(path, {method: HttpMethod.HEAD})).then(
      response => response.ok
    );
  }

  async startWorkInProgress(
    changeNum: NumericChangeId,
    message?: string
  ): Promise<string | undefined> {
    const url = await this._changeBaseURL(changeNum);
    const response = await this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: message ? {message} : {},
      }),
      url: `${url}/wip`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/wip`,
      reportServerError: true,
    });
    if (response.status === 204) {
      return 'Change marked as Work In Progress.';
    }
    return undefined;
  }

  async deleteComment(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    commentID: UrlEncodedCommentId,
    reason: string
  ): Promise<CommentInfo | undefined> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body: {reason},
      }),
      url: `${url}/comments/${commentID}/delete`,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/comments/*/delete`,
    }) as unknown as Promise<CommentInfo | undefined>;
  }

  getChange(
    changeNum: ChangeId | NumericChangeId,
    errFn: ErrorCallback
  ): Promise<ChangeInfo | undefined> {
    if (changeNum in this._projectLookup) {
      // _projectLookup can only store NumericChangeId, so we are sure that
      // changeNum is NumericChangeId in this case.
      return this._changeBaseURL(changeNum as NumericChangeId).then(url =>
        this._restApiHelper.fetchJSON(
          {
            url,
            errFn,
            anonymizedUrl: '/changes/*~*',
          },
          /* noAcceptHeader */ true
        )
      ) as Promise<ChangeInfo | undefined>;
    } else {
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
            return undefined;
          }
          return changeInfos[0];
        });
    }
  }

  /**
   * This can be called by the router, if the project can be determined from
   * the URL. Or when handling a dashabord or a search response.
   *
   * Then we don't need to make a dedicated REST API call or have a fallback,
   * if that fails.
   */
  addRepoNameToCache(changeNum: NumericChangeId, project: RepoName) {
    this._projectLookup[changeNum] = Promise.resolve(project);
  }

  getRepoName(changeNum: NumericChangeId): Promise<RepoName> {
    // Hopefully addRepoNameToCache() has already been called. Then we don't
    // have to make a dedicated REST API call to look up the project.
    let projectPromise = this._projectLookup[changeNum];
    if (projectPromise) return projectPromise;

    // Ignore errors, because we have some dedicated fallback logic, see below.
    const onError = () => {};
    projectPromise = this.getChange(changeNum, onError).then(change => {
      if (change?.project) return change.project;

      // In the very rare case that the change index cannot provide an answer
      // (e.g. stale index) we should check, if the router has called
      // addRepoNameToCache() in the meantime. Then we can fall back to that.
      const currentProjectPromise = this._projectLookup[changeNum];
      if (currentProjectPromise && currentProjectPromise !== projectPromise) {
        return currentProjectPromise;
      }

      // No luck. Without knowing the project we cannot proceed at all.
      firePageError(
        new Response(
          `Failed to lookup the repo for change number ${changeNum}`,
          {status: 404}
        )
      );
      // Don't store failed lookups in the lookup.
      this._projectLookup[changeNum] = undefined;
      throw new Error(
        `Failed to lookup the repo for change number ${changeNum}`
      );
    });
    this._projectLookup[changeNum] = projectPromise;
    return projectPromise;
  }

  async executeChangeAction(
    changeNum: NumericChangeId,
    method: HttpMethod | undefined,
    endpoint: string,
    patchNum?: PatchSetNum,
    payload?: RequestPayload,
    errFn?: ErrorCallback
  ): Promise<Response> {
    const url = await this._changeBaseURL(changeNum, patchNum);
    // No anonymizedUrl specified so the request will not be logged.
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method,
        body: payload,
      }),
      url: url + endpoint,
      errFn,
      reportServerError: true,
    });
  }

  async getBlame(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    path: string,
    base?: boolean
  ): Promise<BlameInfo[] | undefined> {
    const encodedPath = encodeURIComponent(path);
    const url = await this._changeBaseURL(changeNum, patchNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/files/${encodedPath}/blame`,
      params: base ? {base: 't'} : undefined,
      anonymizedUrl: `${ANONYMIZED_REVISION_BASE_URL}/files/*/blame`,
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
    return this._restApiHelper.fetchCacheJSON({
      url,
      errFn,
      anonymizedUrl: '/projects/*/dashboards/*',
    }) as Promise<DashboardInfo | undefined>;
  }

  getDocumentationSearches(filter: string): Promise<DocResult[] | undefined> {
    filter = filter.trim();
    const encodedFilter = encodeURIComponent(filter);

    return this._restApiHelper.fetchCacheJSON({
      url: `/Documentation/?q=${encodedFilter}`,
      anonymizedUrl: '/Documentation/?*',
    }) as Promise<DocResult[] | undefined>;
  }

  async getMergeable(
    changeNum: NumericChangeId
  ): Promise<MergeableInfo | undefined> {
    const url = await this._changeBaseURL(changeNum);
    return this._restApiHelper.fetchJSON({
      url: `${url}/revisions/current/mergeable`,
      anonymizedUrl: `${ANONYMIZED_CHANGE_BASE_URL}/revisions/current/mergeable`,
    }) as Promise<MergeableInfo | undefined>;
  }

  deleteDraftComments(query: string): Promise<Response> {
    const body: DeleteDraftCommentsInput = {query};
    return this._restApiHelper.fetch({
      fetchOptions: getFetchOptions({
        method: HttpMethod.POST,
        body,
      }),
      url: '/accounts/self/drafts:delete',
    });
  }
}
