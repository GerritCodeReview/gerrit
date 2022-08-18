/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Model} from '../../../models/model';
import {GerritView} from '../../../services/router/router-model';
import {select} from '../../../utils/observable-util';
import {ViewModel, ViewState} from '../../gr-app-types';
import {PageContextWithQueryMap} from './gr-router';

export interface SettingsViewState extends ViewState {
  emailToken?: string;
}

const DEFAULT_STATE = {view: GerritView.SETTINGS};

export class SettingsViewModel
  extends Model<SettingsViewState>
  implements Required<ViewModel<SettingsViewState>>
{
  view = GerritView.SETTINGS;

  defaultState = DEFAULT_STATE;

  loginRequired = true;

  routes = [
    {
      name: 'settings',
      pattern: /^\/settings\/?/,
      urlToState: (_: PageContextWithQueryMap) => DEFAULT_STATE,
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
          ...DEFAULT_STATE,
          emailToken: token,
        };
      },
    },
  ];

  constructor() {
    super(DEFAULT_STATE);
  }

  public readonly emailToken$ = select(this.state$, state => state.emailToken);

  updateState(state: SettingsViewState) {
    this.subject$.next({...state});
  }

  // TODO: After converting to state we have to call either setParams() or
  // devise some other way of updating the model.

  stateToUrl = (_: SettingsViewState) => '/settings';
}
