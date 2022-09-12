/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {PageState} from './base';

export interface SettingsPageState extends PageState {
  view: GerritView.SETTINGS;
  emailToken?: string;
}

const DEFAULT_STATE: SettingsPageState = {view: GerritView.SETTINGS};

export class SettingsPageModel extends Model<SettingsPageState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: SettingsPageState) {
    this.subject$.next({...state});
  }
}
