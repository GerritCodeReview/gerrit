/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

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
