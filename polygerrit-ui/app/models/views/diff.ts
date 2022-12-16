/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  encodeURL,
  getBaseUrl,
  getPatchRangeExpression,
} from '../../utils/url-util';
import {
  ChangeChildView,
  ChangeViewState,
  CreateChangeUrlObject,
  objToState,
} from './change';

// TODO: Move to change.ts.
export function createDiffUrl(
  obj: CreateChangeUrlObject | Omit<ChangeViewState, 'view' | 'childView'>
) {
  const state: ChangeViewState = objToState({
    ...obj,
    childView: ChangeChildView.DIFF,
  });
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
