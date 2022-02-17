/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, ChangeInfoId} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';

export interface BulkActionsState {
  selectedChangeIds: ChangeInfoId[];
}

const initialState: BulkActionsState = {
  selectedChangeIds: [],
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  constructor(_restApiService: RestApiService) {
    super(initialState);
  }

  addSelectedChange(change: ChangeInfo) {
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    selectedChangeIds.push(change.id);
    this.setState({...current, selectedChangeIds});
  }

  removeSelectedChange(change: ChangeInfo) {
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    const index = selectedChangeIds.findIndex(item => item === change.id);
    if (index === -1) return;
    selectedChangeIds.splice(index, 1);
    this.setState({...current, selectedChangeIds});
  }

  /** Required for testing */
  getState() {
    return this.subject$.getValue();
  }

  setState(state: BulkActionsState) {
    this.subject$.next(state);
  }

  finalize() {}
}
