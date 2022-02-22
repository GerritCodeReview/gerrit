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
import {
  listChangesOptionsToHex,
  ListChangesOption,
} from '../../utils/change-util';
import {combineLatest} from 'rxjs';

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
  totalChangeCount: number;
}

const initialState: BulkActionsState = {
  loadingState: LoadingState.NOT_SYNCED,
  selectedChangeNums: [],
  totalChangeCount: 0,
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  // A map of all the changes in a section that can be bulk-acted upon.
  // Private but used in tests.
  allChanges: Map<NumericChangeId, ChangeInfo> = new Map();

  constructor(private readonly restApiService: RestApiService) {
    super(initialState);
  }

  public readonly selectedChangeNums$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeNums
  );

  public readonly totalChangeCount$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.totalChangeCount
  );

  public readonly loadingState$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.loadingState
  );

  public readonly abandonable$ = select(
    combineLatest([this.selectedChangeNums$, this.loadingState$]),
    ([selectedChangeNums, loadingState]) => {
      if (loadingState !== LoadingState.LOADED) return false;
      return selectedChangeNums.every(selectedChangeNum => {
        const change = this.allChanges.get(selectedChangeNum);
        if (!change) throw new Error('invalid changeId in model');
        return !!change.actions!.abandon;
      });
    }
  );

  addSelectedChangeNum(changeNum: NumericChangeId) {
    if (!this.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to add change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const current = this.subject$.getValue();
    const selectedChangeNums = [...current.selectedChangeNums];
    selectedChangeNums.push(changeNum);
    this.setState({...current, selectedChangeNums});
  }

  removeSelectedChangeNum(changeNum: NumericChangeId) {
    if (!this.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to remove change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const current = this.subject$.getValue();
    const selectedChangeNums = [...current.selectedChangeNums];
    const index = selectedChangeNums.findIndex(item => item === changeNum);
    if (index === -1) return;
    selectedChangeNums.splice(index, 1);
    this.setState({...current, selectedChangeNums});
  }

  clearSelectedChangeNums() {
    this.setState({...this.subject$.getValue(), selectedChangeNums: []});
  }

  // TODO: remove once detailed changes are stored in the model
  getChange(changeNum: NumericChangeId): ChangeInfo {
    if (!this.allChanges.has(changeNum)) {
      throw new Error(`${changeNum} is not part of bulk-actions model`);
    }
    return this.allChanges.get(changeNum)!;
  }

  abandonChanges(
    reason?: string,
    errFn?: (changeNum: NumericChangeId) => void
  ): Promise<Response | undefined>[] {
    const current = this.subject$.getValue();
    return current.selectedChangeNums.map(changeNum => {
      if (!this.allChanges.get(changeNum)) throw new Error('invalid change id');
      const change = this.allChanges.get(changeNum)!;
      return this.restApiService.executeChangeAction(
        change._number,
        change.actions!.abandon!.method,
        '/abandon',
        undefined,
        {message: reason ?? ''},
        () => errFn && errFn(change._number)
      );
    });
  }

  async sync(changes: ChangeInfo[]) {
    const newChanges = new Map(changes.map(c => [c._number, c]));
    this.allChanges = newChanges;
    const current = this.subject$.getValue();
    const selectedChangeNums = current.selectedChangeNums.filter(changeNum =>
      newChanges.has(changeNum)
    );
    this.setState({
      ...current,
      loadingState: LoadingState.LOADING,
      selectedChangeNums,
      totalChangeCount: this.allChanges.size,
    });

    const query = changes.map(c => `change:${c._number}`).join(' OR ');
    const changeDetails = await this.restApiService.getChanges(
      undefined,
      query,
      undefined,
      listChangesOptionsToHex(
        ListChangesOption.CHANGE_ACTIONS,
        ListChangesOption.CURRENT_ACTIONS,
        ListChangesOption.CURRENT_REVISION,
        ListChangesOption.DETAILED_LABELS
      )
    );
    // Return early if sync has been called again since starting the load.
    if (newChanges !== this.allChanges) return;
    for (const change of changeDetails ?? []) {
      this.allChanges.set(change._number, change);
    }
    this.setState({
      ...this.subject$.getValue(),
      loadingState: LoadingState.LOADED,
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
