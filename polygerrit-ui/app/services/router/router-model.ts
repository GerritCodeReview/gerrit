/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {distinctUntilChanged, map} from 'rxjs/operators';
import {Finalizable} from '../registry';
import {
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
} from '../../types/common';
import {Model} from '../../models/model';
import {SettingsViewModel} from '../../elements/core/gr-router/gr-settings-page-model';
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
  ROOT = 'root',
  SEARCH = 'search',
  SETTINGS = 'settings',
}

export interface RouterState {
  // TODO: Rename to `activeView`.
  view?: GerritView;
  // TODO: Move into a ChangeViewState.
  changeNum?: NumericChangeId;
  // TODO: Move into a ChangeViewState.
  patchNum?: RevisionPatchSetNum;
  // TODO: Move into a ChangeViewState.
  basePatchNum?: BasePatchSetNum;
}

export class RouterModel extends Model<RouterState> implements Finalizable {
  readonly routerView$: Observable<GerritView | undefined>;

  readonly routerChangeNum$: Observable<NumericChangeId | undefined>;

  readonly routerPatchNum$: Observable<RevisionPatchSetNum | undefined>;

  readonly routerBasePatchNum$: Observable<BasePatchSetNum | undefined>;

  readonly settings = new SettingsViewModel();

  public readonly activeView$ = select(this.state$, state => state.view);

  constructor() {
    super({});
    this.routerView$ = this.state$.pipe(
      map(state => state.view),
      distinctUntilChanged()
    );
    this.routerChangeNum$ = this.state$.pipe(
      map(state => state.changeNum),
      distinctUntilChanged()
    );
    this.routerPatchNum$ = this.state$.pipe(
      map(state => state.patchNum),
      distinctUntilChanged()
    );
    this.routerBasePatchNum$ = this.state$.pipe(
      map(state => state.basePatchNum),
      distinctUntilChanged()
    );
  }

  finalize() {}

  // Private but used in tests
  setState(state: RouterState) {
    this.subject$.next(state);
  }

  updateState(partial: Partial<RouterState>) {
    this.subject$.next({
      ...this.subject$.getValue(),
      ...partial,
    });
  }
}
