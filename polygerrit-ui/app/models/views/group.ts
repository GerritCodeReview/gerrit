/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {GroupId} from '../../types/common';
import {encodeURL, getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export enum GroupDetailView {
  MEMBERS = 'members',
  LOG = 'log',
}

export interface GroupViewState extends ViewState {
  view: GerritView.GROUP;
  groupId: GroupId;
  detail?: GroupDetailView;
}

export function createGroupUrl(state: Omit<GroupViewState, 'view'>) {
  let url = `/admin/groups/${encodeURL(`${state.groupId}`)}`;
  if (state.detail === GroupDetailView.MEMBERS) {
    url += ',members';
  } else if (state.detail === GroupDetailView.LOG) {
    url += ',audit-log';
  }
  return getBaseUrl() + url;
}

export const groupViewModelToken = define<GroupViewModel>('group-view-model');

export class GroupViewModel extends Model<GroupViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
