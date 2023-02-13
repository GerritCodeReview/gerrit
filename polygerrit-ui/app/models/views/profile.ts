/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export interface ProfileViewState extends ViewState {
  view: GerritView.PROFILE;
  user?: string;
}

export const profileViewModelToken =
  define<ProfileViewModel>('profile-view-model');

export class ProfileViewModel extends Model<ProfileViewState | undefined> {
  constructor() {
    super(undefined);
  }
}

