/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export interface AgreementViewState extends ViewState {
  view: GerritView.AGREEMENTS;
}

const DEFAULT_STATE: AgreementViewState = {view: GerritView.AGREEMENTS};

export class AgreementViewModel extends Model<AgreementViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }

  updateState(state: AgreementViewState) {
    this.subject$.next({...state});
  }
}
