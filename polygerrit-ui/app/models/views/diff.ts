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
import {encodeURL, getPatchRangeExpression} from '../../utils/url-util';
import {define} from '../dependency';
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

export function createDiffUrl(state: Omit<DiffViewState, 'view'>): string {
  let range = getPatchRangeExpression(state);
  if (range.length) range = '/' + range;

  let suffix = `${range}/${encodeURL(state.path || '', true)}`;

  if (state.lineNum) {
    suffix += '#';
    if (state.leftSide) {
      suffix += 'b';
    }
    suffix += state.lineNum;
  }

  if (state.commentId) {
    suffix = `/comment/${state.commentId}` + suffix;
  }

  if (state.project) {
    const encodedProject = encodeURL(state.project, true);
    return `/c/${encodedProject}/+/${state.changeNum}${suffix}`;
  } else {
    return `/c/${state.changeNum}${suffix}`;
  }
}

export const diffViewModelToken = define<DiffViewModel>('diff-view-model');

export class DiffViewModel extends Model<DiffViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
