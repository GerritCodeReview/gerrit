/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {PageState} from './base';

export enum AdminChildPage {
  REPOS = 'gr-repo-list',
  GROUPS = 'gr-admin-group-list',
  PLUGINS = 'gr-plugin-list',
}
export interface AdminPageState extends PageState {
  // TODO: Rename to `page`.
  view: GerritView.ADMIN;
  adminView: AdminChildPage;
  openCreateModal?: boolean;
  filter?: string | null;
  offset?: number | string;
}

const DEFAULT_STATE: AdminPageState = {
  view: GerritView.ADMIN,
  adminView: AdminChildPage.REPOS,
};

export class AdminPageModel extends Model<AdminPageState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: AdminPageState) {
    this.subject$.next({...state});
  }
}
