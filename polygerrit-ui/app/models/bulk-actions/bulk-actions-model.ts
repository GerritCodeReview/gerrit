/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfoId} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {define} from '../dependency';
import {select} from '../../utils/observable-util';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

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

  public readonly selectedChangeIds$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeIds
  );

  addSelectedChangeId(changeId: ChangeInfoId) {
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    selectedChangeIds.push(changeId);
    this.setState({...current, selectedChangeIds});
  }

  removeSelectedChangeId(changeId: ChangeInfoId) {
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    const index = selectedChangeIds.findIndex(item => item === changeId);
    if (index === -1) return;
    selectedChangeIds.splice(index, 1);
    this.setState({...current, selectedChangeIds});
  }

  sync(visibleChangesId: ChangeInfoId[]) {
    const current = this.subject$.getValue();
    const selectedChangeIds = current.selectedChangeIds.filter(changeId =>
      visibleChangesId.includes(changeId)
    );
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
