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
import {AttemptChoice} from '../checks/checks-util';
import {Model} from '../model';
import {ViewState} from './base';

export interface ChangeViewState extends ViewState {
  view: GerritView.CHANGE;
  changeNum?: NumericChangeId;
  project?: RepoName;
  edit?: boolean;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  commentId?: UrlEncodedCommentId;
  forceReload?: boolean;
  openReplyDialog?: boolean;
  tab?: string;
  /** regular expression for filtering check runs */
  filter?: string;
  /** selected attempt for selected check runs */
  attempt?: AttemptChoice;
}

const DEFAULT_STATE: ChangeViewState = {
  view: GerritView.CHANGE,
};

export class ChangeViewModel extends Model<ChangeViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
