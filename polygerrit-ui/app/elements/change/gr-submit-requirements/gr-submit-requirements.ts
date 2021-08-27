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
import '../gr-submit-requirement-hovercard/gr-submit-requirement-hovercard';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {ParsedChangeInfo} from '../../../types/types';
import {
  AccountInfo,
  isDetailedLabelInfo,
  LabelNameToInfoMap,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {unique} from '../../../utils/common-util';
import {
  extractAssociatedLabels,
  iconForStatus,
} from '../../../utils/label-util';

@customElement('gr-submit-requirements')
export class GrSubmitRequirements extends GrLitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable?: boolean;

  static override get styles() {
    return [
      css`
        :host {
          display: table;
          width: 100%;
        }
        .metadata-title {
          font-size: 100%;
          font-weight: var(--font-weight-bold);
          color: var(--deemphasized-text-color);
          padding-left: var(--metadata-horizontal-padding);
        }
        section {
          display: table-row;
        }
        .title {
          min-width: 10em;
          padding: var(--spacing-s) 0 0 0;
        }
        .value {
          padding: var(--spacing-s) 0 0 0;
        }
        .title,
        .status {
          display: table-cell;
          vertical-align: top;
        }
        .value {
          display: inline-flex;
        }
        .status {
          width: var(--line-height-small);
          padding: var(--spacing-s) var(--spacing-m) 0
            var(--requirements-horizontal-padding);
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
        }
        iron-icon.check {
          color: var(--success-foreground);
        }
        iron-icon.close {
          color: var(--warning-foreground);
        }
        .testing {
          margin-top: var(--spacing-xxl);
          padding-left: var(--metadata-horizontal-padding);
          color: var(--deemphasized-text-color);
        }
        .testing gr-button {
          min-width: 25px;
        }
        .testing * {
          visibility: hidden;
        }
        .testing:hover * {
          visibility: visible;
        }
      `,
    ];
  }

  override render() {
    const submit_requirements = (this.change?.submit_requirements ?? []).filter(
      req => req.status !== SubmitRequirementStatus.NOT_APPLICABLE
    );
    return html`<h3 class="metadata-title">Submit Requirements</h3>
      ${submit_requirements.map(
        requirement => html`<section>
          <gr-submit-requirement-hovercard
            .requirement="${requirement}"
            .change="${this.change}"
            .account="${this.account}"
            .mutable="${this.mutable}"
          ></gr-submit-requirement-hovercard>
          <div class="status">${this.renderStatus(requirement.status)}</div>
          <div class="title">
            <gr-limited-text
              class="name"
              limit="25"
              text="${requirement.name}"
            ></gr-limited-text>
          </div>
          <div class="value">${this.renderVotes(requirement)}</div>
        </section>`
      )}
      ${this.renderTriggerVotes(
        submit_requirements
      )}${this.renderFakeControls()}`;
  }

  renderStatus(status: SubmitRequirementStatus) {
    const icon = iconForStatus(status);
    return html`<iron-icon
      class="${icon}"
      icon="gr-icons:${icon}"
    ></iron-icon>`;
  }

  renderVotes(requirement: SubmitRequirementResultInfo) {
    const requirementLabels = extractAssociatedLabels(requirement);
    const labels = this.change?.labels ?? {};

    return Object.keys(labels)
      .filter(label => requirementLabels.includes(label))
      .map(label => this.renderLabelVote(label, labels));
  }

  renderLabelVote(label: string, labels: LabelNameToInfoMap) {
    const labelInfo = labels[label];
    if (!isDetailedLabelInfo(labelInfo)) return;
    return (labelInfo.all ?? []).map(
      approvalInfo =>
        html`<gr-vote-chip
          .vote="${approvalInfo}"
          .label="${labelInfo}"
        ></gr-vote-chip>`
    );
  }

  renderTriggerVotes(submitReqs: SubmitRequirementResultInfo[]) {
    const labels = this.change?.labels ?? {};
    const allLabels = Object.keys(labels);
    const labelAssociatedWithSubmitReqs = submitReqs
      .map(req => extractAssociatedLabels(req))
      .reduce((acc, val) => acc.concat(val), [])
      .filter(unique);
    const triggerVotes = allLabels.filter(
      label => !labelAssociatedWithSubmitReqs.includes(label)
    );
    if (!triggerVotes.length) return;
    return html`<h3 class="metadata-title">Trigger Votes</h3>
      ${triggerVotes.map(
        label => html`${label}:
          <gr-label-info
            .change="${this.change}"
            .account="${this.account}"
            .mutable="${this.mutable}"
            label="${label}"
            .labelInfo="${labels[label]}"
          ></gr-label-info>`
      )}`;
  }

  renderFakeControls() {
    return html`
      <div class="testing">
        <div>Toggle fake data:</div>
        <gr-button link @click="${() => this.renderFakeSubmitRequirements()}"
          >G</gr-button
        >
      </div>
    `;
  }

  renderFakeSubmitRequirements() {
    if (!this.change) return;
    this.change = {
      ...this.change,
      submit_requirements: [
        {
          name: 'Code-Review',
          status: SubmitRequirementStatus.SATISFIED,
          description:
            "At least one maximum vote for label 'Code-Review' is required",
          submittability_expression_result: {
            expression: 'label:Code-Review=MAX -label:Code-Review=MIN',
            fulfilled: true,
            passing_atoms: [],
            failing_atoms: [],
          },
        },
        {
          name: 'Verified',
          status: SubmitRequirementStatus.UNSATISFIED,
          description: 'CI build and tests results are verified',
          submittability_expression_result: {
            expression: 'label:Verified=MAX -label:Verified=MIN',
            fulfilled: false,
            passing_atoms: [],
            failing_atoms: [],
          },
        },
      ],
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirements': GrSubmitRequirements;
  }
}
