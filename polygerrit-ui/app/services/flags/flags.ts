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

export interface FlagsService {
  isEnabled(experimentId: string): boolean;
  enabledExperiments: string[];
}

/**
 * @desc Experiment ids used in Gerrit.
 */
export enum KnownExperimentId {
  NEW_CONTEXT_CONTROLS = 'UiFeature__new_context_controls',
  // Note that this flag is not supposed to be used by Gerrit itself, but can
  // be used by plugins. The new Checks UI will show up, if a plugin registers
  // with the new Checks plugin API.
  CI_REBOOT_CHECKS = 'UiFeature__ci_reboot_checks',
  NEW_CHANGE_SUMMARY_UI = 'UiFeature__new_change_summary_ui',
  PORTING_COMMENTS = 'UiFeature__porting_comments',
}
