/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {Route, ViewState} from './base';

export const PLUGIN_LIST: Route<AdminViewState> = {
  urlPattern: /^\/admin\/plugins(\/)?$/,
  createState: () => {
    const state: AdminViewState = {
      view: GerritView.ADMIN,
      adminView: AdminChildView.PLUGINS,
    };
    return state;
  },
};

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

export function createAdminUrl(state: Omit<AdminViewState, 'view'>) {
  switch (state.adminView) {
    case AdminChildView.REPOS:
      return `${getBaseUrl()}/admin/repos`;
    case AdminChildView.GROUPS:
      return `${getBaseUrl()}/admin/groups`;
    case AdminChildView.PLUGINS:
      return `${getBaseUrl()}/admin/plugins`;
  }
}

export const adminViewModelToken = define<AdminViewModel>('admin-view-model');

export class AdminViewModel extends Model<AdminViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
