/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {Model} from '../../models/model';
import {select} from '../../utils/observable-util';
import {define} from '../../models/dependency';

export enum GerritView {
  ADMIN = 'admin',
  AGREEMENTS = 'agreements',
  CHANGE = 'change',
  DASHBOARD = 'dashboard',
  DOCUMENTATION_SEARCH = 'documentation-search',
  GROUP = 'group',
  PLUGIN_SCREEN = 'plugin-screen',
  REPO = 'repo',
  SEARCH = 'search',
  SETTINGS = 'settings',
}

export interface RouterState {
  // Note that this router model view must be updated before view model state.
  view?: GerritView;
}

export const routerModelToken = define<RouterModel>('router-model');
export class RouterModel extends Model<RouterState> {
  readonly routerView$: Observable<GerritView | undefined> = select(
    this.state$,
    state => state.view
  );

  constructor() {
    super({});
  }
}
