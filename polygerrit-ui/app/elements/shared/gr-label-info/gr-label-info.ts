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
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../gr-label/gr-label';
import '../gr-tooltip-content/gr-tooltip-content';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-label-info_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {
  AccountInfo,
  LabelInfo,
  ApprovalInfo,
  AccountId,
  isQuickLabelInfo,
  isDetailedLabelInfo,
} from '../../../types/common';
import {GrButton} from '../gr-button/gr-button';
import {getVotingRangeOrDefault} from '../../../utils/label-util';
import {appContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';

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
  account: ApprovalInfo;
  value: string;
}

@customElement('gr-label-info')
export class GrLabelInfo extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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

  /**
   * This method also listens on change.labels.*,
   * to trigger computation when a label is removed from the change.
   *
   * The third parameter is just for *triggering* computation.
   */
  _mapLabelInfo(labelInfo?: LabelInfo, account?: AccountInfo, _?: unknown) {
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
            account: ok ? labelInfo.approved : labelInfo.rejected,
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
        const formattedLabel = {
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
  _computeDeleteClass(
    reviewer: ApprovalInfo | AccountInfo | undefined,
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
  _onDeleteVote(e: MouseEvent) {
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
  _computeShowPlaceholder(labelInfo?: LabelInfo, _?: unknown) {
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
