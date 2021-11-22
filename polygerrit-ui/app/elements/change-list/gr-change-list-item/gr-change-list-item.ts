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

import '../../shared/gr-account-link/gr-account-link';
import '../../shared/gr-change-star/gr-change-star';
import '../../shared/gr-change-status/gr-change-status';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-change-list-column/gr-change-list-column';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {getDisplayName} from '../../../utils/display-name-util';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {getAppContext} from '../../../services/app-context';
import {truncatePath} from '../../../utils/path-list-util';
import {changeStatuses} from '../../../utils/change-util';
import {isSelf, isServiceUser} from '../../../utils/account-util';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  ChangeInfo,
  ServerInfo,
  AccountInfo,
  QuickLabelInfo,
  Timestamp,
} from '../../../types/common';
import {assertNever, hasOwnProperty} from '../../../utils/common-util';
import {pluralize} from '../../../utils/string-util';
import {KnownExperimentId} from '../../../services/flags/flags';
import {getRequirements, iconForStatus} from '../../../utils/label-util';
import {SubmitRequirementStatus} from '../../../api/rest-api';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property/*, state*/} from 'lit/decorators';

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

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-item': GrChangeListItem;
  }
}

@customElement('gr-change-list-item')
export class GrChangeListItem extends LitElement {
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

  @property({type: Boolean})
  showStar = false;

  @property({type: Boolean})
  showNumber = false;

  @property({type: Array})
  _dynamicCellEndpoints?: string[];

  @property({type: Boolean})
  _isSubmitRequirementsUiEnabled = false;

  reporting: ReportingService = getAppContext().reportingService;

  private readonly flagsService = getAppContext().flagsService;

