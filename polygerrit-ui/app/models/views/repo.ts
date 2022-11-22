/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {BranchName, RepoName} from '../../types/common';
import {encodeURL, getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export enum RepoDetailView {
  GENERAL = 'general',
  ACCESS = 'access',
  BRANCHES = 'branches',
  COMMANDS = 'commands',
  DASHBOARDS = 'dashboards',
  TAGS = 'tags',
}

export interface RepoViewState extends ViewState {
  view: GerritView.REPO;
  detail?: RepoDetailView;
  repo?: RepoName;
  filter?: string | null;
  offset?: number | string;
  /**
   * This is for creating a change from the URL and then redirecting to a file
   * editing page.
   */
  createEdit?: {
    branch: BranchName;
    path: string;
  };
}

export function createRepoUrl(state: Omit<RepoViewState, 'view'>) {
  let url = `/admin/repos/${encodeURL(`${state.repo}`, true)}`;
  if (state.detail === RepoDetailView.GENERAL) {
    url += ',general';
  } else if (state.detail === RepoDetailView.ACCESS) {
    url += ',access';
  } else if (state.detail === RepoDetailView.BRANCHES) {
    url += ',branches';
  } else if (state.detail === RepoDetailView.TAGS) {
    url += ',tags';
  } else if (state.detail === RepoDetailView.COMMANDS) {
    url += ',commands';
  } else if (state.detail === RepoDetailView.DASHBOARDS) {
    url += ',dashboards';
  }
  return getBaseUrl() + url;
}

export const repoViewModelToken = define<RepoViewModel>('repo-view-model');

export class RepoViewModel extends Model<RepoViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
