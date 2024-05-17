/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {Finalizable} from '../../types/types';

export interface FlagsService extends Finalizable {
  isEnabled(experimentId: string): boolean;
  enabledExperiments: string[];
}

/**
 * Experiment ids used in Gerrit.
 */
export enum KnownExperimentId {
  NEW_IMAGE_DIFF_UI = 'UiFeature__new_image_diff_ui',
  CHECKS_DEVELOPER = 'UiFeature__checks_developer',
  PUSH_NOTIFICATIONS_DEVELOPER = 'UiFeature__push_notifications_developer',
  PUSH_NOTIFICATIONS = 'UiFeature__push_notifications',
  ML_SUGGESTED_EDIT = 'UiFeature__ml_suggested_edit',
  ML_SUGGESTED_EDIT_V2 = 'UiFeature__ml_suggested_edit_v2',
  REVISION_PARENTS_DATA = 'UiFeature__revision_parents_data',
  COMMENT_AUTOCOMPLETION = 'UiFeature__comment_autocompletion_enabled',
  GR_TEXTAREA = 'UiFeature__gr_textarea_enabled',
  SAVE_PROJECT_CONFIG_FOR_REVIEW = 'UiFeature__save_project_config_for_review',
}
