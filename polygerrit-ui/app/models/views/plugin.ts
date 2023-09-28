/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {select} from '../../utils/observable-util';
import {define} from '../dependency';
import {Model} from '../base/model';
import {ViewState} from './base';

/**
 * This is simple hacky way for allowing certain plugin screens to hide the
 * header and the footer of the Gerrit page.
 */
export const ALLOW_LISTED_FULL_SCREEN_PLUGINS = [
  'git_source_editor/screen/edit',
];

export function screenName(plugin?: string, screen?: string) {
  if (!plugin || !screen) return '';
  return `${plugin}-screen-${screen}`;
}

export interface PluginViewState extends ViewState {
  view: GerritView.PLUGIN_SCREEN;
  plugin?: string;
  screen?: string;
}

const DEFAULT_STATE: PluginViewState = {view: GerritView.PLUGIN_SCREEN};

export const pluginViewModelToken =
  define<PluginViewModel>('plugin-view-model');

export class PluginViewModel extends Model<PluginViewState> {
  public readonly screenName$ = select(this.state$, state =>
    screenName(state.plugin, state.screen)
  );

  constructor() {
    super(DEFAULT_STATE);
  }
}
