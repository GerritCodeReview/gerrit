/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export interface PluginViewState extends ViewState {
  view: GerritView.PLUGIN_SCREEN;
  plugin?: string;
  screen?: string;
}

const DEFAULT_STATE: PluginViewState = {view: GerritView.PLUGIN_SCREEN};

export class PluginViewModel extends Model<PluginViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: PluginViewState) {
    this.subject$.next({...state});
  }
}
