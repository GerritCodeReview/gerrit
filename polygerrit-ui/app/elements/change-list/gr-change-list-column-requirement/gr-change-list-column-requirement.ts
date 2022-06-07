/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../change/gr-submit-requirement-dashboard-hovercard/gr-submit-requirement-dashboard-hovercard';
import '../../shared/gr-change-status/gr-change-status';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  ApprovalInfo,
  ChangeInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {
  extractAssociatedLabels,
  getAllUniqueApprovals,
  getRequirements,
  getTriggerVotes,
  hasNeutralStatus,
  hasVotes,
  iconForStatus,
} from '../../../utils/label-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {ifDefined} from 'lit/directives/if-defined';
import {capitalizeFirstLetter} from '../../../utils/string-util';

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
    return html`<div
      class="container ${this.computeClass()}"
      title=${ifDefined(this.computeLabelTitle())}
    >
      ${this.renderContent()}
    </div>`;
  }

  private renderContent() {
    if (!this.labelName) return;
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) {
      return this.renderTriggerVote();
    }

    const requirement = requirements[0];
    if (requirement.status === SubmitRequirementStatus.UNSATISFIED) {
      return this.renderUnsatisfiedState(requirement);
    } else {
      return this.renderStatusIcon(requirement.status);
    }
  }

  private renderTriggerVote() {
    if (!this.labelName || !this.isTriggerVote(this.labelName)) return;
    const allLabels = this.change?.labels ?? {};
    const labelInfo = allLabels[this.labelName];
    if (isDetailedLabelInfo(labelInfo)) {
      // votes sorted from best e.g +2 to worst e.g -2
      const votes = this.getSortedVotes(this.labelName);
      if (votes.length > 0) {
        const bestVote = votes[0];
        return html`<gr-vote-chip
          .vote=${bestVote}
          .label=${labelInfo}
          tooltip-with-who-voted
        ></gr-vote-chip>`;
      }
    }
    if (isQuickLabelInfo(labelInfo)) {
      return html`<gr-vote-chip .label=${labelInfo}></gr-vote-chip>`;
    }
    return;
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
      // votes sorted from worst e.g -2 to best e.g +2
      const votes = this.getSortedVotes(label).sort(
        (a, b) => (a.value ?? 0) - (b.value ?? 0)
      );
      if (votes.length === 0) break;
      if (!worstVote || (worstVote.value ?? 0) > (votes[0].value ?? 0)) {
        worstVote = votes[0];
        labelInfo = allLabels[label];
      }
    }
    if (worstVote === undefined) {
      return this.renderStatusIcon(requirement.status);
    } else {
      return html`<gr-vote-chip
        .vote=${worstVote}
        .label=${labelInfo}
        tooltip-with-who-voted
      ></gr-vote-chip>`;
    }
  }

  private renderStatusIcon(status: SubmitRequirementStatus) {
    const icon = iconForStatus(status ?? SubmitRequirementStatus.ERROR);
    return html`<iron-icon class=${icon} icon="gr-icons:${icon}"></iron-icon>`;
  }

  private computeClass(): string {
    if (!this.labelName) return '';
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0 && !this.isTriggerVote(this.labelName)) {
      return 'not-applicable';
    }
    return '';
  }

  private computeLabelTitle() {
    if (!this.labelName) return;
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) {
      if (this.isTriggerVote(this.labelName)) {
        return;
      } else {
        return 'Requirement not applicable';
      }
    }
    const requirement = requirements[0];
    if (requirement.status === SubmitRequirementStatus.UNSATISFIED) {
      const requirementLabels = extractAssociatedLabels(
        requirement,
        'onlySubmittability'
      );
      const allLabels = this.change?.labels ?? {};
      const associatedLabels = Object.keys(allLabels).filter(label =>
        requirementLabels.includes(label)
      );
      const requirementWithoutLabelToVoteOn = associatedLabels.length === 0;
      if (requirementWithoutLabelToVoteOn) {
        const status = capitalizeFirstLetter(requirement.status.toLowerCase());
        return status;
      }

      const everyAssociatedLabelsIsWithoutVotes = associatedLabels.every(
        label => !hasVotes(allLabels[label])
      );
      if (everyAssociatedLabelsIsWithoutVotes) {
        return 'No votes';
      } else {
        return; // there is a vote with tooltip, so undefined label title
      }
    } else {
      return capitalizeFirstLetter(requirement.status.toLowerCase());
    }
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

  private getSortedVotes(label: string) {
    const allLabels = this.change?.labels ?? {};
    const labelInfo = allLabels[label];
    if (isDetailedLabelInfo(labelInfo)) {
      return getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
    }
    return [];
  }

  private isTriggerVote(labelName: string) {
    const triggerVotes = getTriggerVotes(this.change);
    return triggerVotes.includes(labelName);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirement': GrChangeListColumnRequirement;
  }
}
