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

import '../../../styles/gr-change-list-styles';
import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-change-star/gr-change-star';
import '../../shared/gr-change-status/gr-change-status';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-change-list-column/gr-change-list-column';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-list-item_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getDisplayName} from '../../../utils/display-name-util';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {appContext} from '../../../services/app-context';
import {truncatePath} from '../../../utils/path-list-util';
import {changeStatuses} from '../../../utils/change-util';
import {isSelf, isServiceUser} from '../../../utils/account-util';
import {customElement, property} from '@polymer/decorators';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  ChangeInfo,
  ServerInfo,
  AccountInfo,
  QuickLabelInfo,
  Timestamp,
} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
import {pluralize} from '../../../utils/string-util';
import {ChangeStates} from '../../shared/gr-change-status/gr-change-status';

enum ChangeSize {
  XS = 10,
  SMALL = 50,
  MEDIUM = 250,
  LARGE = 1000,
}

// export for testing
export enum LabelCategory {
  NOT_APPLICABLE = 'NOT_APPLICABLE',
  APPROVED = 'APPROVED',
  POSITIVE = 'POSITIVE',
  NEUTRAL = 'NEUTRAL',
  UNRESOLVED_COMMENTS = 'UNRESOLVED_COMMENTS',
  NEGATIVE = 'NEGATIVE',
  REJECTED = 'REJECTED',
}

// How many reviewers should be shown with an account-label?
const PRIMARY_REVIEWERS_COUNT = 2;

