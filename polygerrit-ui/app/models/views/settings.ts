/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export interface SettingsViewState extends ViewState {
  view: GerritView.SETTINGS;
  emailToken?: string;
}

const DEFAULT_STATE: SettingsViewState = {view: GerritView.SETTINGS};

export class SettingsViewModel extends Model<SettingsViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
