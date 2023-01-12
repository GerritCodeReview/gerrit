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

  let suffix = `${range}/${encodeURL(state.diffView?.path ?? '', true)}`;

  if (state.diffView?.lineNum) {
    suffix += '#';
    if (state.diffView?.leftSide) {
      suffix += 'b';
    }
    suffix += state.diffView.lineNum;
  }

  // TODO: Move creating of comment URLs to a separate function. We are
  // "abusing" the `commentId` property, which should only be used for pointing
  // to comment in the COMMENTS tab of the OVERVIEW page.
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
