/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @fileOverview This file contains interfaces shared between
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
