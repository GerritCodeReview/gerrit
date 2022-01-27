/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ChangeId} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Subscription} from 'rxjs';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

export interface BulkActionsState {
  selectedChanges: ChangeId[];
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

  private selectedChanges$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChanges
  );

  addSelectedChange(changedId: ChangeId) {
    const current = this.subject$.getValue();
    const selectedChanges = [...current.selectedChanges];
    selectedChanges.push(changedId);
    this.setState({...current, selectedChanges});
  }

  removeSelectedChange(changedId: ChangeId) {
    const current = this.subject$.getValue();
    const selectedChanges = [...current.selectedChanges];
    const index = selectedChanges.findIndex(item => item === changedId);
    if (index === -1) return;
    selectedChanges.splice(index, 1);
    this.setState({...current, selectedChanges});
  }

  setState(state: BulkActionsState) {
    this.subject$.next(state);
  }

  resetState() {
    this.subject$.next(initialState);
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }
}
