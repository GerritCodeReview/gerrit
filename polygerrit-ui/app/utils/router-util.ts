/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, PARENT} from '../types/common';
import {PatchRangeParams} from '../elements/core/gr-router/gr-router';
import {getBaseUrl} from './url-util';
import {assertNever} from './common-util';
import {GerritView} from '../services/router/router-model';
import {createEditUrl, EditViewState} from '../models/views/edit';
import {createDiffUrl, DiffViewState} from '../models/views/diff';
import {ChangeViewState, createChangeUrl} from '../models/views/change';

export interface DashboardSection {
  name: string;
  query: string;
  suffixForDashboard?: string;
  selfOnly?: boolean;
  hideIfEmpty?: boolean;
  results?: ChangeInfo[];
}

export type GenerateUrlParameters =
  | ChangeViewState
  | EditViewState
  | DiffViewState;

export function isChangeViewState(
  x: GenerateUrlParameters
): x is ChangeViewState {
  return x.view === GerritView.CHANGE;
}

export function isEditViewState(x: GenerateUrlParameters): x is EditViewState {
  return x.view === GerritView.EDIT;
}

export function isDiffViewState(x: GenerateUrlParameters): x is DiffViewState {
  return x.view === GerritView.DIFF;
}

export const TEST_ONLY = {
  getPatchRangeExpression,
};

export function rootUrl() {
  return `${getBaseUrl()}/`;
}

export function generateUrl(params: GenerateUrlParameters) {
  const base = getBaseUrl();
  let url = '';

  if (params.view === GerritView.CHANGE) {
    url = createChangeUrl(params);
  } else if (params.view === GerritView.DIFF) {
    url = createDiffUrl(params);
  } else if (params.view === GerritView.EDIT) {
    url = createEditUrl(params);
  } else {
    assertNever(params, "Can't generate");
  }

  return base + url;
}

/**
 * Given an object of parameters, potentially including a `patchNum` or a
 * `basePatchNum` or both, return a string representation of that range. If
 * no range is indicated in the params, the empty string is returned.
 */
export function getPatchRangeExpression(params: PatchRangeParams) {
  let range = '';
  if (params.patchNum) {
    range = `${params.patchNum}`;
  }
  if (params.basePatchNum && params.basePatchNum !== PARENT) {
    range = `${params.basePatchNum}..${range}`;
  }
  return range;
}
