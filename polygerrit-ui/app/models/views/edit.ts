/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {Model} from '../model';
import {ViewState} from './base';

export interface EditViewState extends ViewState {
  view: GerritView.EDIT;
  changeNum?: NumericChangeId;
  project?: RepoName;
  path?: string;
  patchNum?: RevisionPatchSetNum;
  lineNum?: number;
}

const DEFAULT_STATE: EditViewState = {
  view: GerritView.EDIT,
};

export class EditViewModel extends Model<EditViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
