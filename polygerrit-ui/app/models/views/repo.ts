/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {RepoName} from '../../types/common';
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
}

const DEFAULT_STATE: RepoViewState = {
  view: GerritView.REPO,
};

export class RepoViewModel extends Model<RepoViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
