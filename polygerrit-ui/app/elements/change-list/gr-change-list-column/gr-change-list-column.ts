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
import '../../shared/gr-change-status/gr-change-status';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {changeStatuses} from '../../../utils/change-util';
import {getRequirements, iconForStatus} from '../../../utils/label-util';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';

@customElement('gr-change-list-column-requirements')
export class GrChangeListColumRequirements extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  static override get styles() {
    return [
      submitRequirementsStyles,
      css`
        iron-icon {
          width: var(--line-height-normal, 20px);
          height: var(--line-height-normal, 20px);
          vertical-align: top;
        }
        iron-icon.block,
        iron-icon.check-circle-filled {
          margin-right: var(--spacing-xs);
        }
        iron-icon.commentIcon {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-s);
        }
        span {
          line-height: var(--line-height-normal);
        }
        span.check-circle-filled {
          color: var(--success-foreground);
        }
        .unsatisfied {
          color: var(--primary-text-color);
        }
        .total {
          margin-left: var(--spacing-xs);
          color: var(--deemphasized-text-color);
        }
        :host {
          align-items: center;
          display: inline-flex;
        }
        .comma {
          padding-right: var(--spacing-xs);
        }
        /* Used to hide the leading separator comma for statuses. */
        .comma:first-of-type {
          display: none;
        }
      `,
    ];
  }

  override render() {
    const commentIcon = this.renderCommentIcon();
    return html`${this.renderChangeStatus()} ${commentIcon}`;
  }

  renderChangeStatus() {
    if (!this.change) return;
    const statuses = changeStatuses(this.change);
    if (statuses.length > 0) {
      return statuses.map(
        status => html`
          <div class="comma">,</div>
          <gr-change-status flat .status=${status}></gr-change-status>
        `
      );
    }
    return this.renderActiveStatus();
  }

  renderActiveStatus() {
    const submitRequirements = getRequirements(this.change);
    if (!submitRequirements.length) return html`n/a`;
    const numRequirements = submitRequirements.length;
    const numSatisfied = submitRequirements.filter(
      req =>
        req.status === SubmitRequirementStatus.SATISFIED ||
        req.status === SubmitRequirementStatus.OVERRIDDEN
    ).length;

    if (numSatisfied === numRequirements) {
      return this.renderState(
        iconForStatus(SubmitRequirementStatus.SATISFIED),
        'Ready'
      );
    }

    const numUnsatisfied = submitRequirements.filter(
      req => req.status === SubmitRequirementStatus.UNSATISFIED
    ).length;

    return this.renderState(
      iconForStatus(SubmitRequirementStatus.UNSATISFIED),
      this.renderSummary(numUnsatisfied, numRequirements)
    );
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
