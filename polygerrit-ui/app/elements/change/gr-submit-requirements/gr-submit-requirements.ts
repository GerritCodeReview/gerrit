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
import '../../shared/gr-label-info/gr-label-info';
import '../gr-submit-requirement-hovercard/gr-submit-requirement-hovercard';
import '../gr-trigger-vote-hovercard/gr-trigger-vote-hovercard';
import {LitElement, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ParsedChangeInfo} from '../../../types/types';
import {
  AccountInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
  LabelNameToInfoMap,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {unique} from '../../../utils/common-util';
import {
  extractAssociatedLabels,
  getAllUniqueApprovals,
  hasNeutralStatus,
  hasVotes,
  iconForStatus,
  orderSubmitRequirements,
} from '../../../utils/label-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {charsOnly, pluralize} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {
  allRunsLatestPatchsetLatestAttempt$,
  CheckRun,
} from '../../../services/checks/checks-model';
import {getResultsOf, hasResultsOf} from '../../../services/checks/checks-util';
import {Category} from '../../../api/checks';
import '../../shared/gr-vote-chip/gr-vote-chip';

/**
 * @attr {Boolean} suppress-title - hide titles, currently for hovercard view
 */
@customElement('gr-submit-requirements')
export class GrSubmitRequirements extends LitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable?: boolean;

  @state()
  runs: CheckRun[] = [];

  static override get styles() {
    return [
      fontStyles,
      css`
        :host([suppress-title]) .metadata-title {
          display: none;
        }
        .metadata-title {
          color: var(--deemphasized-text-color);
          padding-left: var(--metadata-horizontal-padding);
          margin: 0 0 var(--spacing-s);
          border-top: 1px solid var(--border-color);
          padding-top: var(--spacing-s);
        }
        iron-icon {
          width: var(--line-height-normal, 20px);
          height: var(--line-height-normal, 20px);
        }
        iron-icon.check,
        iron-icon.overridden {
          color: var(--success-foreground);
        }
        iron-icon.close {
          color: var(--error-foreground);
        }
        .requirements,
        section.trigger-votes {
          margin-left: var(--spacing-l);
        }
        .trigger-votes {
          padding-top: var(--spacing-s);
          display: flex;
          flex-wrap: wrap;
          gap: var(--spacing-s);
          /* Setting max-width as defined in Submit Requirements design,
           *  to wrap overflowed items to next row.
           */
          max-width: 390px;
        }
        gr-limited-text.name {
          font-weight: var(--font-weight-bold);
        }
        table {
          border-collapse: collapse;
          border-spacing: 0;
        }
        td {
          padding: var(--spacing-s);
          white-space: nowrap;
        }
        .votes-cell {
          display: flex;
        }
        .check-error {
          margin-right: var(--spacing-l);
        }
        .check-error iron-icon {
          color: var(--error-foreground);
          vertical-align: top;
        }
        gr-vote-chip {
          margin-right: var(--spacing-s);
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(this, allRunsLatestPatchsetLatestAttempt$, x => (this.runs = x));
  }

  override render() {
    let submit_requirements = orderSubmitRequirements(
      this.change?.submit_requirements ?? []
    ).filter(req => req.status !== SubmitRequirementStatus.NOT_APPLICABLE);

    const hasNonLegacyRequirements = submit_requirements.some(
      req => req.is_legacy === false
    );
    if (hasNonLegacyRequirements) {
      submit_requirements = submit_requirements.filter(
        req => req.is_legacy === false
      );
    }

    return html` <h3
        class="metadata-title heading-3"
        id="submit-requirements-caption"
      >
        Submit Requirements
      </h3>
      <table class="requirements" aria-labelledby="submit-requirements-caption">
        <thead hidden>
          <tr>
            <th>Status</th>
            <th>Name</th>
            <th>Votes</th>
          </tr>
        </thead>
        <tbody>
          ${submit_requirements.map(
            requirement => html`<tr
              id="requirement-${charsOnly(requirement.name)}"
            >
              <td>${this.renderStatus(requirement.status)}</td>
              <td class="name">
                <gr-limited-text
                  class="name"
                  limit="25"
                  .text="${requirement.name}"
                ></gr-limited-text>
              </td>
              <td>
                <div class="votes-cell">
                  ${this.renderVotes(requirement)}
                  ${this.renderChecks(requirement)}
                </div>
              </td>
            </tr>`
          )}
        </tbody>
      </table>
      ${submit_requirements.map(
        requirement => html`
          <gr-submit-requirement-hovercard
            for="requirement-${charsOnly(requirement.name)}"
            .requirement="${requirement}"
            .change="${this.change}"
            .account="${this.account}"
            .mutable="${this.mutable ?? false}"
          ></gr-submit-requirement-hovercard>
        `
      )}
      ${this.renderTriggerVotes(submit_requirements)}`;
  }

  renderStatus(status: SubmitRequirementStatus) {
    const icon = iconForStatus(status);
    return html`<iron-icon
      class="${icon}"
      icon="gr-icons:${icon}"
      role="img"
      aria-label="${status.toLowerCase()}"
    ></iron-icon>`;
  }

  renderVotes(requirement: SubmitRequirementResultInfo) {
    const requirementLabels = extractAssociatedLabels(requirement);
    const allLabels = this.change?.labels ?? {};
    const associatedLabels = Object.keys(allLabels).filter(label =>
      requirementLabels.includes(label)
    );

    const everyAssociatedLabelsIsWithoutVotes = associatedLabels.every(
      label => !hasVotes(allLabels[label])
    );
    if (everyAssociatedLabelsIsWithoutVotes) return html`No votes`;

    return associatedLabels.map(label =>
      this.renderLabelVote(label, allLabels)
    );
  }

  renderLabelVote(label: string, labels: LabelNameToInfoMap) {
    const labelInfo = labels[label];
    if (isDetailedLabelInfo(labelInfo)) {
      const uniqueApprovals = getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
      return uniqueApprovals.map(
        approvalInfo =>
          html`<gr-vote-chip
            .vote="${approvalInfo}"
            .label="${labelInfo}"
            .more="${(labelInfo.all ?? []).filter(
              other => other.value === approvalInfo.value
            ).length > 1}"
          ></gr-vote-chip>`
      );
    } else if (isQuickLabelInfo(labelInfo)) {
      return [html`<gr-vote-chip .label="${labelInfo}"></gr-vote-chip>`];
    } else {
      return html``;
    }
  }

  renderChecks(requirement: SubmitRequirementResultInfo) {
    const requirementLabels = extractAssociatedLabels(requirement);
    const requirementRuns = this.runs
      .filter(run => hasResultsOf(run, Category.ERROR))
      .filter(
        run => run.labelName && requirementLabels.includes(run.labelName)
      );
    const runsCount = requirementRuns.reduce(
      (sum, run) => sum + getResultsOf(run, Category.ERROR).length,
      0
    );
    if (runsCount > 0) {
      return html`<span class="check-error"
        ><iron-icon icon="gr-icons:error"></iron-icon>${pluralize(
          runsCount,
          'error'
        )}</span
      >`;
    }
    return;
  }

  renderTriggerVotes(submitReqs: SubmitRequirementResultInfo[]) {
    const labels = this.change?.labels ?? {};
    const allLabels = Object.keys(labels);
    const labelAssociatedWithSubmitReqs = submitReqs
      .flatMap(req => extractAssociatedLabels(req))
      .filter(unique);
    const triggerVotes = allLabels
      .filter(label => !labelAssociatedWithSubmitReqs.includes(label))
      .filter(label => hasVotes(labels[label]));
    if (!triggerVotes.length) return;
    return html`<h3 class="metadata-title heading-3">Trigger Votes</h3>
      <section class="trigger-votes">
        ${triggerVotes.map(
          label =>
            html`<gr-trigger-vote
              .label="${label}"
              .labelInfo="${labels[label]}"
              .change="${this.change}"
              .account="${this.account}"
              .mutable="${this.mutable ?? false}"
            ></gr-trigger-vote>`
        )}
      </section>`;
  }
}

@customElement('gr-trigger-vote')
export class GrTriggerVote extends LitElement {
  @property()
  label?: string;

  @property({type: Object})
  labelInfo?: LabelInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable?: boolean;

  static override get styles() {
    return css`
      :host {
        display: block;
      }
      .container {
        box-sizing: border-box;
        border: 1px solid var(--border-color);
        border-radius: calc(var(--border-radius) + 2px);
        background-color: var(--background-color-primary);
        display: flex;
        padding: 0;
        padding-left: var(--spacing-s);
        padding-right: var(--spacing-xxs);
        align-items: center;
      }
      .label {
        padding-right: var(--spacing-s);
        font-weight: var(--font-weight-bold);
      }
      gr-vote-chip {
        --gr-vote-chip-width: 14px;
        --gr-vote-chip-height: 14px;
        margin-right: 0px;
        margin-left: var(--spacing-xs);
      }
      gr-vote-chip:first-of-type {
        margin-left: 0px;
      }
    `;
  }

  override render() {
    if (!this.labelInfo) return;
    return html`
      <div class="container">
        <gr-trigger-vote-hovercard .labelName=${this.label}>
          <gr-label-info
            slot="label-info"
            .change=${this.change}
            .account=${this.account}
            .mutable=${this.mutable}
            .label=${this.label}
            .labelInfo=${this.labelInfo}
            .showAllReviewers=${false}
          ></gr-label-info>
        </gr-trigger-vote-hovercard>
        <span class="label">${this.label}</span>
        ${this.renderVotes()}
      </div>
    `;
  }

  private renderVotes() {
    const {labelInfo} = this;
    if (!labelInfo) return;
    if (isDetailedLabelInfo(labelInfo)) {
      const approvals = getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
      return approvals.map(
        approvalInfo => html`<gr-vote-chip
          .vote="${approvalInfo}"
          .label="${labelInfo}"
        ></gr-vote-chip>`
      );
    } else if (isQuickLabelInfo(labelInfo)) {
      return [html`<gr-vote-chip .label="${this.labelInfo}"></gr-vote-chip>`];
    } else {
      return html``;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirements': GrSubmitRequirements;
    'gr-trigger-vote': GrTriggerVote;
  }
}
