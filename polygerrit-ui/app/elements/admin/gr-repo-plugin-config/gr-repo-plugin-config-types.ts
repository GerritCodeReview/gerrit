/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * @fileoverview This file contains interfaces shared between
 * gr-repo-plugin-config.ts and nested editors
 * (e.g. gr-plugin-config-array-editor.ts)
 *
 * This file is required to avoid circular dependencies between files
 */

import {
  ConfigArrayParameterInfo,
  ConfigParameterInfo,
  ConfigParameterInfoBase,
} from '../../../types/common';

export interface PluginOption<
  T extends ConfigParameterInfoBase = ConfigParameterInfo
> {
  _key: string; // parameterName of PluginParameterToConfigParameterInfoMap
  info: T;
}

export type ArrayPluginOption = PluginOption<ConfigArrayParameterInfo>;

export interface PluginConfigOptionsChangedEventDetail {
  _key: string;
  info: ConfigArrayParameterInfo;
  notifyPath: string;
}
