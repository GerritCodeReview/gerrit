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
import {ProgressStatus} from '../../constants/constants';

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
  progress: Map<NumericChangeId, ProgressStatus>;
}

const initialState: BulkActionsState = {
  loadingState: LoadingState.NOT_SYNCED,
  selectedChangeNums: [],
  progress: new Map(),
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

  public readonly loadingState$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.loadingState
  );

  public readonly progress$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.progress
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

  async abandonChanges(reason?: string) {
    const current = this.subject$.getValue();
    const selectedChangeNums = [...current.selectedChangeNums];
    const progress = new Map();
    selectedChangeNums.forEach(id => progress.set(id, ProgressStatus.RUNNING));
    this.setState({...current, progress});
    return Promise.all(
      selectedChangeNums.map(changeId => {
        if (!this.allChanges.get(changeId))
          throw new Error('invalid change id');
        const change = this.allChanges.get(changeId)!;
        const errFn = () => {
          const current = this.subject$.getValue();
          const progress = new Map(current.progress);
          progress.set(changeId, ProgressStatus.FAILED);
          this.setState({...current, progress});
        };
        return this.restApiService
          .executeChangeAction(
            change._number,
            change.actions!.abandon!.method,
            '/abandon',
            undefined,
            {message: reason ?? ''},
            errFn
          )
          .then(() => {
            const current = this.subject$.getValue();
            const progress = new Map(current.progress);
            progress.set(changeId, ProgressStatus.SUCCESSFUL);
            this.setState({...current, progress});
          });
      })
    );
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
