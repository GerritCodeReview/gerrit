/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {DashboardId} from '../../types/common';
import {DashboardSection} from '../../utils/router-util';
import {encodeURL} from '../../utils/url-util';
import {Model} from '../model';
import {ViewState} from './base';

export interface DashboardViewState extends ViewState {
  view: GerritView.DASHBOARD;
  project?: RepoName;
  dashboard?: DashboardId;
  user?: string;
  sections?: DashboardSection[];
  title?: string;
}

const DEFAULT_STATE: DashboardViewState = {
  view: GerritView.DASHBOARD,
};

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

export function createDashboardUrl(state: Omit<DashboardViewState, 'view'>) {
  const repoName = state.project || undefined;
  if (state.sections) {
    // Custom dashboard.
    const queryParams = sectionsToEncodedParams(state.sections, repoName);
    if (state.title) {
      queryParams.push('title=' + encodeURIComponent(state.title));
    }
    const user = state.user ? state.user : '';
    return `/dashboard/${user}?${queryParams.join('&')}`;
  } else if (repoName) {
    // Project dashboard.
    const encodedRepo = encodeURL(repoName, true);
    return `/p/${encodedRepo}/+/dashboard/${state.dashboard}`;
  } else {
    // User dashboard.
    return `/dashboard/${state.user || 'self'}`;
  }
}

export class DashboardViewModel extends Model<DashboardViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
