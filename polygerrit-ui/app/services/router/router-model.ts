/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';
import {
  NumericChangeId,
  RevisionPatchSetNum,
  BasePatchSetNum,
} from '../../types/common';
import {Model} from '../../models/model';
import {select} from '../../utils/observable-util';
import {GerritView} from '../../elements/gr-app-types';
import {SettingsViewModel} from '../../elements/core/gr-router/settings-view-model';

export {GerritView};

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
  // TODO: Rename to `activeView`.
  readonly routerView$ = select(this.state$, state => state.view);

  // TODO: Move into a ChangeViewModel.
  readonly routerChangeNum$ = select(this.state$, state => state.changeNum);

  // TODO: Move into a ChangeViewModel.
  readonly routerPatchNum$ = select(this.state$, state => state.patchNum);

  // TODO: Move into a ChangeViewModel.
  readonly routerBasePatchNum$ = select(
    this.state$,
    state => state.basePatchNum
  );

  readonly settings = new SettingsViewModel();

  constructor() {
    super({});
  }

  finalize() {}

  // Private but used in tests
  setState(state: RouterState) {
    this.subject$.next(state);
  }

  setActiveView(view: GerritView) {
    this.setState({view});
  }

  updateState(partial: Partial<RouterState>) {
    this.subject$.next({
      ...this.subject$.getValue(),
      ...partial,
    });
  }
}
