/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  DashboardSection,
  GenerateUrlParameters,
  GroupDetailView,
  RepoDetailView,
} from './core/gr-navigation/gr-navigation';
import {
  BasePatchSetNum,
  DashboardId,
  GroupId,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../types/common';
import {GerritView} from '../services/router/router-model';

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

export interface AppElementSettingsParam {
  view: GerritView.SETTINGS;
  emailToken?: string;
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
  | AppElementSettingsParam
  | AppElementAgreementParam
  | AppElementDiffViewParam
  | AppElementDiffEditViewParam
  | AppElementJustRegisteredParams;

export function isAppElementJustRegisteredParams(
  p: AppElementParams
): p is AppElementJustRegisteredParams {
  return (p as AppElementJustRegisteredParams).justRegistered !== undefined;
}
