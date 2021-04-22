/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import './gr-checks-styles';
import {hovercardBehaviorMixin} from '../shared/gr-hovercard/gr-hovercard-behavior';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-hovercard-run_html';
import {customElement, property} from '@polymer/decorators';
import {CheckRun} from '../../services/checks/checks-model';
import {
  iconForCategory,
  iconForStatus,
  runActions,
  worstCategory,
} from '../../services/checks/checks-util';
import {durationString, fromNow} from '../../utils/date-util';
import {RunStatus} from '../../api/checks';

@customElement('gr-hovercard-run')
export class GrHovercardRun extends hovercardBehaviorMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  run?: CheckRun;

  computeIcon(run?: CheckRun) {
    if (!run) return '';
    const category = worstCategory(run);
    if (category) return iconForCategory(category);
    return run.status === RunStatus.COMPLETED
      ? iconForStatus(RunStatus.COMPLETED)
      : '';
  }

  computeActions(run?: CheckRun) {
    return runActions(run);
  }

  computeChipIcon(run?: CheckRun) {
    if (run?.status === RunStatus.COMPLETED) return 'check';
    if (run?.status === RunStatus.RUNNING) return 'timelapse';
    return '';
  }

  computeCompletionDuration(run?: CheckRun) {
    if (!run?.finishedTimestamp || !run?.startedTimestamp) return '';
    return durationString(run.startedTimestamp, run.finishedTimestamp, true);
  }

  computeDuration(date?: Date) {
    return date ? fromNow(date) : '';
  }

  computeHostName(link?: string) {
    return link ? new URL(link).hostname : '';
  }

  hideChip(run?: CheckRun) {
    return !run || run.status === RunStatus.RUNNABLE;
  }

  hideHeaderSectionIcon(run?: CheckRun) {
    return this.computeIcon(run).length === 0;
  }

  hideStatusSection(run?: CheckRun) {
    if (!run) return true;
    return !run.statusLink && !run.statusDescription;
  }

  hideAttemptSection(run?: CheckRun) {
    if (!run) return true;
    return (
      !run.startedTimestamp &&
      !run.scheduledTimestamp &&
      !run.finishedTimestamp &&
      this.hideAttempts(run)
    );
  }

  hideAttempts(run?: CheckRun) {
    const attemptCount = run?.attemptDetails?.length;
    return attemptCount === undefined || attemptCount < 2;
  }

  hideScheduled(run?: CheckRun) {
    return !run?.scheduledTimestamp || !!run?.startedTimestamp;
  }

  hideCompletion(run?: CheckRun) {
    return !run?.startedTimestamp || !run?.finishedTimestamp;
  }

  hideDescriptionSection(run?: CheckRun) {
    if (!run) return true;
    return !run.checkLink && !run.checkDescription;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard-run': GrHovercardRun;
  }
}
