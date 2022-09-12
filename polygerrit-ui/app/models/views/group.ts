/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {GroupId} from '../../types/common';
import {Model} from '../model';
import {ViewState} from './base';

export enum GroupDetailView {
  MEMBERS = 'members',
  LOG = 'log',
}

export interface GroupViewState extends ViewState {
  view: GerritView.GROUP;
  detail?: GroupDetailView;
  groupId?: GroupId;
}

const DEFAULT_STATE: GroupViewState = {
  view: GerritView.GROUP,
};

export class GroupViewModel extends Model<GroupViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
