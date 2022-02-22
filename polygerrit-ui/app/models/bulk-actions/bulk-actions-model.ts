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
import {
  listChangesOptionsToHex,
  ListChangesOption,
} from '../../utils/change-util';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

export interface BulkActionsState {
  loading: boolean;
  selectedChangeIds: ChangeInfoId[];
}

const initialState: BulkActionsState = {
  loading: false,
  selectedChangeIds: [],
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  // A map of all the changes in a section that can be bulk-acted upon.
  // Private but used in tests.
  allChanges: Map<ChangeInfoId, ChangeInfo> = new Map();

  constructor(private readonly restApiService: RestApiService) {
    super(initialState);
  }

  public readonly selectedChangeIds$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeIds
  );

  public readonly loading$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.loading
  );

  addSelectedChangeId(changeId: ChangeInfoId) {
    if (!this.allChanges.has(changeId)) {
      throw new Error(
        `Trying to add change ${changeId} that is not part of bulk-actions model`
      );
    }
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    selectedChangeIds.push(changeId);
    this.setState({...current, selectedChangeIds});
  }

  removeSelectedChangeId(changeId: ChangeInfoId) {
    if (!this.allChanges.has(changeId)) {
      throw new Error(
        `Trying to remove change ${changeId} that is not part of bulk-actions model`
      );
    }
    const current = this.subject$.getValue();
    const selectedChangeIds = [...current.selectedChangeIds];
    const index = selectedChangeIds.findIndex(item => item === changeId);
    if (index === -1) return;
    selectedChangeIds.splice(index, 1);
    this.setState({...current, selectedChangeIds});
  }

  async sync(changes: ChangeInfo[]) {
    const newChanges = new Map(changes.map(c => [c.id, c]));
    this.allChanges = newChanges;
    const current = this.subject$.getValue();
    const selectedChangeIds = current.selectedChangeIds.filter(changeId =>
      newChanges.has(changeId)
    );
    this.setState({...current, loading: true, selectedChangeIds});

    const query = changes.map(c => `change:${c.id}`).join(' OR ');
    const changeDetails = await this.restApiService.getChanges(
      undefined,
      query,
      undefined,
      listChangesOptionsToHex(
        ListChangesOption.CHANGE_ACTIONS,
        ListChangesOption.CURRENT_ACTIONS,
        ListChangesOption.CURRENT_REVISION,
        ListChangesOption.DETAILED_LABELS,
        ListChangesOption.SUBMIT_REQUIREMENTS,
        ListChangesOption.SUBMITTABLE
      )
    );
    // Return early if sync has been called again since starting the load.
    if (newChanges !== this.allChanges) return;
    for (const change of changeDetails ?? []) {
      this.allChanges.set(change.id, change);
    }
    this.setState({...this.subject$.getValue(), loading: false});
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
