/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Subscription} from 'rxjs';

export interface BulkActionsState {
  selectedChanges: ChangeInfo[];
}

const initialState: BulkActionsState = {
  selectedChanges: [],
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  private subscriptions: Subscription[] = [];

  constructor(_restApiService: RestApiService) {
    super(initialState);
  }

  addSelectedChange(change: ChangeInfo) {
    const current = this.subject$.getValue();
    const selectedChanges = [...current.selectedChanges];
    selectedChanges.push(change);
    this.setState({...current, selectedChanges});
  }

  removeSelectedChange(change: ChangeInfo) {
    const current = this.subject$.getValue();
    const selectedChanges = [...current.selectedChanges];
    const index = selectedChanges.findIndex(item => item.id === change.id);
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

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }
}
