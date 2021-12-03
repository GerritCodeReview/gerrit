/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import '../../change/gr-submit-requirement-dashboard-hovercard/gr-submit-requirement-dashboard-hovercard';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {changeIsMerged} from '../../../utils/change-util';
import {getRequirements, iconForStatus} from '../../../utils/label-util';

@customElement('gr-change-list-column-requirements')
export class GrChangeListColumRequirements extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  static override get styles() {
    return [
      css`
        iron-icon {
          width: var(--line-height-normal, 20px);
          height: var(--line-height-normal, 20px);
          vertical-align: top;
        }
        span {
          line-height: var(--line-height-normal);
        }
        .unsatisfied {
          color: var(--primary-text-color);
        }
        .total {
          margin-left: var(--spacing-s);
          color: var(--deemphasized-text-color);
        }
        .check-circle-filled {
          color: var(--success-foreground);
        }
        iron-icon.block {
          color: var(--deemphasized-text-color);
        }
        .commentIcon {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    const satisfiedIcon = iconForStatus(SubmitRequirementStatus.SATISFIED);
    if (changeIsMerged(this.change)) {
      return this.renderState(satisfiedIcon, 'Merged');
    }

    const submitRequirements = getRequirements(this.change);
    if (!submitRequirements.length) return html`n/a`;
    const numRequirements = submitRequirements.length;
    const numSatisfied = submitRequirements.filter(
      req =>
        req.status === SubmitRequirementStatus.SATISFIED ||
        req.status === SubmitRequirementStatus.OVERRIDDEN
    ).length;

    if (numSatisfied === numRequirements) {
      return this.renderState(satisfiedIcon, 'Ready');
    }

    const numUnsatisfied = submitRequirements.filter(
      req => req.status === SubmitRequirementStatus.UNSATISFIED
    ).length;

    const state = this.renderState(
      iconForStatus(SubmitRequirementStatus.UNSATISFIED),
      this.renderSummary(numUnsatisfied, numRequirements)
    );
    const commentIcon = this.renderCommentIcon();
    return html`${state}${commentIcon}`;
  }

  renderState(icon: string, aggregation: string | TemplateResult) {
    return html`<span class="${icon}"
      ><gr-submit-requirement-dashboard-hovercard .change=${this.change}>
      </gr-submit-requirement-dashboard-hovercard>
      <iron-icon class="${icon}" icon="gr-icons:${icon}" role="img"></iron-icon
      >${aggregation}</span
    >`;
  }

  renderSummary(numUnsatisfied: number, numRequirements: number) {
    return html`<span
      ><span class="unsatisfied">${numUnsatisfied}</span
      ><span class="total">(of ${numRequirements})</span></span
    >`;
  }

  renderCommentIcon() {
    if (!this.change?.unresolved_comment_count) return;
    return html`<iron-icon
      icon="gr-icons:comment"
      class="commentIcon"
    ></iron-icon>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirements': GrChangeListColumRequirements;
  }
}
