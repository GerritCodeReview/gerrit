/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, ChangeInfoId} from '../../api/rest-api';
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
  // A map of all the changes in a section that can be bulk-acted upon.
  private allChanges: Map<ChangeInfoId, ChangeInfo> = new Map();

  constructor(_restApiService: RestApiService) {
    super(initialState);
  }

  public readonly selectedChangeIds$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeIds
  );

  addSelectedChangeId(changeId: ChangeInfoId) {
    if (!this.allChanges.has(changeId)) return;
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    selectedChangeIds.push(changeId);
    this.setState({...current, selectedChangeIds});
  }

  removeSelectedChangeId(changeId: ChangeInfoId) {
    if (!this.allChanges.has(changeId)) return;
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    const index = selectedChangeIds.findIndex(item => item === changeId);
    if (index === -1) return;
    selectedChangeIds.splice(index, 1);
    this.setState({...current, selectedChangeIds});
  }

  sync(changes: ChangeInfo[]) {
    this.allChanges = new Map(changes.map(c => [c.id, c]));
    const current = this.subject$.getValue();
    const selectedChangeIds = current.selectedChangeIds.filter(changeId =>
      this.allChanges.has(changeId)
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
