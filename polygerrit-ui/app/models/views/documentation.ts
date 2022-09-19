/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export interface DocumentationViewState extends ViewState {
  view: GerritView.DOCUMENTATION_SEARCH;
  filter?: string | null;
}

const DEFAULT_STATE: DocumentationViewState = {
  view: GerritView.DOCUMENTATION_SEARCH,
};

export const documentationViewModelToken = define<DocumentationViewModel>(
  'documentation-view-model'
);

export class DocumentationViewModel extends Model<DocumentationViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
