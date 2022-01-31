/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  ApprovalInfo,
  ChangeInfo,
  isDetailedLabelInfo,
  LabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {
  extractAssociatedLabels,
  getAllUniqueApprovals,
  getRequirements,
  hasNeutralStatus,
  iconForStatus,
} from '../../../utils/label-util';
import {sharedStyles} from '../../../styles/shared-styles';

@customElement('gr-change-list-column-requirement')
export class GrChangeListColumnRequirement extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  @property()
  labelName?: string;

  static override get styles() {
    return [
      submitRequirementsStyles,
      sharedStyles,
      css`
        iron-icon {
          vertical-align: top;
        }
        .container {
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .container.not-applicable {
          background-color: var(--table-header-background-color);
          height: calc(var(--line-height-normal) + var(--spacing-m));
        }
      `,
    ];
  }

  override render() {
    return html`<div class="container ${this.computeClass()}">
      ${this.renderContent()}
    </div>`;
  }

  private renderContent() {
    if (!this.labelName) return;
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) return;

    const requirement = requirements[0];
    if (requirement.status === SubmitRequirementStatus.UNSATISFIED) {
      return this.renderUnsatisfiedState(requirement);
    } else {
      return this.renderStatusIcon(requirement.status);
    }
  }

  private renderUnsatisfiedState(requirement: SubmitRequirementResultInfo) {
    const requirementLabels = extractAssociatedLabels(
      requirement,
      'onlySubmittability'
    );
    const allLabels = this.change?.labels ?? {};
    const associatedLabels = Object.keys(allLabels).filter(label =>
      requirementLabels.includes(label)
    );

    let worstVote: ApprovalInfo | undefined;
    let labelInfo: LabelInfo | undefined;
    for (const label of associatedLabels) {
      const votes = this.getVotes(label);
      if (votes.length === 0) break;
      // votes are already sorted from worst e.g -2 to best e.g +2
      if (!worstVote || (worstVote.value ?? 0) > (votes[0].value ?? 0)) {
        worstVote = votes[0];
        labelInfo = allLabels[label];
      }
    }
    if (worstVote === undefined) {
      return this.renderStatusIcon(requirement.status);
    } else {
      return html`<gr-vote-chip
        .vote="${worstVote}"
        .label="${labelInfo}"
      ></gr-vote-chip>`;
    }
  }

  private renderStatusIcon(status: SubmitRequirementStatus) {
    const icon = iconForStatus(status ?? SubmitRequirementStatus.ERROR);
    return html`<iron-icon
      class="${icon}"
      icon="gr-icons:${icon}"
    ></iron-icon>`;
  }

  private computeClass(): string {
    if (!this.labelName) return '';
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) {
      return 'not-applicable';
    }
    return '';
  }

  private getRequirement(labelName: string) {
    const requirements = getRequirements(this.change).filter(
      sr => sr.name === labelName
    );
    // TODO(milutin): Remove this after migration from legacy requirements.
    if (requirements.length > 1) {
      return requirements.filter(sr => !sr.is_legacy);
    } else {
      return requirements;
    }
  }

  private getVotes(label: string) {
    const allLabels = this.change?.labels ?? {};
    const labelInfo = allLabels[label];
    if (isDetailedLabelInfo(labelInfo)) {
      return getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
    }
    return [];
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirement': GrChangeListColumnRequirement;
  }
}
