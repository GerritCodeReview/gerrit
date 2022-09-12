/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  BasePatchSetNum,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {UrlEncodedCommentId} from '../../types/common';
import {Model} from '../model';
import {ViewState} from './base';

export interface DiffViewState extends ViewState {
  view: GerritView.DIFF;
  changeNum?: NumericChangeId;
  project?: RepoName;
  commentId?: UrlEncodedCommentId;
  path?: string;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  lineNum?: number;
  leftSide?: boolean;
  commentLink?: boolean;
}

const DEFAULT_STATE: DiffViewState = {
  view: GerritView.DIFF,
};

export class DiffViewModel extends Model<DiffViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
