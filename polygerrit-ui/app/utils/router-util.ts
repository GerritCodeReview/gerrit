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
  GenerateUrlDiffViewParameters,
  GenerateUrlEditViewParameters,
  GenerateUrlGroupViewParameters,
  GenerateUrlParameters,
  GenerateUrlRepoViewParameters,
  GenerateUrlSearchViewParameters,
  GroupDetailView,
  isGenerateUrlDiffViewParameters,
  RepoDetailView,
} from '../elements/core/gr-navigation/gr-navigation';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {encodeURL, getBaseUrl} from './url-util';
import {assertNever} from './common-util';
import {GerritView} from '../services/router/router-model';
import {addQuotesWhen} from './string-util';

export function generateUrl(params: GenerateUrlParameters) {
  const base = getBaseUrl();
  let url = '';

  if (params.view === GerritView.SEARCH) {
    url = generateSearchUrl(params);
  } else if (params.view === GerritView.CHANGE) {
    url = generateChangeUrl(params);
  } else if (params.view === GerritView.DASHBOARD) {
    url = generateDashboardUrl(params);
  } else if (
    params.view === GerritView.DIFF ||
    params.view === GerritView.EDIT
  ) {
    url = generateDiffOrEditUrl(params);
  } else if (params.view === GerritView.GROUP) {
    url = generateGroupUrl(params);
  } else if (params.view === GerritView.REPO) {
    url = generateRepoUrl(params);
  } else if (params.view === GerritView.ROOT) {
    url = '/';
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

export function generateSearchUrl(params: GenerateUrlSearchViewParameters) {
  let offsetExpr = '';
  if (params.offset && params.offset > 0) {
    offsetExpr = `,${params.offset}`;
  }

  if (params.query) {
    return '/q/' + encodeURL(params.query, true) + offsetExpr;
  }

  const operators: string[] = [];
  if (params.owner) {
    operators.push('owner:' + encodeURL(params.owner, false));
  }
  if (params.project) {
    operators.push('project:' + encodeURL(params.project, false));
  }
  if (params.branch) {
    operators.push('branch:' + encodeURL(params.branch, false));
  }
  if (params.topic) {
    operators.push(
      'topic:' +
        addQuotesWhen(
          encodeURL(params.topic, false),
          /[\s:]/.test(params.topic)
        )
    );
  }
  if (params.hashtag) {
    operators.push(
      'hashtag:' +
        addQuotesWhen(
          encodeURL(params.hashtag.toLowerCase(), false),
          /[\s:]/.test(params.hashtag)
        )
    );
  }
  if (params.statuses) {
    if (params.statuses.length === 1) {
      operators.push('status:' + encodeURL(params.statuses[0], false));
    } else if (params.statuses.length > 1) {
      operators.push(
        '(' +
          params.statuses
            .map(s => `status:${encodeURL(s, false)}`)
            .join(' OR ') +
          ')'
      );
    }
  }

  return '/q/' + operators.join('+') + offsetExpr;
}

export function generateDiffOrEditUrl(
  params: GenerateUrlDiffViewParameters | GenerateUrlEditViewParameters
) {
  let range = getPatchRangeExpression(params);
  if (range.length) {
    range = '/' + range;
  }

  let suffix = `${range}/${encodeURL(params.path || '', true)}`;

  if (params.view === GerritView.EDIT) {
    suffix += ',edit';
  }

  if (params.lineNum) {
    suffix += '#';
    if (isGenerateUrlDiffViewParameters(params) && params.leftSide) {
      suffix += 'b';
    }
    suffix += params.lineNum;
  }

  if (isGenerateUrlDiffViewParameters(params) && params.commentId) {
    suffix = `/comment/${params.commentId}` + suffix;
  }

  if (params.project) {
    const encodedProject = encodeURL(params.project, true);
    return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
  } else {
    return `/c/${params.changeNum}${suffix}`;
  }
}

export function generateGroupUrl(params: GenerateUrlGroupViewParameters) {
  let url = `/admin/groups/${encodeURL(`${params.groupId}`, true)}`;
  if (params.detail === GroupDetailView.MEMBERS) {
    url += ',members';
  } else if (params.detail === GroupDetailView.LOG) {
    url += ',audit-log';
  }
  return url;
}

export function generateRepoUrl(params: GenerateUrlRepoViewParameters) {
  let url = `/admin/repos/${encodeURL(`${params.repoName}`, true)}`;
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

export function generateSettingsUrl() {
  return '/settings';
}