@customElement('gr-change-list-item')
export class GrChangeListItem extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /** The logged-in user's account, or null if no user is logged in. */
  @property({type: Object})
  account: AccountInfo | null = null;

  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Array})
  labelNames?: string[];

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  config?: ServerInfo;

  /** Name of the section in the change-list. Used for reporting. */
  @property({type: String})
  sectionName?: string;

  @property({type: String, computed: '_computeChangeURL(change)'})
  changeURL?: string;

  @property({type: Array, computed: '_changeStatuses(change)'})
  statuses?: ChangeStates[];

  @property({type: Boolean})
  showStar = false;

  @property({type: Boolean})
  showNumber = false;

  @property({type: String, computed: '_computeChangeSize(change)'})
  _changeSize?: string;

  @property({type: Array})
  _dynamicCellEndpoints?: string[];

  reporting: ReportingService = appContext.reportingService;

  override connectedCallback() {
    super.connectedCallback();
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicCellEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-list-item-cell'
        );
      });
  }

  _changeStatuses(change?: ChangeInfo) {
    if (!change) return [];
    return changeStatuses(change);
  }

  _computeChangeURL(change?: ChangeInfo) {
    if (!change) return '';
    return GerritNav.getUrlForChange(change);
  }

  _computeLabelTitle(change: ChangeInfo | undefined, labelName: string) {
    const label: QuickLabelInfo | undefined = change?.labels?.[labelName];
    const category = this._computeLabelCategory(change, labelName);
    if (!label || category === LabelCategory.NOT_APPLICABLE) {
      return 'Label not applicable';
    }
    const titleParts: string[] = [];
    if (category === LabelCategory.UNRESOLVED_COMMENTS) {
      const num = change?.unresolved_comment_count ?? 0;
      titleParts.push(pluralize(num, 'unresolved comment'));
    }
    const significantLabel =
      label.rejected || label.approved || label.disliked || label.recommended;
    if (significantLabel?.name) {
      titleParts.push(`${labelName} by ${significantLabel.name}`);
    }
    if (titleParts.length > 0) {
      return titleParts.join(',\n');
    }
    return labelName;
  }

  _computeLabelClass(change: ChangeInfo | undefined, labelName: string) {
    const category = this._computeLabelCategory(change, labelName);
    const classes = ['cell', 'label'];
    switch (category) {
      case LabelCategory.NOT_APPLICABLE:
        classes.push('u-gray-background');
        break;
      case LabelCategory.APPROVED:
        classes.push('u-green');
        break;
      case LabelCategory.POSITIVE:
        classes.push('u-monospace');
        classes.push('u-green');
        break;
      case LabelCategory.NEGATIVE:
        classes.push('u-monospace');
        classes.push('u-red');
        break;
      case LabelCategory.REJECTED:
        classes.push('u-red');
        break;
    }
    return classes.sort().join(' ');
  }

  _computeHasLabelIcon(change: ChangeInfo | undefined, labelName: string) {
    return this._computeLabelIcon(change, labelName) !== '';
  }

  _computeLabelIcon(change: ChangeInfo | undefined, labelName: string): string {
    const category = this._computeLabelCategory(change, labelName);
    switch (category) {
      case LabelCategory.APPROVED:
        return 'gr-icons:check';
      case LabelCategory.UNRESOLVED_COMMENTS:
        return 'gr-icons:comment';
      case LabelCategory.REJECTED:
        return 'gr-icons:close';
      default:
        return '';
    }
  }

  _computeLabelCategory(change: ChangeInfo | undefined, labelName: string) {
    const label: QuickLabelInfo | undefined = change?.labels?.[labelName];
    if (!label) {
      return LabelCategory.NOT_APPLICABLE;
    }
    if (label.rejected) {
      return LabelCategory.REJECTED;
    }
    if (label.value && label.value < 0) {
      return LabelCategory.NEGATIVE;
    }
    if (change?.unresolved_comment_count && labelName === 'Code-Review') {
      return LabelCategory.UNRESOLVED_COMMENTS;
    }
    if (label.approved) {
      return LabelCategory.APPROVED;
    }
    if (label.value && label.value > 0) {
      return LabelCategory.POSITIVE;
    }
    return LabelCategory.NEUTRAL;
  }

  _computeLabelValue(change: ChangeInfo | undefined, labelName: string) {
    const label: QuickLabelInfo | undefined = change?.labels?.[labelName];
    const category = this._computeLabelCategory(change, labelName);
    switch (category) {
      case LabelCategory.NOT_APPLICABLE:
        return '';
      case LabelCategory.APPROVED:
        return '\u2713'; // ✓
      case LabelCategory.POSITIVE:
        return `+${label?.value}`;
      case LabelCategory.NEUTRAL:
        return '';
      case LabelCategory.UNRESOLVED_COMMENTS:
        return 'u';
      case LabelCategory.NEGATIVE:
        return `${label?.value}`;
      case LabelCategory.REJECTED:
        return '\u2715'; // ✕
    }
  }

  _computeRepoUrl(change?: ChangeInfo) {
    if (!change) return '';
    return GerritNav.getUrlForProjectChanges(
      change.project,
      true,
      change.internalHost
    );
  }

  _computeRepoBranchURL(change?: ChangeInfo) {
    if (!change) return '';
    return GerritNav.getUrlForBranch(
      change.branch,
      change.project,
      undefined,
      change.internalHost
    );
  }

  _computeTopicURL(change?: ChangeInfo) {
    if (!change?.topic) {
      return '';
    }
    return GerritNav.getUrlForTopic(change.topic, change.internalHost);
  }

  /**
   * Computes the display string for the project column. If there is a host
   * specified in the change detail, the string will be prefixed with it.
   *
   * @param truncate whether or not the project name should be
   * truncated. If this value is truthy, the name will be truncated.
   */
  _computeRepoDisplay(change?: ChangeInfo) {
    if (!change?.project) {
      return '';
    }
    let str = '';
    if (change.internalHost) {
      str += change.internalHost + '/';
    }
    str += change.project;
    return str;
  }

  _computeTruncatedRepoDisplay(change?: ChangeInfo) {
    if (!change?.project) {
      return '';
    }
    let str = '';
    if (change.internalHost) {
      str += change.internalHost + '/';
    }
    str += truncatePath(change.project, 2);
    return str;
  }

  _computeSizeTooltip(change?: ChangeInfo) {
    if (
      !change ||
      change.insertions + change.deletions === 0 ||
      isNaN(change.insertions + change.deletions)
    ) {
      return 'Size unknown';
    } else {
      return `added ${change.insertions}, removed ${change.deletions} lines`;
    }
  }

  _hasAttention(account: AccountInfo) {
    if (!this.change || !this.change.attention_set || !account._account_id) {
      return false;
    }
    return hasOwnProperty(this.change.attention_set, account._account_id);
  }

  /**
   * Computes the array of all reviewers with sorting the reviewers in the
   * attention set before others, and the current user first.
   */
  _computeReviewers(change?: ChangeInfo) {
    if (!change?.reviewers || !change?.reviewers.REVIEWER) return [];
    const reviewers = [...change.reviewers.REVIEWER].filter(
      r =>
        (!change.owner || change.owner._account_id !== r._account_id) &&
        !isServiceUser(r)
    );
    reviewers.sort((r1, r2) => {
      if (this.account) {
        if (isSelf(r1, this.account)) return -1;
        if (isSelf(r2, this.account)) return 1;
      }
      if (this._hasAttention(r1) && !this._hasAttention(r2)) return -1;
      if (this._hasAttention(r2) && !this._hasAttention(r1)) return 1;
      return (r1.name || '').localeCompare(r2.name || '');
    });
    return reviewers;
  }

  _computePrimaryReviewers(change?: ChangeInfo) {
    return this._computeReviewers(change).slice(0, PRIMARY_REVIEWERS_COUNT);
  }

  _computeAdditionalReviewers(change?: ChangeInfo) {
    return this._computeReviewers(change).slice(PRIMARY_REVIEWERS_COUNT);
  }

  _computeAdditionalReviewersCount(change?: ChangeInfo) {
    return this._computeAdditionalReviewers(change).length;
  }

  _computeAdditionalReviewersTitle(change?: ChangeInfo, config?: ServerInfo) {
    if (!change || !config) return '';
    return this._computeAdditionalReviewers(change)
      .map(user => getDisplayName(config, user, true))
      .join(', ');
  }

  _computeComments(unresolved_comment_count?: number) {
    if (!unresolved_comment_count || unresolved_comment_count < 1) return '';
    return `${unresolved_comment_count} unresolved`;
  }

  /**
   * TShirt sizing is based on the following paper:
   * http://dirkriehle.com/wp-content/uploads/2008/09/hicss-42-csdistr-final-web.pdf
   */
  _computeChangeSize(change?: ChangeInfo) {
    if (!change) return null;
    const delta = change.insertions + change.deletions;
    if (isNaN(delta) || delta === 0) {
      return null; // Unknown
    }
    if (delta < ChangeSize.XS) {
      return 'XS';
    } else if (delta < ChangeSize.SMALL) {
      return 'S';
    } else if (delta < ChangeSize.MEDIUM) {
      return 'M';
    } else if (delta < ChangeSize.LARGE) {
      return 'L';
    } else {
      return 'XL';
    }
  }

  _computeWaiting(
    account?: AccountInfo | null,
    change?: ChangeInfo | null
  ): Timestamp | undefined {
    if (!account?._account_id || !change?.attention_set) return undefined;
    return change?.attention_set[account._account_id]?.last_update;
  }

  _computeIsColumnHidden(columnToCheck?: string, columnsToDisplay?: string[]) {
    if (!columnsToDisplay || !columnToCheck) {
      return false;
    }
    return !columnsToDisplay.includes(columnToCheck);
  }

  _formatDate(date: Timestamp | undefined): string | undefined {
    if (!date) return undefined;
    return date.toString();
  }

  _handleChangeClick() {
    // Don't prevent the default and neither stop bubbling. We just want to
    // report the click, but then let the browser handle the click on the link.

    const selfId = (this.account && this.account._account_id) || -1;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;

    this.reporting.reportInteraction('change-row-clicked', {
      section: this.sectionName,
      isOwner: selfId === ownerId,
    });
  }

  _computeCommaHidden(index?: number, change?: ChangeInfo) {
    if (index === undefined) return false;
    if (change === undefined) return false;

    const additionalCount = this._computeAdditionalReviewersCount(change);
    const primaryCount = this._computePrimaryReviewers(change).length;
    const isLast = index === primaryCount - 1;
    return isLast && additionalCount === 0;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-item': GrChangeListItem;
  }
}
