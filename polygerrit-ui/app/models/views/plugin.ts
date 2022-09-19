/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export interface PluginViewState extends ViewState {
  view: GerritView.PLUGIN_SCREEN;
  plugin?: string;
  screen?: string;
}

const DEFAULT_STATE: PluginViewState = {view: GerritView.PLUGIN_SCREEN};

export const pluginViewModelToken =
  define<PluginViewModel>('plugin-view-model');

export class PluginViewModel extends Model<PluginViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
