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
  ML_SUGGESTED_EDIT_V2 = 'UiFeature__ml_suggested_edit_v2',
  PARALLEL_DASHBOARD_REQUESTS = 'UiFeature__parallel_dashboard_requests',
  ML_SUGGESTED_EDIT_UNCHECK_BY_DEFAULT = 'UiFeature__ml_suggested_edit_uncheck_by_default',
  ML_SUGGESTED_EDIT_FEEDBACK = 'UiFeature__ml_suggested_edit_feedback',
  ML_SUGGESTED_EDIT_EDITABLE_SUGGESTION = 'UiFeature__ml_suggested_edit_editable_suggestion',
  ASYNC_SUBMIT_REQUIREMENTS = 'UiFeature__async_submit_requirements',
  ENABLE_AI_CHAT = 'UiFeature__enable_ai_chat',
}
