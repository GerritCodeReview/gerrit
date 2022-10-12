/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {Finalizable} from '../registry';
import {
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
} from '../../types/common';
import {Model} from '../../models/model';
import {select} from '../../utils/observable-util';

export enum GerritView {
  ADMIN = 'admin',
  AGREEMENTS = 'agreements',
  CHANGE = 'change',
  DASHBOARD = 'dashboard',
  DIFF = 'diff',
  DOCUMENTATION_SEARCH = 'documentation-search',
  EDIT = 'edit',
  GROUP = 'group',
  PLUGIN_SCREEN = 'plugin-screen',
  REPO = 'repo',
  SEARCH = 'search',
  SETTINGS = 'settings',
}

export interface RouterState {
  // Note that this router model view must be updated before view model state.
  view?: GerritView;
  changeNum?: NumericChangeId;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
}

export class RouterModel extends Model<RouterState> implements Finalizable {
  readonly routerView$: Observable<GerritView | undefined> = select(
    this.state$,
    state => state.view
  );

  readonly routerChangeNum$: Observable<NumericChangeId | undefined> = select(
    this.state$,
    state => state.changeNum
  );

  readonly routerPatchNum$: Observable<RevisionPatchSetNum | undefined> =
    select(this.state$, state => state.patchNum);

  readonly routerBasePatchNum$: Observable<BasePatchSetNum | undefined> =
    select(this.state$, state => state.basePatchNum);

  constructor() {
    super({});
  }
}
