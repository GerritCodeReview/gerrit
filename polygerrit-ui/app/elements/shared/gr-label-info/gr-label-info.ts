/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../../styles/gr-font-styles';
import '../../../styles/gr-voting-styles';
import '../../../styles/shared-styles';
import '../gr-account-label/gr-account-label';
import '../gr-account-link/gr-account-link';
import '../gr-account-chip/gr-account-chip';
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../gr-label/gr-label';
import '../gr-tooltip-content/gr-tooltip-content';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  AccountInfo,
  LabelInfo,
  ApprovalInfo,
  AccountId,
  isQuickLabelInfo,
  isDetailedLabelInfo,
  LabelNameToInfoMap,
} from '../../../types/common';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {GrButton} from '../gr-button/gr-button';
import {
  canVote,
  getApprovalInfo,
  getVotingRangeOrDefault,
} from '../../../utils/label-util';
import {appContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {votingStyles} from '../../../styles/gr-voting-styles';
import {ifDefined} from 'lit/directives/if-defined';
import {KnownExperimentId} from '../../../services/flags/flags';

declare global {
  interface HTMLElementTagNameMap {
    'gr-label-info': GrLabelInfo;
  }
}

enum LabelClassName {
  NEGATIVE = 'negative',
  POSITIVE = 'positive',
  MIN = 'min',
  MAX = 'max',
}

interface FormattedLabel {
  className?: LabelClassName;
  account: ApprovalInfo | AccountInfo;
  value: string;
}

@customElement('gr-label-info')
export class GrLabelInfo extends LitElement {
  @property({type: Object})
  labelInfo?: LabelInfo;

  @property({type: String})
  label = '';

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable = false;

  private readonly restApiService = appContext.restApiService;

  private readonly reporting = appContext.reportingService;

  // TODO(TS): not used, remove later
  _xhrPromise?: Promise<void>;

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      votingStyles,
      css`
        .placeholder {
          color: var(--deemphasized-text-color);
        }
        .hidden {
          display: none;
        }
        /* Note that most of the .voteChip styles are coming from the
         gr-voting-styles include. */
        .voteChip {
          display: flex;
          justify-content: center;
          margin-right: var(--spacing-s);
          padding: 1px;
        }
        .max {
          background-color: var(--vote-color-approved);
        }
        .min {
          background-color: var(--vote-color-rejected);
        }
        .positive {
          background-color: var(--vote-color-recommended);
          border-radius: 12px;
          border: 1px solid var(--vote-outline-recommended);
          color: var(--chip-color);
        }
        .negative {
          background-color: var(--vote-color-disliked);
          border-radius: 12px;
          border: 1px solid var(--vote-outline-disliked);
          color: var(--chip-color);
        }
        .hidden {
          display: none;
        }
        td {
          vertical-align: top;
        }
        tr {
          min-height: var(--line-height-normal);
        }
        gr-tooltip-content {
          display: block;
        }
        gr-button {
          vertical-align: top;
        }
        gr-button::part(paper-button) {
          height: var(--line-height-normal);
          width: var(--line-height-normal);
          padding: 0;
        }
        gr-button[disabled] iron-icon {
          color: var(--border-color);
        }
        gr-account-link {
          --account-max-length: 100px;
          margin-right: var(--spacing-xs);
        }
        iron-icon {
          height: calc(var(--line-height-normal) - 2px);
          width: calc(var(--line-height-normal) - 2px);
        }
        .labelValueContainer:not(:first-of-type) td {
          padding-top: var(--spacing-s);
        }
      `,
    ];
  }

  private readonly flagsService = appContext.flagsService;

  override render() {
    const {labelInfo} = this;
    if (this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI)) {
      if (!labelInfo || !isDetailedLabelInfo(labelInfo)) return;
      const reviewers = (this.change?.reviewers['REVIEWER'] ?? []).filter(
        reviewer => canVote(labelInfo, reviewer)
      );
      return html`<div>
        ${reviewers.map(
          reviewer => html` <gr-account-chip
            .account="${reviewer}"
            .change="${this.change}"
          >
            <gr-vote-chip
              slot="vote-chip"
              .vote="${getApprovalInfo(labelInfo, reviewer)}"
              .label="${labelInfo}"
            ></gr-vote-chip
          ></gr-account-chip>`
        )}
      </div>`;
    }
    return html` <p
        class="placeholder ${this.computeShowPlaceholder(
          labelInfo,
          this.change?.labels
        )}"
      >
        No votes
      </p>
      <table>
        ${this.mapLabelInfo(labelInfo, this.account, this.change?.labels).map(
          mappedLabel => this.renderLabel(mappedLabel)
        )}
      </table>`;
  }

  renderLabel(mappedLabel: FormattedLabel) {
    const {labelInfo, change} = this;
    return html` <tr class="labelValueContainer">
      <td>
        <gr-tooltip-content
          has-tooltip
          title="${this._computeValueTooltip(labelInfo, mappedLabel.value)}"
        >
          <gr-label class="${mappedLabel.className} voteChip font-small">
            ${mappedLabel.value}
          </gr-label>
        </gr-tooltip-content>
      </td>
      <td>
        <gr-account-link
          .account="${mappedLabel.account}"
          .change="${change}"
        ></gr-account-link>
      </td>
      <td>
        <gr-tooltip-content has-tooltip title="Remove vote">
          <gr-button
            link
            aria-label="Remove vote"
            @click="${this.onDeleteVote}"
            data-account-id="${ifDefined(mappedLabel.account._account_id)}"
            class="deleteBtn ${this.computeDeleteClass(
              mappedLabel.account,
              this.mutable,
              change
            )}"
          >
            <iron-icon icon="gr-icons:delete"></iron-icon>
          </gr-button>
        </gr-tooltip-content>
      </td>
    </tr>`;
  }

  /**
   * This method also listens on change.labels.*,
   * to trigger computation when a label is removed from the change.
   *
   * The third parameter is just for *triggering* computation.
   */
  private mapLabelInfo(
    labelInfo?: LabelInfo,
    account?: AccountInfo,
    _?: LabelNameToInfoMap
  ): FormattedLabel[] {
    const result: FormattedLabel[] = [];
    if (!labelInfo) {
      return result;
    }
    if (!isDetailedLabelInfo(labelInfo)) {
      if (
        isQuickLabelInfo(labelInfo) &&
        (labelInfo.rejected || labelInfo.approved)
      ) {
        const ok = labelInfo.approved || !labelInfo.rejected;
        return [
          {
            value: ok ? '👍️' : '👎️',
            className: ok ? LabelClassName.POSITIVE : LabelClassName.NEGATIVE,
            // executed only if approved or rejected is not undefined
            account: ok ? labelInfo.approved! : labelInfo.rejected!,
          },
        ];
      }
      return result;
    }

    // Sort votes by positivity.
    // TODO(TS): maybe mark value as required if always present
    const votes = (labelInfo.all || []).sort(
      (a, b) => (a.value || 0) - (b.value || 0)
    );
    const votingRange = getVotingRangeOrDefault(labelInfo);
    for (const label of votes) {
      if (
        label.value &&
        (!isQuickLabelInfo(labelInfo) ||
          label.value !== labelInfo.default_value)
      ) {
        let labelClassName;
        let labelValPrefix = '';
        if (label.value > 0) {
          labelValPrefix = '+';
          if (label.value === votingRange.max) {
            labelClassName = LabelClassName.MAX;
          } else {
            labelClassName = LabelClassName.POSITIVE;
          }
        } else if (label.value < 0) {
          if (label.value === votingRange.min) {
            labelClassName = LabelClassName.MIN;
          } else {
            labelClassName = LabelClassName.NEGATIVE;
          }
        }
        const formattedLabel: FormattedLabel = {
          value: `${labelValPrefix}${label.value}`,
          className: labelClassName,
          account: label,
        };
        if (label._account_id === account?._account_id) {
          // Put self-votes at the top.
          result.unshift(formattedLabel);
        } else {
          result.push(formattedLabel);
        }
      }
    }
    return result;
  }

  /**
   * A user is able to delete a vote iff the mutable property is true and the
   * reviewer that left the vote exists in the list of removable_reviewers
   * received from the backend.
   *
   * @param reviewer An object describing the reviewer that left the
   *     vote.
   */
  private computeDeleteClass(
    reviewer: ApprovalInfo,
    mutable: boolean,
    change?: ParsedChangeInfo
  ) {
    if (!mutable || !change || !change.removable_reviewers) {
      return 'hidden';
    }
    const removable = change.removable_reviewers;
    if (removable.find(r => r._account_id === reviewer?._account_id)) {
      return '';
    }
    return 'hidden';
  }

  /**
   * Closure annotation for Polymer.prototype.splice is off.
   * For now, suppressing annotations.
   */
  private onDeleteVote(e: MouseEvent) {
    if (!this.change) return;

    e.preventDefault();
    let target = (dom(e) as EventApi).rootTarget as GrButton;
    while (!target.classList.contains('deleteBtn')) {
      if (!target.parentElement) {
        return;
      }
      target = target.parentElement as GrButton;
    }

    target.disabled = true;
    const accountID = Number(
      `${target.getAttribute('data-account-id')}`
    ) as AccountId;
    this._xhrPromise = this.restApiService
      .deleteVote(this.change._number, accountID, this.label)
      .then(response => {
        target.disabled = false;
        if (!response.ok) {
          return;
        }
        if (this.change) {
          GerritNav.navigateToChange(this.change);
        }
      })
      .catch(err => {
        this.reporting.error(err);
        target.disabled = false;
        return;
      });
  }

  _computeValueTooltip(labelInfo: LabelInfo | undefined, score: string) {
    if (
      !labelInfo ||
      !isDetailedLabelInfo(labelInfo) ||
      !labelInfo.values?.[score]
    ) {
      return '';
    }
    return labelInfo.values[score];
  }

  /**
   * This method also listens change.labels.* in
   * order to trigger computation when a label is removed from the change.
   *
   * The second parameter is just for *triggering* computation.
   */
  private computeShowPlaceholder(
    labelInfo?: LabelInfo,
    _?: LabelNameToInfoMap
  ) {
    if (!labelInfo) {
      return '';
    }
    if (
      !isDetailedLabelInfo(labelInfo) &&
      isQuickLabelInfo(labelInfo) &&
      (labelInfo.rejected || labelInfo.approved)
    ) {
      return 'hidden';
    }

    if (isDetailedLabelInfo(labelInfo) && labelInfo.all) {
      for (const label of labelInfo.all) {
        if (
          label.value &&
          (!isQuickLabelInfo(labelInfo) ||
            label.value !== labelInfo.default_value)
        ) {
          return 'hidden';
        }
      }
    }
    return '';
  }
}
