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
import {
  encodeURL,
  getBaseUrl,
  getPatchRangeExpression,
} from '../../utils/url-util';
import {AttemptChoice} from '../checks/checks-util';
import {define} from '../dependency';
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

  messageHash?: string;
  usp?: string;
}

export function createChangeUrl(state: Omit<ChangeViewState, 'view'>) {
  let range = getPatchRangeExpression(state);
  if (range.length) {
    range = '/' + range;
  }
  let suffix = `${range}`;
  const queries = [];
  if (state.forceReload) {
    queries.push('forceReload=true');
  }
  if (state.openReplyDialog) {
    queries.push('openReplyDialog=true');
  }
  if (state.usp) {
    queries.push(`usp=${state.usp}`);
  }
  if (state.edit) {
    suffix += ',edit';
  }
  if (state.commentId) {
    suffix = suffix + `/comments/${state.commentId}`;
  }
  if (queries.length > 0) {
    suffix += '?' + queries.join('&');
  }
  if (state.messageHash) {
    suffix += state.messageHash;
  }
  if (state.project) {
    const encodedProject = encodeURL(state.project, true);
    return getBaseUrl() + `/c/${encodedProject}/+/${state.changeNum}${suffix}`;
  } else {
    return getBaseUrl() + `/c/${state.changeNum}${suffix}`;
  }
}

export const changeViewModelToken =
  define<ChangeViewModel>('change-view-model');

export class ChangeViewModel extends Model<ChangeViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
