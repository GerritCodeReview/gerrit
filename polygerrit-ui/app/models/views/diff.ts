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
  ChangeInfo,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {UrlEncodedCommentId} from '../../types/common';
import {
  encodeURL,
  getBaseUrl,
  getPatchRangeExpression,
} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export interface DiffViewState extends ViewState {
  view: GerritView.DIFF;
  changeNum: NumericChangeId;
  repo?: RepoName;
  commentId?: UrlEncodedCommentId;
  path?: string;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  lineNum?: number;
  leftSide?: boolean;
  commentLink?: boolean;
}

/**
 * This is a convenience type such that you can pass a `ChangeInfo` object
 * as the `change` property instead of having to set both the `changeNum` and
 * `project` properties explicitly.
 */
export type CreateChangeUrlObject = Omit<
  DiffViewState,
  'view' | 'changeNum' | 'project'
> & {
  change: Pick<ChangeInfo, '_number' | 'project'>;
};

export function isCreateChangeUrlObject(
  state: CreateChangeUrlObject | Omit<DiffViewState, 'view'>
): state is CreateChangeUrlObject {
  return !!(state as CreateChangeUrlObject).change;
}

export function objToState(
  obj: CreateChangeUrlObject | Omit<DiffViewState, 'view'>
): DiffViewState {
  if (isCreateChangeUrlObject(obj)) {
    return {
      ...obj,
      view: GerritView.DIFF,
      changeNum: obj.change._number,
      repo: obj.change.project,
    };
  }
  return {...obj, view: GerritView.DIFF};
}

export function createDiffUrl(
  obj: CreateChangeUrlObject | Omit<DiffViewState, 'view'>
) {
  const state: DiffViewState = objToState(obj);
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

  if (state.repo) {
    const encodedProject = encodeURL(state.repo, true);
    return `${getBaseUrl()}/c/${encodedProject}/+/${state.changeNum}${suffix}`;
  } else {
    return `${getBaseUrl()}/c/${state.changeNum}${suffix}`;
  }
}

export const diffViewModelToken = define<DiffViewModel>('diff-view-model');

export class DiffViewModel extends Model<DiffViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
