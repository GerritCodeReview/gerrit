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
  selectedChanges: ChangeInfoId[];
}

const initialState: BulkActionsState = {
  selectedChanges: [],
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
    const selectedChanges = [...current.selectedChanges];
    selectedChanges.push(change.id);
    this.setState({...current, selectedChanges});
  }

  removeSelectedChange(change: ChangeInfo) {
    const current = this.subject$.getValue();
    const selectedChanges = [...current.selectedChanges];
    const index = selectedChanges.findIndex(item => item === change.id);
    if (index === -1) return;
    selectedChanges.splice(index, 1);
    this.setState({...current, selectedChanges});
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
