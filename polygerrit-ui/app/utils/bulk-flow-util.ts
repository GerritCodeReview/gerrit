/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ProgressStatus} from '../constants/constants';
import {NumericChangeId} from '../api/rest-api';

export function getOverallStatus(
  progressByChangeNum: Map<NumericChangeId, ProgressStatus>
) {
  const statuses = Array.from(progressByChangeNum.values());
  if (statuses.every(s => s === ProgressStatus.NOT_STARTED)) {
    return ProgressStatus.NOT_STARTED;
  }
  if (statuses.some(s => s === ProgressStatus.RUNNING)) {
    return ProgressStatus.RUNNING;
  }
  if (statuses.some(s => s === ProgressStatus.FAILED)) {
    return ProgressStatus.FAILED;
  }
  return ProgressStatus.SUCCESSFUL;
}
