/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
// TODO: Rename this file from gr-app-types to router-types.
import {
  BasePatchSetNum,
  DashboardId,
  GroupId,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../types/common';
import {PageContextWithQueryMap} from '../utils/page-wrapper-utils';
import {
  GenerateUrlParameters,
  DashboardSection,
  GroupDetailView,
  RepoDetailView,
} from '../utils/router-util';
import {SettingsViewState} from './core/gr-router/settings-view-model';

export enum GerritView {
  ADMIN = 'admin',
  AGREEMENTS = 'agreements',
  CHANGE = 'change',
  DASHBOARD = 'dashboard',
  DIFF = 'diff',
  DOCUMENTATION_SEARCH = 'documentation-search',
  EDIT = 'edit',
  GROUP = 'group',
  PLUGIN_SCREEN = 'plugin-screen',
  REPO = 'repo',
  ROOT = 'root',
  SEARCH = 'search',
  SETTINGS = 'settings',
}

export interface ViewState {
  view: GerritView;
}

export interface Route<S extends ViewState> {
  name: string;
  pattern: string | RegExp;
  urlToState: (data: PageContextWithQueryMap) => S;
}

export interface ViewModel<S extends ViewState> {
  view: GerritView;
  routes: Route<S>[];
  /**
   * This is the only public method interesting for the entire app.
   * Everything else is just interesting for the router and should be
   * considered "package protected".
   * TODO: Is there a better way to model that than introducing another
   * interface?
   */
  stateToUrl: (state: S) => string;
  updateState: (state: S) => void;
  defaultState?: S;
  loginRequired: boolean;
}

export interface AppElement extends HTMLElement {
  params: AppElementParams | GenerateUrlParameters;
}

// TODO(TS): Remove unify AppElementParams with GenerateUrlParameters
// Seems we can use GenerateUrlParameters instead of AppElementParams,
// but it require some refactoring
export interface AppElementDashboardParams {
  view: GerritView.DASHBOARD;
  project?: RepoName;
  dashboard: DashboardId;
  user?: string;
  sections?: DashboardSection[];
  title?: string;
}

export interface AppElementGroupParams {
  view: GerritView.GROUP;
  detail?: GroupDetailView;
  groupId: GroupId;
}

export interface ListViewParams {
  filter?: string | null;
  offset?: number | string;
}

export interface AppElementAdminParams extends ListViewParams {
  view: GerritView.ADMIN;
  adminView: string;
  openCreateModal?: boolean;
}

export interface AppElementRepoParams extends ListViewParams {
  view: GerritView.REPO;
  detail?: RepoDetailView;
  repo: RepoName;
}

export interface AppElementDocSearchParams {
  view: GerritView.DOCUMENTATION_SEARCH;
  filter: string | null;
}

export interface AppElementPluginScreenParams {
  view: GerritView.PLUGIN_SCREEN;
  plugin?: string;
  screen?: string;
}

export interface AppElementSearchParam {
  view: GerritView.SEARCH;
  query: string;
  offset: string;
}

export interface AppElementAgreementParam {
  view: GerritView.AGREEMENTS;
}

export interface AppElementDiffViewParam {
  view: GerritView.DIFF;
  changeNum: NumericChangeId;
  project?: RepoName;
  commentId?: UrlEncodedCommentId;
  path?: string;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  lineNum?: number;
  leftSide?: boolean;
  commentLink?: boolean;
}

export interface AppElementDiffEditViewParam {
  view: GerritView.EDIT;
  changeNum: NumericChangeId;
  project: RepoName;
  path: string;
  patchNum: RevisionPatchSetNum;
  lineNum?: number;
}

export interface AppElementChangeViewParams {
  view: GerritView.CHANGE;
  changeNum: NumericChangeId;
  project: RepoName;
  edit?: boolean;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  commentId?: UrlEncodedCommentId;
  forceReload?: boolean;
  openReplyDialog?: boolean;
  tab?: string;
  /** regular expression for filtering check runs */
  filter?: string;
  /** regular expression for selecting check runs */
  select?: string;
  /** selected attempt for selected check runs */
  attempt?: number;
}

export interface AppElementJustRegisteredParams {
  // We use params.view === ... as a type guard.
  // The view?: never tells to the compiler that
  // AppElementJustRegisteredParams can't have view property.
  // Otherwise, the compiler reports an error when the code tries to use
  // the property 'view' of AppElementParams.
  view?: never;
  justRegistered: boolean;
}

export type AppElementParams =
  | AppElementDashboardParams
  | AppElementGroupParams
  | AppElementAdminParams
  | AppElementChangeViewParams
  | AppElementRepoParams
  | AppElementDocSearchParams
  | AppElementPluginScreenParams
  | AppElementSearchParam
  | SettingsViewState
  | AppElementAgreementParam
  | AppElementDiffViewParam
  | AppElementDiffEditViewParam
  | AppElementJustRegisteredParams;

export function isAppElementJustRegisteredParams(
  p: AppElementParams
): p is AppElementJustRegisteredParams {
  return (p as AppElementJustRegisteredParams).justRegistered !== undefined;
}
