/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  ChangeInfo,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../types/common';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {encodeURL, getBaseUrl} from './url-util';
import {assertNever} from './common-util';
import {GerritView} from '../services/router/router-model';
import {AttemptChoice} from '../models/checks/checks-util';
import {GroupDetailView, GroupViewState} from '../models/views/group';
import {DashboardViewState} from '../models/views/dashboard';
import {RepoDetailView, RepoViewState} from '../models/views/repo';
import {SettingsViewState} from '../models/views/settings';
import {createEditUrl, EditViewState} from '../models/views/edit';
import {createDiffUrl, DiffViewState} from '../models/views/diff';

export interface DashboardSection {
  name: string;
  query: string;
  suffixForDashboard?: string;
  selfOnly?: boolean;
  hideIfEmpty?: boolean;
  results?: ChangeInfo[];
}

export interface GenerateUrlChangeViewParameters {
  view: GerritView.CHANGE;
  // TODO(TS): NumericChangeId - not sure about it, may be it can be removed
  changeNum: NumericChangeId;
  project: RepoName;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  edit?: boolean;
  messageHash?: string;
  commentId?: UrlEncodedCommentId;
  forceReload?: boolean;
  openReplyDialog?: boolean;
  tab?: string;
  /** regular expression for filtering check runs */
  filter?: string;
  /** selected attempt for selected check runs */
  attempt?: AttemptChoice;
  usp?: string;
}

export type GenerateUrlParameters =
  | GenerateUrlChangeViewParameters
  | RepoViewState
  | DashboardViewState
  | GroupViewState
  | EditViewState
  | SettingsViewState
  | DiffViewState;

export function isGenerateUrlChangeViewParameters(
  x: GenerateUrlParameters
): x is GenerateUrlChangeViewParameters {
  return x.view === GerritView.CHANGE;
}

export function isEditViewState(x: GenerateUrlParameters): x is EditViewState {
  return x.view === GerritView.EDIT;
}

export function isDiffViewState(x: GenerateUrlParameters): x is DiffViewState {
  return x.view === GerritView.DIFF;
}

export const TEST_ONLY = {
  getPatchRangeExpression,
};

export function rootUrl() {
  return `${getBaseUrl()}/`;
}

export function generateUrl(params: GenerateUrlParameters) {
  const base = getBaseUrl();
  let url = '';

  if (params.view === GerritView.CHANGE) {
    url = generateChangeUrl(params);
  } else if (params.view === GerritView.DASHBOARD) {
    url = generateDashboardUrl(params);
  } else if (params.view === GerritView.DIFF) {
    url = createDiffUrl(params);
  } else if (params.view === GerritView.EDIT) {
    url = createEditUrl(params);
  } else if (params.view === GerritView.GROUP) {
    url = generateGroupUrl(params);
  } else if (params.view === GerritView.REPO) {
    url = generateRepoUrl(params);
  } else if (params.view === GerritView.SETTINGS) {
    url = generateSettingsUrl();
  } else {
    assertNever(params, "Can't generate");
  }

  return base + url;
}

/**
 * Given an object of parameters, potentially including a `patchNum` or a
 * `basePatchNum` or both, return a string representation of that range. If
 * no range is indicated in the params, the empty string is returned.
 */
export function getPatchRangeExpression(params: PatchRangeParams) {
  let range = '';
  if (params.patchNum) {
    range = `${params.patchNum}`;
  }
  if (params.basePatchNum && params.basePatchNum !== PARENT) {
    range = `${params.basePatchNum}..${range}`;
  }
  return range;
}

function generateChangeUrl(params: GenerateUrlChangeViewParameters) {
  let range = getPatchRangeExpression(params);
  if (range.length) {
    range = '/' + range;
  }
  let suffix = `${range}`;
  const queries = [];
  if (params.forceReload) {
    queries.push('forceReload=true');
  }
  if (params.openReplyDialog) {
    queries.push('openReplyDialog=true');
  }
  if (params.usp) {
    queries.push(`usp=${params.usp}`);
  }
  if (params.edit) {
    suffix += ',edit';
  }
  if (params.commentId) {
    suffix = suffix + `/comments/${params.commentId}`;
  }
  if (queries.length > 0) {
    suffix += '?' + queries.join('&');
  }
  if (params.messageHash) {
    suffix += params.messageHash;
  }
  if (params.project) {
    const encodedProject = encodeURL(params.project, true);
    return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
  } else {
    return `/c/${params.changeNum}${suffix}`;
  }
}

const REPO_TOKEN_PATTERN = /\${(project|repo)}/g;

function sectionsToEncodedParams(
  sections: DashboardSection[],
  repoName?: RepoName
) {
  return sections.map(section => {
    // If there is a repo name provided, make sure to substitute it into the
    // ${repo} (or legacy ${project}) query tokens.
    const query = repoName
      ? section.query.replace(REPO_TOKEN_PATTERN, repoName)
      : section.query;
    return encodeURIComponent(section.name) + '=' + encodeURIComponent(query);
  });
}

function generateDashboardUrl(params: DashboardViewState) {
  const repoName = params.project || undefined;
  if (params.sections) {
    // Custom dashboard.
    const queryParams = sectionsToEncodedParams(params.sections, repoName);
    if (params.title) {
      queryParams.push('title=' + encodeURIComponent(params.title));
    }
    const user = params.user ? params.user : '';
    return `/dashboard/${user}?${queryParams.join('&')}`;
  } else if (repoName) {
    // Project dashboard.
    const encodedRepo = encodeURL(repoName, true);
    return `/p/${encodedRepo}/+/dashboard/${params.dashboard}`;
  } else {
    // User dashboard.
    return `/dashboard/${params.user || 'self'}`;
  }
}

function generateGroupUrl(params: GroupViewState) {
  let url = `/admin/groups/${encodeURL(`${params.groupId}`, true)}`;
  if (params.detail === GroupDetailView.MEMBERS) {
    url += ',members';
  } else if (params.detail === GroupDetailView.LOG) {
    url += ',audit-log';
  }
  return url;
}

function generateRepoUrl(params: RepoViewState) {
  let url = `/admin/repos/${encodeURL(`${params.repo}`, true)}`;
  if (params.detail === RepoDetailView.GENERAL) {
    url += ',general';
  } else if (params.detail === RepoDetailView.ACCESS) {
    url += ',access';
  } else if (params.detail === RepoDetailView.BRANCHES) {
    url += ',branches';
  } else if (params.detail === RepoDetailView.TAGS) {
    url += ',tags';
  } else if (params.detail === RepoDetailView.COMMANDS) {
    url += ',commands';
  } else if (params.detail === RepoDetailView.DASHBOARDS) {
    url += ',dashboards';
  }
  return url;
}

function generateSettingsUrl() {
  return '/settings';
}
