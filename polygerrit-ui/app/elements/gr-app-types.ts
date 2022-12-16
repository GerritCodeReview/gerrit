/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {SettingsViewState} from '../models/views/settings';
import {AdminViewState} from '../models/views/admin';
import {GroupViewState} from '../models/views/group';
import {RepoViewState} from '../models/views/repo';
import {AgreementViewState} from '../models/views/agreement';
import {DocumentationViewState} from '../models/views/documentation';
import {PluginViewState} from '../models/views/plugin';
import {SearchViewState} from '../models/views/search';
import {DashboardViewState} from '../models/views/dashboard';
import {ChangeViewState} from '../models/views/change';

export interface AppElement extends HTMLElement {
  params: AppElementParams;
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
  | DashboardViewState
  | GroupViewState
  | AdminViewState
  | ChangeViewState
  | RepoViewState
  | DocumentationViewState
  | PluginViewState
  | SearchViewState
  | SettingsViewState
  | AgreementViewState
  | AppElementJustRegisteredParams;

export function isAppElementJustRegisteredParams(
  p: AppElementParams
): p is AppElementJustRegisteredParams {
  return (p as AppElementJustRegisteredParams).justRegistered !== undefined;
}
