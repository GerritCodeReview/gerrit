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
import {css, customElement, html, property} from 'lit-element';
import { ApprovalInfo } from '../../../api/rest-api';
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

  static get styles() {
    return [
      css`
        .chipVote {
          display: flex;
          justify-content: center;
          margin-right: var(--spacing-s);
          padding: 1px;
          border-radius: 4px;
          color: var(--vote-text-color);
          border: 1px solid var(--border-color);
          line-height: var(--line-height-normal);
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
    if (!this.vote?.value) return;
    const className = this.computeClass(this.vote.value);
    return html`<span class="chipVote ${className}">+${this.vote.value}</span>`;
  }

  computeClass(vote: Number) {
    if (vote === 2) return 'max';
    if (vote === 1) return 'positive';
    if (vote === -1) return 'negative';
    if (vote === -2) return 'min';
    return '';
  }
}
