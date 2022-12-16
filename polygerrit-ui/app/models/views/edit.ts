/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {EDIT} from '../../api/rest-api';
import {
  encodeURL,
  getBaseUrl,
  getPatchRangeExpression,
} from '../../utils/url-util';
import {ChangeViewState} from './change';

// TODO: Move to change.ts.
export function createEditUrl(
  state: Omit<ChangeViewState, 'view' | 'childView'>
): string {
  if (state.patchNum === undefined) {
    state = {...state, patchNum: EDIT};
  }
  let range = getPatchRangeExpression(state);
  if (range.length) range = '/' + range;

  let suffix = `${range}/${encodeURL(state.editView?.path ?? '', true)}`;
  suffix += ',edit';

  if (state.editView?.lineNum) {
    suffix += '#';
    suffix += state.editView.lineNum;
  }

  if (state.repo) {
    const encodedProject = encodeURL(state.repo, true);
    return `${getBaseUrl()}/c/${encodedProject}/+/${state.changeNum}${suffix}`;
  } else {
    return `${getBaseUrl()}/c/${state.changeNum}${suffix}`;
  }
}
