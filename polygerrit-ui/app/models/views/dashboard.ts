/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {DashboardId} from '../../types/common';
import {DashboardSection} from '../../utils/router-util';
import {Model} from '../model';
import {ViewState} from './base';

export interface DashboardViewState extends ViewState {
  view: GerritView.DASHBOARD;
  project?: RepoName;
  dashboard?: DashboardId;
  user?: string;
  sections?: DashboardSection[];
  title?: string;
}

const DEFAULT_STATE: DashboardViewState = {
  view: GerritView.DASHBOARD,
};

export class DashboardViewModel extends Model<DashboardViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
