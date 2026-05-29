/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {define} from '../dependency';
import {Model} from '../base/model';
import {ViewState} from './base';

export interface AgreementViewState extends ViewState {
  view: GerritView.AGREEMENTS;
}

const DEFAULT_STATE: AgreementViewState = {view: GerritView.AGREEMENTS};

export const agreementViewModelToken = define<AgreementViewModel>(
  'agreement-view-model'
);

export class AgreementViewModel extends Model<AgreementViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
