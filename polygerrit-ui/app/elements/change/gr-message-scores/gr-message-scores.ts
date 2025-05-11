/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-trigger-vote/gr-trigger-vote';
import '../../checks/gr-checks-chip-for-label';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {ChangeInfo, PatchSetNumber} from '../../../api/rest-api';
import {
  LabelExtreme,
  PATCH_SET_PREFIX_PATTERN,
} from '../../../utils/comment-util';
import {hasOwnProperty} from '../../../utils/common-util';
import {getTriggerVotes} from '../../../utils/label-util';
import {ChangeMessage} from '../../../types/common';
import {CheckRun} from '../../../api/checks';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';

const VOTE_RESET_TEXT = '0 (vote reset)';

interface Score {
  label?: string;
  value?: string;
}

export const LABEL_TITLE_SCORE_PATTERN =
  /^(-?)([A-Za-z0-9-]+?)([+-]\d+)?[.:]?$/;

@customElement('gr-message-scores')
export class GrMessageScores extends LitElement {
  @property()
  labelExtremes?: LabelExtreme;

  @property({type: Object})
  message?: ChangeMessage;

  @property({type: Object})
  change?: ChangeInfo;

  @state() runs: CheckRun[] = [];

  @state() latestPatchNum?: PatchSetNumber;

  static override get styles() {
    return css`
      .score,
      gr-trigger-vote {
        padding: 0 var(--spacing-s);
        margin-right: var(--spacing-s);
        display: inline-block;
      }
      .score {
        box-sizing: border-box;
        border-radius: var(--border-radius);
        color: var(--vote-text-color);
        text-align: center;
        min-width: 115px;
      }
      .score.removed {
        background-color: var(--vote-color-neutral);
      }
      .score.negative {
        background-color: var(--vote-color-disliked);
        border: 1px solid var(--vote-outline-disliked);
        line-height: calc(var(--line-height-normal) - 2px);
        color: var(--chip-color);
      }
      .score.negative.min {
        background-color: var(--vote-color-rejected);
        border: none;
        padding-top: 1px;
        padding-bottom: 1px;
        color: var(--vote-text-color);
      }
      .score.positive {
        background-color: var(--vote-color-recommended);
        border: 1px solid var(--vote-outline-recommended);
        line-height: calc(var(--line-height-normal) - 2px);
        color: var(--chip-color);
      }
      .score.positive.max {
        background-color: var(--vote-color-approved);
        border: none;
        padding-top: 1px;
        padding-bottom: 1px;
        color: var(--vote-text-color);
      }

      @media screen and (max-width: 50em) {
        .score {
          min-width: 0px;
        }
      }

      gr-checks-chip-for-label {
        /* .checksChip has top: 2px, this is canceling it */
        position: relative;
        top: -2px;
      }
    `;
  }

  private readonly getChangeModel = resolve(this, changeModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
  }

  override render() {
    const scores = this._getScores(this.message, this.labelExtremes);
    const triggerVotes = getTriggerVotes(this.change);
    return scores.map(score => this.renderScore(score, triggerVotes));
  }

  private renderScore(score: Score, triggerVotes: string[]) {
    if (
      score.label &&
      triggerVotes.includes(score.label) &&
      !score.value?.includes(VOTE_RESET_TEXT)
    ) {
      const labels = this.change?.labels ?? {};
      return html`<gr-trigger-vote
        .label=${score.label}
        .displayValue=${score.value}
        .labelInfo=${labels[score.label]}
        .change=${this.change}
        .mutable=${false}
        disable-hovercards
      >
      </gr-trigger-vote>`;
    }
    return html`<span
        class="score ${this._computeScoreClass(score, this.labelExtremes)}"
      >
        ${score.label} ${score.value} </span
      >${this.renderChecks(score)}`;
  }

  renderChecks(score: Score) {
    const labelName = score.label;
    if (!labelName) return nothing;
    if (Number(score.value) >= 0) return nothing;
    if (this.latestPatchNum !== this.message?._revision_number) return nothing;

    return html`<gr-checks-chip-for-label
      .labels=${[labelName]}
      .showRunning=${false}
    ></gr-checks-chip-for-label>`;
  }

  _computeScoreClass(score?: Score, labelExtremes?: LabelExtreme) {
    if (score === undefined || labelExtremes === undefined) {
      return '';
    }
    if (!score.value) {
      return '';
    }
    if (score.value.includes(VOTE_RESET_TEXT)) {
      return 'removed';
    }
    const classes = [];
    if (Number(score.value) > 0) {
      classes.push('positive');
    } else if (Number(score.value) < 0) {
      classes.push('negative');
    }
    if (score.label) {
      const extremes = labelExtremes[score.label];
      if (extremes) {
        const intScore = Number(score.value);
        if (intScore === extremes.max) {
          classes.push('max');
        } else if (intScore === extremes.min) {
          classes.push('min');
        }
      }
    }
    return classes.join(' ');
  }

  _getScores(message?: ChangeMessage, labelExtremes?: LabelExtreme): Score[] {
    if (!message || !message.message || !labelExtremes) {
      return [];
    }
    const line = message.message.split('\n', 1)[0];
    const patchSetPrefix = PATCH_SET_PREFIX_PATTERN;
    if (!line.match(patchSetPrefix)) {
      return [];
    }
    const scoresRaw = line.split(patchSetPrefix)[1];
    if (!scoresRaw) {
      return [];
    }
    return scoresRaw
      .split(' ')
      .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
      .filter(
        ms => ms && ms.length === 4 && hasOwnProperty(labelExtremes, ms[2])
      )
      .map(ms => {
        const label = ms?.[2];
        const value = ms?.[1] === '-' ? VOTE_RESET_TEXT : ms?.[3];
        return {label, value};
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-message-scores': GrMessageScores;
  }
}
