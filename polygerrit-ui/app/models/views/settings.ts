/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {select} from '../../utils/observable-util';
import {getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../base/model';
import {ViewState} from './base';

export interface SettingsViewState extends ViewState {
  view: GerritView.SETTINGS;
  emailToken?: string;
}

export function createSettingsUrl() {
  return getBaseUrl() + '/settings';
}

export const settingsViewModelToken = define<SettingsViewModel>(
  'settings-view-model'
);

export class SettingsViewModel extends Model<SettingsViewState | undefined> {
  constructor() {
    super(undefined);
  }

  public emailToken$ = select(this.state$, state => state?.emailToken);

  clearToken() {
    this.updateState({emailToken: undefined});
  }
}
