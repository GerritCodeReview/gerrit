/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {GroupId} from '../../types/common';
import {Model} from '../model';
import {PageState} from './base';

export enum GroupChildPage {
  MEMBERS = 'members',
  LOG = 'log',
}

export interface GroupPageState extends PageState {
  view: GerritView.GROUP;
  childPage?: GroupChildPage;
  groupId?: GroupId;
}

const DEFAULT_STATE: GroupPageState = {
  view: GerritView.GROUP,
};

export class GroupPageModel extends Model<GroupPageState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: GroupPageState) {
    this.subject$.next({...state});
  }
}
