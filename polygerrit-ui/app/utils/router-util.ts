/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PARENT, RepoName} from '../types/common';
import {
  DashboardSection,
  GenerateUrlChangeViewParameters,
  GenerateUrlDashboardViewParameters,
} from '../elements/core/gr-navigation/gr-navigation';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {encodeURL} from './url-util';

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

export function generateChangeUrl(params: GenerateUrlChangeViewParameters) {
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

export function generateDashboardUrl(
  params: GenerateUrlDashboardViewParameters
) {
  const repoName = params.repo || params.project || undefined;
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
