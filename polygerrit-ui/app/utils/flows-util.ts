/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FlowStageInfo} from '../api/rest-api';
import {capitalizeFirstLetter} from './string-util';

export const STAGE_SEPARATOR = ';';

export interface Stage {
  condition: string;
  action: string;
  parameterStr: string;
}

export function computeFlowString(stages: Stage[]) {
  const stageToString = (stage: Stage) => {
    if (stage.action) {
      if (stage.parameterStr) {
        return `${stage.condition} -> ${stage.action} ${stage.parameterStr}`;
      }
      return `${stage.condition} -> ${stage.action}`;
    }
    return stage.condition;
  };
  return stages.map(stageToString).join(STAGE_SEPARATOR);
}

export function computeFlowStringFromFlowStageInfo(stages: FlowStageInfo[]) {
  return computeFlowString(
    stages.map(s => {
      return {
        condition: s.expression.condition,
        action: s.expression.action?.name ?? '',
        parameterStr: s.expression.action?.parameters?.join(' ') ?? '',
      };
    })
  );
}

/**
 * Formats a flow action name for display.
 * Converts snake_case (e.g., 'add_reviewer') to Title Case (e.g., 'Add Reviewer').
 */
export function formatActionName(name?: string): string {
  if (!name) return '';
  return name
    .split('_')
    .map(word => capitalizeFirstLetter(word))
    .join(' ');
}
