/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../../services/router/router-model';
import {ViewModel, ViewState} from '../../gr-app-types';
import {PageContextWithQueryMap} from './gr-router';

export interface SettingsViewState extends ViewState {
  emailToken?: string;
}

export type SettingsViewModel = Required<ViewModel<SettingsViewState>>;

export const SETTINGS_VIEW: SettingsViewModel = {
  view: GerritView.SETTINGS,

  defaultState: {view: GerritView.SETTINGS},

  loginRequired: true,

  routes: [
    {
      name: 'settings',
      pattern: /^\/settings\/?/,
      urlToState: (_: PageContextWithQueryMap) => {
        return {view: GerritView.SETTINGS};
      },
    },
    {
      name: 'settings email confirmation',
      pattern: /^\/settings\/VE\/(\S+)/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        // email tokens may contain '+' but no space.
        // The parameter parsing replaces all '+' with a space,
        // undo that to have valid tokens.
        const token = ctx.params[0].replace(/ /g, '+');
        return {
          view: GerritView.SETTINGS,
          emailToken: token,
        };
      },
    },
  ],

  // TODO: After converting to state we have to call either setParams() or
  // devise some other way of updating the model.

  stateToUrl: (_: SettingsViewState) => '/settings',
};
