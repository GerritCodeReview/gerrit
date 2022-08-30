/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';
import {Model} from '../../models/model';
import {select} from '../../utils/observable-util';
import {GerritView} from '../../elements/gr-app-types';
import {SettingsViewModel} from '../../elements/core/gr-router/settings-view-model';
import {
  ChangeViewModel,
  ChangeViewState,
} from '../../elements/core/gr-router/change-view-model';
import {getBaseUrl} from '../../utils/url-util';

export {GerritView};

// TODO: Maybe rename to just AppElementState
export interface RouterState {
  // TODO: Rename to `activeView`.
  view?: GerritView;
}

// TODO: Maybe rename to just AppElementModel
export class RouterModel extends Model<RouterState> implements Finalizable {
  // TODO: Rename to `activeView`.
  readonly routerView$ = select(this.state$, state => state.view);

  // TODO: Is this the right place to expose all the view models?
  // We don't really want to stick all the view models onto the app context
  // individuall, so there must be some place to look them all up.
  readonly settings = new SettingsViewModel();

  readonly change = new ChangeViewModel();

  constructor() {
    super({});
  }

  finalize() {}

  settingsUrl() {
    const state = this.settings.defaultState;
    return getBaseUrl() + this.settings.stateToUrl(state);
  }

  changeUrl(state: Partial<ChangeViewState>) {
    return getBaseUrl() + this.change.stateToUrl(state);
  }

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
