/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export interface SearchViewState extends ViewState {
  view: GerritView.SEARCH;
  query?: string;
  offset?: string;
}

const DEFAULT_STATE: SearchViewState = {
  view: GerritView.SEARCH,
};

export class SearchViewModel extends Model<SearchViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
