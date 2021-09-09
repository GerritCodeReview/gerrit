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
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ApprovalInfo, LabelInfo} from '../../../api/rest-api';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {
  classForLabelStatus,
  getLabelStatus,
  valueString,
} from '../../../utils/label-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-vote-chip': GrVoteChip;
  }
}

@customElement('gr-vote-chip')
export class GrVoteChip extends LitElement {
  @property({type: Object})
  vote?: ApprovalInfo;

  @property({type: Object})
  label?: LabelInfo;

  /** Show vote as a stack of same votes. */
  @property({type: Boolean})
  more = false;

  private readonly flagsService = appContext.flagsService;

  static get styles() {
    return [
      css`
        .vote-chip.max {
          background-color: var(--vote-color-approved);
          padding: 2px;
        }
        .vote-chip.max.more {
          padding: 1px;
          border: 1px solid var(--vote-outline-recommended);
        }
        .vote-chip.min {
          background-color: var(--vote-color-rejected);
          padding: 2px;
        }
        .vote-chip.min.more {
          padding: 1px;
          border: 1px solid var(--vote-outline-disliked);
        }
        .vote-chip.positive,
        .chip-angle.max,
        .chip-angle.positive {
          background-color: var(--vote-color-recommended);
          border: 1px solid var(--vote-outline-recommended);
          color: var(--chip-color);
        }
        .vote-chip.negative,
        .chip-angle.min,
        .chip-angle.negative {
          background-color: var(--vote-color-disliked);
          border: 1px solid var(--vote-outline-disliked);
          color: var(--chip-color);
        }
        .vote-chip,
        .chip-angle {
          display: flex;
          width: var(--gr-vote-chip-width, 18px);
          height: var(--gr-vote-chip-height, 18px);
          justify-content: center;
          margin-right: var(--spacing-s);
          padding: 1px;
          border-radius: var(--border-radius);
          line-height: var(--gr-vote-chip-width, 18px);
        }
        .vote-chip {
          position: relative;
          z-index: 2;
        }
        .chip-angle {
          position: absolute;
          top: 2px;
          left: 2px;
          z-index: 1;
        }
        .container {
          position: relative;
        }
      `,
    ];
  }

  render() {
    if (!this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI))
      return;
    if (!this.vote?.value) return;
    const className = this.computeClass(this.vote.value, this.label);
    return html`<span class="container">
      <div class="vote-chip ${className} ${this.more ? 'more' : ''}">
        ${valueString(this.vote.value)}
      </div>
      ${this.more
        ? html`<div class="chip-angle ${className}">
            ${valueString(this.vote.value)}
          </div>`
        : ''}
    </span>`;
  }

  computeClass(vote: number, label?: LabelInfo) {
    const status = getLabelStatus(label, vote);
    return classForLabelStatus(status);
  }
}