  override connectedCallback() {
    super.connectedCallback();
    this._isSubmitRequirementsUiEnabled = this.flagsService.isEnabled(
      KnownExperimentId.SUBMIT_REQUIREMENTS_UI
    );
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicCellEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-list-item-cell'
        );
      });
  }

  static override get styles() {
    return [
      changeListStyles,
      sharedStyles,
      css`
        :host {
          display: table-row;
          color: var(--primary-text-color);
        }
        :host(:focus) {
          outline: none;
        }
        :host(:hover) {
          background-color: var(--hover-background-color);
        }
        .container {
          position: relative;
        }
        .content {
          overflow: hidden;
          position: absolute;
          text-overflow: ellipsis;
          white-space: nowrap;
          width: 100%;
        }
        .content a {
          display: block;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          width: 100%;
        }
        .comments,
        .reviewers,
        .requirements {
          white-space: nowrap;
        }
        .reviewers {
          --account-max-length: 70px;
        }
        .spacer {
          height: 0;
          overflow: hidden;
        }
        .status {
          align-items: center;
          display: inline-flex;
        }
        .status .comma {
          padding-right: var(--spacing-xs);
        }
        /* Used to hide the leading separator comma for statuses. */
        .status .comma:first-of-type {
          display: none;
        }
        .size gr-tooltip-content {
          margin: -0.4rem -0.6rem;
          max-width: 2.5rem;
          padding: var(--spacing-m) var(--spacing-l);
        }
        a {
          color: inherit;
          cursor: pointer;
          text-decoration: none;
        }
        a:hover {
          text-decoration: underline;
        }
        .subject:hover .content {
          text-decoration: underline;
        }
        .u-monospace {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
        }
        .u-green,
        .u-green iron-icon {
          color: var(--positive-green-text-color);
        }
        .u-red,
        .u-red iron-icon {
          color: var(--negative-red-text-color);
        }
        .u-gray-background {
          background-color: var(--table-header-background-color);
        }
        .comma,
        .placeholder {
          color: var(--deemphasized-text-color);
        }
        .cell.label {
          font-weight: var(--font-weight-normal);
        }
        .cell.label iron-icon {
          vertical-align: top;
        }
        @media only screen and (max-width: 50em) {
          :host {
            display: flex;
          }
        }
      `,
    ];
  }

  override render() {
    const changeUrl = this._computeChangeURL(this.change);
    return html`
      <td aria-hidden="true" class="cell leftPadding"></td>
      <td class="cell star" ?hidden=${!this.showStar}>
        <gr-change-star .change=${this.change}></gr-change-star>
      </td>
      <td class="cell number" ?hidden=${!this.showNumber}>
        <a href="${changeUrl}">${this.change?._number}</a>
      </td>
      <td
        class="cell subject"
        ?hidden=${this._computeIsColumnHidden(
          'Subject',
          this.visibleChangeTableColumns
        )}
      >
        <a
          title="${this.change?.subject}"
          href="${changeUrl}"
          @click=${() => this._handleChangeClick()}
        >
          <div class="container">
            <div class="content">${this.change?.subject}</div>
            <div class="spacer">${this.change?.subject}</div>
            <span>&nbsp;</span>
          </div>
        </a>
      </td>
      <td
        class="cell status"
        ?hidden=${this._computeIsColumnHidden(
          'Status',
          this.visibleChangeTableColumns
        )}
      >
        ${this.renderChangeStatus()}
      </td>
      <td
        class="cell owner"
        ?hidden=${this._computeIsColumnHidden(
          'Owner',
          this.visibleChangeTableColumns
        )}
      >
        <gr-account-link
          highlightAttention
          .change=${this.change}
          .account=${this.change?.owner}
        ></gr-account-link>
      </td>
      <td
        class="cell reviewers"
        ?hidden=${this._computeIsColumnHidden(
          'Reviewers',
          this.visibleChangeTableColumns
        )}
      >
        <div>
          ${this._computePrimaryReviewers(this.change).map((reviewer, index) =>
            this.renderChangeReviewers(reviewer, index)
          )}
          ${this._computeAdditionalReviewersCount(this.change)
            ? html`<span
                title="${this._computeAdditionalReviewersTitle(
                  this.change,
                  this.config
                )}"
                >+${this._computeAdditionalReviewersCount(this.change)}</span
              >`
            : ''}
        </div>
      </td>
      <td
        class="cell comments"
        ?hidden=${this._computeIsColumnHidden(
          'Comments',
          this.visibleChangeTableColumns
        )}
      >
        <iron-icon
          ?hidden=${!this.change?.unresolved_comment_count}
          icon="gr-icons:comment"
        ></iron-icon>
        <span
          >${this._computeComments(this.change?.unresolved_comment_count)}</span
        >
      </td>
      <td
        class="cell repo"
        ?hidden=${this._computeIsColumnHidden(
          'Repo',
          this.visibleChangeTableColumns
        )}
      >
        <a class="fullRepo" href="${this._computeRepoUrl(this.change)}">
          ${this._computeRepoDisplay(this.change)}
        </a>
        <a
          class="truncatedRepo"
          href="${this._computeRepoUrl(this.change)}"
          title="${this._computeRepoDisplay(this.change)}"
        >
          ${this._computeTruncatedRepoDisplay(this.change)}
        </a>
      </td>
      <td
        class="cell branch"
        ?hidden="${this._computeIsColumnHidden(
          'Branch',
          this.visibleChangeTableColumns
        )}"
      >
        <a href="${this._computeRepoBranchURL(this.change)}">
          ${this.change?.branch}
        </a>
        ${this.renderChangeBranch()}
      </td>
      <td
        class="cell updated"
        ?hidden=${this._computeIsColumnHidden(
          'Updated',
          this.visibleChangeTableColumns
        )}
      >
        <gr-date-formatter
          withTooltip
          .dateStr=${this._formatDate(this.change?.updated)}
        ></gr-date-formatter>
      </td>
      <td
        class="cell submitted"
        ?hidden=${this._computeIsColumnHidden(
          'Submitted',
          this.visibleChangeTableColumns
        )}
      >
        <gr-date-formatter
          withTooltip
          .dateStr=${this._formatDate(this.change?.submitted)}
        ></gr-date-formatter>
      </td>
      <td
        class="cell waiting"
        ?hidden=${this._computeIsColumnHidden(
          'Waiting',
          this.visibleChangeTableColumns
        )}
      >
        <gr-date-formatter
          withTooltip
          forceRelative
          relativeOptionNoAgo
          .dateStr=${this._computeWaiting(this.account, this.change)}
        ></gr-date-formatter>
      </td>
      <td
        class="cell size"
        ?hidden=${this._computeIsColumnHidden(
          'Size',
          this.visibleChangeTableColumns
        )}
      >
        <gr-tooltip-content
          hasTooltip
          title="${this._computeSizeTooltip(this.change)}"
        >
          ${this.renderChangeSize()}
        </gr-tooltip-content>
      </td>
      <td
        class="cell requirements"
        ?hidden=${this._computeIsColumnHidden(
          'Requirements',
          this.visibleChangeTableColumns
        )}
      >
        <gr-change-list-column-requirements .change=${this.change}>
        </gr-change-list-column-requirements>
      </td>
      ${this.labelNames?.map(labelNames => this.renderChangeLabels(labelNames))}
      ${this._dynamicCellEndpoints?.map(pluginEndpointName =>
        this.renderChangePluginEndpoint(pluginEndpointName)
      )}
    `;
  }

  private renderChangeStatus() {
    const statuses = this._changeStatuses(this.change);
    if (!statuses.length) {
      return html`<span class="placeholder">--</span>`;
    }

    return statuses.map(
      status => html`
        <div class="comma">,</div>
        <gr-change-status flat .status=${status}></gr-change-status>
      `
    );
  }

  private renderChangeReviewers(reviewer: AccountInfo, index: number) {
    return html`
      <gr-account-link
        hideAvatar
        hideStatus
        firstName
        highlightAttention
        .change=${this.change}
        .account=${reviewer}
      ></gr-account-link
      ><span
        ?hidden=${this._computeCommaHidden(index, this.change)}
        aria-hidden="true"
        >,
      </span>
    `;
  }

  private renderChangeBranch() {
    if (!this.change?.topic) return;

    return html`
      (<a href="${this._computeTopicURL(this.change)}"
        ><!--
       --><gr-limited-text .limit=${50} .text=${this.change.topic}>
        </gr-limited-text
        ><!--
     --></a
      >)
    `;
  }

  private renderChangeSize() {
    const changeSize = this._computeChangeSize(this.change);
    if (!changeSize) return html`<span class="placeholder">--</span>`;

    return html` <span>${changeSize}</span> `;
  }

  private renderChangeLabels(labelName: string) {
    return html`
      <td
        title="${this._computeLabelTitle(this.change, labelName)}"
        class="${this._computeLabelClass(this.change, labelName)}"
      >
        ${this.renderChangeHasLabelIcon(labelName)}
      </td>
    `;
  }

  private renderChangeHasLabelIcon(labelName: string) {
    if (!this._computeHasLabelIcon(this.change, labelName))
      return html`<span
        >${this._computeLabelValue(this.change, labelName)}</span
      >`;

    return html`
      <iron-icon
        icon=${this._computeLabelIcon(this.change, labelName)}
      ></iron-icon>
    `;
  }

  private renderChangePluginEndpoint(pluginEndpointName: string) {
    return html`
      <td class="cell endpoint">
        <gr-endpoint-decorator name="${pluginEndpointName}">
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </td>
    `;
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
    const classes = ['cell', 'label'];
    if (this._isSubmitRequirementsUiEnabled) {
      const requirements = getRequirements(change).filter(
        sr => sr.name === labelName
      );
      if (requirements.length === 1) {
        const status = requirements[0].status;
        switch (status) {
          case SubmitRequirementStatus.SATISFIED:
            classes.push('u-green');
            break;
          case SubmitRequirementStatus.UNSATISFIED:
            classes.push('u-red');
            break;
          case SubmitRequirementStatus.OVERRIDDEN:
            classes.push('u-green');
            break;
          case SubmitRequirementStatus.NOT_APPLICABLE:
            classes.push('u-gray-background');
            break;
          default:
            assertNever(status, `Unsupported status: ${status}`);
        }
        return classes.sort().join(' ');
      }
    }
    const category = this._computeLabelCategory(change, labelName);
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
    if (this._isSubmitRequirementsUiEnabled) {
      const requirements = getRequirements(change).filter(
        sr => sr.name === labelName
      );
      if (requirements.length === 1) {
        return `gr-icons:${iconForStatus(requirements[0].status)}`;
      }
    }
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
