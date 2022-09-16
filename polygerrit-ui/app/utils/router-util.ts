/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ChangeInfo,
  RepoName,
} from '../types/common';
import {encodeURL, getBaseUrl} from './url-util';
import {assertNever} from './common-util';
import {GerritView} from '../services/router/router-model';
import {GroupDetailView, GroupViewState} from '../models/views/group';
import {DashboardViewState} from '../models/views/dashboard';
import {createEditUrl, EditViewState} from '../models/views/edit';
import {createDiffUrl, DiffViewState} from '../models/views/diff';
import {ChangeViewState, createChangeUrl} from '../models/views/change';

export interface DashboardSection {
  name: string;
  query: string;
  suffixForDashboard?: string;
  selfOnly?: boolean;
  hideIfEmpty?: boolean;
  results?: ChangeInfo[];
}

export type GenerateUrlParameters =
  | ChangeViewState
  | DashboardViewState
  | GroupViewState
  | EditViewState
  | DiffViewState;

export function isChangeViewState(
  x: GenerateUrlParameters
): x is ChangeViewState {
  return x.view === GerritView.CHANGE;
}

export function isEditViewState(x: GenerateUrlParameters): x is EditViewState {
  return x.view === GerritView.EDIT;
}

export function isDiffViewState(x: GenerateUrlParameters): x is DiffViewState {
  return x.view === GerritView.DIFF;
}

export function rootUrl() {
  return `${getBaseUrl()}/`;
}

export function generateUrl(params: GenerateUrlParameters) {
  const base = getBaseUrl();
  let url = '';

  if (params.view === GerritView.CHANGE) {
    url = createChangeUrl(params);
  } else if (params.view === GerritView.DASHBOARD) {
    url = generateDashboardUrl(params);
  } else if (params.view === GerritView.DIFF) {
    url = createDiffUrl(params);
  } else if (params.view === GerritView.EDIT) {
    url = createEditUrl(params);
  } else if (params.view === GerritView.GROUP) {
    url = generateGroupUrl(params);
  } else {
    assertNever(params, "Can't generate");
  }

  return base + url;
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
