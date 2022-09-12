/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export enum AdminChildView {
  REPOS = 'gr-repo-list',
  GROUPS = 'gr-admin-group-list',
  PLUGINS = 'gr-plugin-list',
}
export interface AdminViewState extends ViewState {
  view: GerritView.ADMIN;
  adminView: AdminChildView;
  openCreateModal?: boolean;
  filter?: string | null;
  offset?: number | string;
}

const DEFAULT_STATE: AdminViewState = {
  view: GerritView.ADMIN,
  adminView: AdminChildView.REPOS,
};

export class AdminViewModel extends Model<AdminViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: AdminViewState) {
    this.subject$.next({...state});
  }
}
