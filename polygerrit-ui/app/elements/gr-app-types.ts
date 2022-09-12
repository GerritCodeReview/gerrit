/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../types/common';
import {GerritView} from '../services/router/router-model';
import {GenerateUrlParameters} from '../utils/router-util';
import {AttemptChoice} from '../models/checks/checks-util';
import {SettingsViewState} from '../models/views/settings';
import {AdminViewState} from '../models/views/admin';
import {GroupViewState} from '../models/views/group';
import {RepoViewState} from '../models/views/repo';
import {AgreementViewState} from '../models/views/agreement';
import {DocumentationViewState} from '../models/views/documentation';
import {PluginViewState} from '../models/views/plugin';
import {SearchViewState} from '../models/views/search';
import {DashboardViewState} from '../models/views/dashboard';

export interface AppElement extends HTMLElement {
  params: AppElementParams | GenerateUrlParameters;
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
  /** selected attempt for selected check runs */
  attempt?: AttemptChoice;
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
  | AppElementChangeViewParams
  | RepoViewState
  | DocumentationViewState
  | PluginViewState
  | SearchViewState
  | SettingsViewState
  | AgreementViewState
  | AppElementDiffViewParam
  | AppElementDiffEditViewParam
  | AppElementJustRegisteredParams;

export function isAppElementJustRegisteredParams(
  p: AppElementParams
): p is AppElementJustRegisteredParams {
  return (p as AppElementJustRegisteredParams).justRegistered !== undefined;
}
