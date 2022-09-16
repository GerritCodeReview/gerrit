/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  EDIT,
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {encodeURL, getPatchRangeExpression} from '../../utils/url-util';
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

export function createEditUrl(state: Omit<EditViewState, 'view'>): string {
  if (state.patchNum === undefined) {
    state = {...state, patchNum: EDIT};
  }
  let range = getPatchRangeExpression(state);
  if (range.length) range = '/' + range;

  let suffix = `${range}/${encodeURL(state.path || '', true)}`;
  suffix += ',edit';

  if (state.lineNum) {
    suffix += '#';
    suffix += state.lineNum;
  }

  if (state.project) {
    const encodedProject = encodeURL(state.project, true);
    return `/c/${encodedProject}/+/${state.changeNum}${suffix}`;
  } else {
    return `/c/${state.changeNum}${suffix}`;
  }
}

export class EditViewModel extends Model<EditViewState> {
  constructor() {
    super(DEFAULT_STATE);
  }
}
