/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {RepoName} from '../../types/common';
import {Model} from '../model';
import {PageState} from './base';

export enum RepoChildPage {
  GENERAL = 'general',
  ACCESS = 'access',
  BRANCHES = 'branches',
  COMMANDS = 'commands',
  DASHBOARDS = 'dashboards',
  TAGS = 'tags',
}

export interface RepoPageState extends PageState {
  view: GerritView.REPO;
  childPage?: RepoChildPage;
  repo?: RepoName;
  filter?: string | null;
  offset?: number | string;
}

const DEFAULT_STATE: RepoPageState = {
  view: GerritView.REPO,
};

export class RepoPageModel extends Model<RepoPageState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: RepoPageState) {
    this.subject$.next({...state});
  }
}
