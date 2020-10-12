/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  GenerateUrlParameters,
  GerritView,
  GroupDetailView,
  RepoDetailView,
} from './core/gr-navigation/gr-navigation';
import {DashboardId, GroupId, RepoName} from '../types/common';

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
  sections: Array<{name: string; query: string}>;
  title?: string;
}

export interface AppElementGroupParams {
  view: GerritView.GROUP;
  detail?: GroupDetailView;
  groupId: GroupId;
}

export interface AppElementAdminParams {
  view: GerritView.ADMIN;
  adminView: string;
  offset?: string | number;
  filter?: string | null;
  openCreateModal?: boolean;
}

export interface AppElementRepoParams {
  view: GerritView.REPO;
  detail?: RepoDetailView;
  repo: RepoName;
  offset?: string | number;
  filter?: string | null;
}

export interface AppElementDocSearchParams {
  view: GerritView.DOCUMENTATION_SEARCH;
  filter: string | null;
}

export interface AppElementPluginScreenParams {
  view: GerritView.PLUGIN_SCREEN;
  plugin: string;
  screen: string;
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

export interface AppElementJustRegisteredParams {
  // We use params.view === ... as a type guiard.
  // The view?: never tells to the compiler that
  // AppElementJustRegisteredParams can't have view property.
  // Otherwise, the compiler reports an error when the code tries to use
  // the property 'view' of AppElementParams.
  view?: never;
  justRegistered: true;
}

export type AppElementParams =
  | AppElementDashboardParams
  | AppElementGroupParams
  | AppElementAdminParams
  | AppElementRepoParams
  | AppElementDocSearchParams
  | AppElementPluginScreenParams
  | AppElementSearchParam
  | AppElementSettingsParam
  | AppElementAgreementParam
  | AppElementJustRegisteredParams;
