/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';

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
  DIFF_RENDERING_LIT = 'UiFeature__diff_rendering_lit',
  PUSH_NOTIFICATIONS = 'UiFeature__push_notifications',
  SUGGEST_EDIT = 'UiFeature__suggest_edit',
  RENDER_MARKDOWN = 'UiFeature__render_markdown',
  REBASE_CHAIN = 'UiFeature__rebase_chain',
}
