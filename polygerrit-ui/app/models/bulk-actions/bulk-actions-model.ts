/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, NumericChangeId} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {define} from '../dependency';
import {select} from '../../utils/observable-util';
import {combineLatest} from 'rxjs';
import {map} from 'rxjs/operators';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

export enum LoadingState {
  NOT_SYNCED = 'NOT_SYNCED',
  LOADING = 'LOADING',
  LOADED = 'LOADED',
}
export interface BulkActionsState {
  loadingState: LoadingState;
  selectedChangeNums: NumericChangeId[];
  allChanges: Map<NumericChangeId, ChangeInfo>;
}

const initialState: BulkActionsState = {
  loadingState: LoadingState.NOT_SYNCED,
  selectedChangeNums: [],
  allChanges: new Map(),
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  constructor(private readonly restApiService: RestApiService) {
    super(initialState);
  }

  public readonly selectedChangeNums$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeNums
  );

  public readonly totalChangeCount$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.allChanges.size
  );

  public readonly loadingState$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.loadingState
  );

  public readonly allChanges$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.allChanges
  );

  public readonly selectedChanges$ = combineLatest([
    this.selectedChangeNums$,
    this.allChanges$,
  ]).pipe(
    map(([selected, allChanges]) => {
      const result = [];
      for (const changeNum of selected) {
        const change = allChanges.get(changeNum);
        if (change) result.push(change);
      }
      return result;
    })
  );

  addSelectedChangeNum(changeNum: NumericChangeId) {
    const current = this.getState();
    if (!current.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to add change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const selectedChangeNums = [...current.selectedChangeNums];
    selectedChangeNums.push(changeNum);
    this.setState({...current, selectedChangeNums});
  }

  removeSelectedChangeNum(changeNum: NumericChangeId) {
    const current = this.getState();
    if (!current.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to remove change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const selectedChangeNums = [...current.selectedChangeNums];
    const index = selectedChangeNums.findIndex(item => item === changeNum);
    if (index === -1) return;
    selectedChangeNums.splice(index, 1);
    this.setState({...current, selectedChangeNums});
  }

  clearSelectedChangeNums() {
    this.setState({...this.subject$.getValue(), selectedChangeNums: []});
  }

  async abandonChanges(reason?: string) {
    const current = this.subject$.getValue();
    const selectedChangeNums = [...current.selectedChangeNums];
    return Promise.all(
      selectedChangeNums.map(changeId => {
        if (!this.allChanges.get(changeId))
          throw new Error('invalid change id');
        const change = this.allChanges.get(changeId)!;
        return this.restApiService.executeChangeAction(
          change._number,
          change.actions!.abandon!.method,
          '/abandon',
          undefined,
          {message: reason ?? ''}
        );
      })
    );
  }

  async sync(changes: ChangeInfo[]) {
    const newChanges = new Map(changes.map(c => [c._number, c]));
    const current = this.subject$.getValue();
    const selectedChangeNums = current.selectedChangeNums.filter(changeNum =>
      newChanges.has(changeNum)
    );
    this.setState({
      ...current,
      loadingState: LoadingState.LOADING,
      selectedChangeNums,
      allChanges: newChanges,
    });

    const changeDetails =
      await this.restApiService.getDetailedChangesWithActions(
        changes.map(c => c._number)
      );
    const newCurrent = this.subject$.getValue();
    // Return early if sync has been called again since starting the load.
    if (newChanges !== newCurrent.allChanges) return;
    const allDetailedChanges = new Map(newChanges);
    for (const change of changeDetails ?? []) {
      allDetailedChanges.set(change._number, change);
    }
    this.setState({
      ...newCurrent,
      loadingState: LoadingState.LOADED,
      allChanges: allDetailedChanges,
    });
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
