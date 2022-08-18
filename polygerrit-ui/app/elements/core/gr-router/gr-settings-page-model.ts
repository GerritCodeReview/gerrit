/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../../services/router/router-model';
import {Page, PageState} from '../../gr-app-types';
import {PageContextWithQueryMap} from './gr-router';

export interface SettingsPageState extends PageState {
  emailToken?: string;
}

export type SettingsPage = Required<Page<SettingsPageState>>;

export const SETTINGS_PAGE: SettingsPage = {
  view: GerritView.SETTINGS,

  defaultState: {view: GerritView.SETTINGS},

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

  stateToUrl: (_: SettingsPageState) => '/settings',
};
