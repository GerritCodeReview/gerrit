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
import {css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ApprovalInfo, LabelInfo} from '../../../api/rest-api';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {getVotingRangeOrDefault, valueString} from '../../../utils/label-util';
import {GrLitElement} from '../../lit/gr-lit-element';

declare global {
  interface HTMLElementTagNameMap {
    'gr-vote-chip': GrVoteChip;
  }
}

@customElement('gr-vote-chip')
export class GrVoteChip extends GrLitElement {
  @property({type: Object})
  vote?: ApprovalInfo;

  @property({type: Object})
  label?: LabelInfo;

  private readonly flagsService = appContext.flagsService;

  static get styles() {
    return [
      css`
        .chipVote {
          display: flex;
          justify-content: center;
          margin-right: var(--spacing-s);
          padding: 1px;
          border-radius: var(--border-radius);
          color: var(--vote-text-color);
          border: 1px solid var(--border-color);
          line-height: calc(var(--line-height-normal) - 4px);
        }
        .max {
          background-color: var(--vote-color-approved);
        }
        .min {
          background-color: var(--vote-color-rejected);
        }
        .positive {
          background-color: var(--vote-color-recommended);
          border: 1px solid var(--vote-outline-recommended);
          color: var(--chip-color);
        }
        .negative {
          background-color: var(--vote-color-disliked);
          border: 1px solid var(--vote-outline-disliked);
          color: var(--chip-color);
        }
      `,
    ];
  }

  render() {
    if (!this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI))
      return;
    if (!this.vote?.value) return;
    const className = this.computeClass(this.vote.value, this.label);
    return html`<div class="chipVote ${className}">
      ${valueString(this.vote.value)}
    </div>`;
  }

  computeClass(vote: Number, label?: LabelInfo) {
    const votingRange = getVotingRangeOrDefault(label);
    if (vote > 0) {
      if (vote === votingRange.max) {
        return 'max';
      } else {
        return 'positive';
      }
    } else if (vote < 0) {
      if (vote === votingRange.min) {
        return 'min';
      } else {
        return 'negative';
      }
    }
    return '';
  }
}
