/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-account-label/gr-account-label';
import '../../shared/gr-change-star/gr-change-star';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-change-list-column-requirements-summary/gr-change-list-column-requirements-summary';
import '../gr-change-list-column-requirement/gr-change-list-column-requirement';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getDisplayName} from '../../../utils/display-name-util';
import {getAppContext} from '../../../services/app-context';
import {truncatePath} from '../../../utils/path-list-util';
import {isSelf, isServiceUser} from '../../../utils/account-util';
import {
  AccountInfo,
  ChangeInfo,
  NumericChangeId,
  ServerInfo,
  Timestamp,
  UserId,
} from '../../../types/common';
import {assertIsDefined, hasOwnProperty} from '../../../utils/common-util';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {ifDefined} from 'lit/directives/if-defined.js';
import {ChangeStatus, ColumnNames, WAITING} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {classMap} from 'lit/directives/class-map.js';
import {createSearchUrl} from '../../../models/views/search';
import {createChangeUrl} from '../../../models/views/change';
import {userModelToken} from '../../../models/user/user-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {configModelToken} from '../../../models/config/config-model';
import {formStyles} from '../../../styles/form-styles';

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
  /** The logged-in user's account, or undefined if no user is logged in. */
  @property({type: Object})
  loggedInUser?: AccountInfo;

  /**
   * When the list is part of the dashboard, the user for which the dashboard is
   * generated.
   */
  @property({type: String})
  dashboardUser?: UserId | 'self';

  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Array})
  labelNames?: string[];

  @property({type: Object})
  change?: ChangeInfo;

  /** Name of the section in the change-list. Used for reporting. */
  @property({type: String})
  sectionName?: string;

  @property({type: Boolean})
  showNumber = false;

  @property({type: String})
  usp?: string;

  /** Index of the item in the overall list. */
  @property({type: Number})
  globalIndex = 0;

  /** Callback to call to request the item to be selected in the list. */
  @property({type: Function})
  triggerSelectionCallback?: (globalIndex: number) => void;

  @property({type: Boolean, reflect: true}) selected = false;

  // private but used in tests
  @property({type: Boolean, reflect: true}) checked = false;

  @state()
  config?: ServerInfo;

  @state() private dynamicCellEndpoints?: string[];

  private readonly reporting = getAppContext().reportingService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  private readonly getUserModel = resolve(this, userModelToken);

  @state() private isLoggedIn = false;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => {
        this.updateCheckedState(selectedChangeNums);
      }
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      isLoggedIn => (this.isLoggedIn = isLoggedIn)
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.config = config;
      }
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this.dynamicCellEndpoints =
          this.getPluginLoader().pluginEndPoints.getDynamicEndpoints(
            'change-list-item-cell'
          );
      });
    this.addEventListener('click', this.onItemClick);
  }

  override disconnectedCallback() {
    this.removeEventListener('click', this.onItemClick);
  }

  override willUpdate(changedProperties: PropertyValues<this>) {
    // When the cursor selects this item, give it focus so that the item is read
    // out by screen readers and lets users start tabbing through the item
    if (this.selected && changedProperties.has('selected')) {
      this.focus();
    }

    if (changedProperties.has('change')) {
      this.updateCheckedState(
        this.getBulkActionsModel().getState().selectedChangeNums
      );
    }
  }

  private updateCheckedState(selectedChangeNums: NumericChangeId[]) {
    if (!this.change) {
      this.checked = false;
      return;
    }
    this.checked = selectedChangeNums.includes(this.change._number);
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      submitRequirementsStyles,
      changeListStyles,
      css`
        :host {
          display: table-row;
          color: var(--primary-text-color);
        }
        :host(:focus) {
          outline: none;
        }
        :host([checked]),
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
        .size gr-tooltip-content {
          margin: -0.4rem -0.6rem;
          max-width: 2.5rem;
          padding: var(--spacing-m) var(--spacing-l);
        }
        .size span {
          border-radius: var(--border-radius);
          color: var(--dashboard-size-text);
          font-size: var(--font-size-small);
          /* To set height and width of span, it has to be inline block */
          display: inline-block;
          height: 20px;
          width: 20px;
          text-align: center;
          vertical-align: top;
        }
        .size span.size-xs {
          background-color: var(--dashboard-size-xs);
          color: var(--dashboard-size-xs-text);
        }
        .size span.size-s {
          background-color: var(--dashboard-size-s);
        }
        .size span.size-m {
          background-color: var(--dashboard-size-m);
        }
        .size span.size-l {
          background-color: var(--dashboard-size-l);
        }
        .size span.size-xl {
          background-color: var(--dashboard-size-xl);
          color: var(--dashboard-size-xl-text);
        }
        a {
          color: inherit;
          cursor: pointer;
          text-decoration: none;
        }
        /* The subject cell needs a separate rule for these reasons:
           1. :hover does not propagate to absolutely positioned children.
           2. .strikethrough for abandoned changes must be respected.
           3. We don't want the "spacer" and the &nbsp; to be underlined.
        */
        .cell:not(.subject) a:hover,
        .cell.subject a:hover .content:not(.strikethrough) {
          text-decoration: underline;
        }
        .strikethrough {
          color: var(--deemphasized-text-color);
          text-decoration: line-through;
        }
        .comma,
        .placeholder {
          color: var(--deemphasized-text-color);
        }
        .cell.selection input {
          vertical-align: middle;
        }
        .selectionLabel {
          padding: 10px;
          margin: -10px;
          display: block;
        }
        .cell.label {
          font-weight: var(--font-weight-normal);
        }
        .cell.label gr-icon {
          vertical-align: top;
        }
        /* Requirement child needs whole area */
        .cell.requirement {
          padding: 0;
          margin: 0;
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
    const changeUrl = this.computeChangeURL();
    return html`
      <td aria-hidden="true" class="cell leftPadding"></td>
      ${this.renderCellSelectionBox()} ${this.renderCellStar()}
      ${this.renderCellNumber(changeUrl)} ${this.renderCellSubject(changeUrl)}
      ${this.renderCellOwner()} ${this.renderCellReviewers()}
      ${this.renderCellRepo()} ${this.renderCellBranch()}
      ${this.renderCellUpdated()} ${this.renderCellSubmitted()}
      ${this.renderCellWaiting()} ${this.renderCellSize()}
      ${this.renderCellRequirements()}
      ${this.labelNames?.map(labelNames => this.renderChangeLabels(labelNames))}
      ${this.dynamicCellEndpoints?.map(pluginEndpointName =>
        this.renderChangePluginEndpoint(pluginEndpointName)
      )}
    `;
  }

  private renderCellSelectionBox() {
    if (!this.isLoggedIn) return;

    return html`
      <td class="cell selection">
        <!--
          The .checked property must be used rather than the attribute because
          the attribute only controls the default checked state and does not
          update the current checked state.
          See: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/checkbox#attr-checked
        -->
        <label class="selectionLabel">
          <input
            type="checkbox"
            .checked=${this.checked}
            @click=${this.toggleCheckbox}
          />
        </label>
      </td>
    `;
  }

  private renderCellStar() {
    if (!this.isLoggedIn) return;

    return html`
      <td class="cell star">
        <gr-change-star .change=${this.change}></gr-change-star>
      </td>
    `;
  }

  private renderCellNumber(changeUrl: string) {
    if (!this.showNumber) return;

    return html`
      <td class="cell number">
        <a href=${changeUrl}>${this.change?._number}</a>
      </td>
    `;
  }

  private renderCellSubject(changeUrl: string) {
    if (this.computeIsColumnHidden(ColumnNames.SUBJECT)) return;

    return html`
      <td class="cell subject">
        <a
          title=${ifDefined(this.change?.subject)}
          href=${changeUrl}
          @click=${this.handleChangeClick}
        >
          <div class="container">
            <div
              class=${classMap({
                content: true,
                strikethrough: this.change?.status === ChangeStatus.ABANDONED,
              })}
            >
              ${this.change?.subject}
            </div>
            <div class="spacer">${this.change?.subject}</div>
            <span>&nbsp;</span>
          </div>
        </a>
      </td>
    `;
  }

  private renderCellOwner() {
    if (this.computeIsColumnHidden(ColumnNames.OWNER)) return;

    return html`
      <td class="cell owner">
        <gr-account-label
          highlightAttention
          clickable
          .change=${this.change}
          .account=${this.change?.owner}
        ></gr-account-label>
      </td>
    `;
  }

  private renderCellReviewers() {
    if (this.computeIsColumnHidden(ColumnNames.REVIEWERS)) return;

    return html`
      <td class="cell reviewers">
        <div>
          ${this.computePrimaryReviewers().map((reviewer, index) =>
            this.renderChangeReviewers(reviewer, index)
          )}
          ${this.computeAdditionalReviewersCount()
            ? html`<span title=${this.computeAdditionalReviewersTitle()}
                >+${this.computeAdditionalReviewersCount()}</span
              >`
            : ''}
        </div>
      </td>
    `;
  }

  private renderChangeReviewers(reviewer: AccountInfo, index: number) {
    return html`
      <gr-account-label
        clickable
        hideAvatar
        firstName
        highlightAttention
        .change=${this.change}
        .account=${reviewer}
      ></gr-account-label
      ><span ?hidden=${this.computeCommaHidden(index)} aria-hidden="true"
        >,
      </span>
    `;
  }

  private renderCellRepo() {
    if (this.computeIsColumnHidden(ColumnNames.REPO)) {
      return;
    }

    const repo = this.change?.project ?? '';
    return html`
      <td class="cell repo">
        <a class="fullRepo" href=${this.computeRepoUrl()}> ${repo} </a>
        <a class="truncatedRepo" href=${this.computeRepoUrl()} title=${repo}>
          ${truncatePath(repo, 2)}
        </a>
      </td>
    `;
  }

  private renderCellBranch() {
    if (this.computeIsColumnHidden(ColumnNames.BRANCH)) return;

    return html`
      <td class="cell branch">
        <a href=${this.computeRepoBranchURL()}> ${this.change?.branch} </a>
        ${this.renderChangeBranch()}
      </td>
    `;
  }

  private renderChangeBranch() {
    if (!this.change?.topic) return;

    return html`
      (<a href=${this.computeTopicURL()}
        ><!--
      --><gr-limited-text .limit=${50} .text=${this.change.topic}>
        </gr-limited-text
        ><!--
    --></a
      >)
    `;
  }

  private renderCellUpdated() {
    if (this.computeIsColumnHidden(ColumnNames.UPDATED)) return;

    return html`
      <td class="cell updated">
        <gr-date-formatter
          withTooltip
          .dateStr=${this.formatDate(this.change?.updated)}
        ></gr-date-formatter>
      </td>
    `;
  }

  private renderCellSubmitted() {
    if (this.computeIsColumnHidden('Submitted')) return;

    return html`
      <td class="cell submitted">
        <gr-date-formatter
          withTooltip
          .dateStr=${this.formatDate(this.change?.submitted)}
        ></gr-date-formatter>
      </td>
    `;
  }

  private renderCellWaiting() {
    if (this.computeIsColumnHidden(WAITING)) return;

    return html`
      <td class="cell waiting">
        <gr-date-formatter
          withTooltip
          forceRelative
          relativeOptionNoAgo
          .dateStr=${this.computeWaiting()}
        ></gr-date-formatter>
      </td>
    `;
  }

  private renderCellSize() {
    if (this.computeIsColumnHidden(ColumnNames.SIZE)) return;

    return html`
      <td class="cell size">
        <gr-tooltip-content has-tooltip title=${this.computeSizeTooltip()}>
          ${this.renderChangeSize()}
        </gr-tooltip-content>
      </td>
    `;
  }

  private renderChangeSize() {
    const changeSize = this.computeChangeSize();
    if (!changeSize) return html`<span class="placeholder">--</span>`;

    return html`
      <span class="size-${changeSize.toLowerCase()}">${changeSize}</span>
    `;
  }

  private renderCellRequirements() {
    if (this.computeIsColumnHidden(ColumnNames.STATUS)) return;

    return html`
      <td class="cell status requirements">
        <gr-change-list-column-requirements-summary .change=${this.change}>
        </gr-change-list-column-requirements-summary>
      </td>
    `;
  }

  private renderChangeLabels(labelName: string) {
    return html` <td class="cell label requirement">
      <gr-change-list-column-requirement
        .change=${this.change}
        .labelName=${labelName}
      >
      </gr-change-list-column-requirement>
    </td>`;
  }

  private renderChangePluginEndpoint(pluginEndpointName: string) {
    return html`
      <td class="cell endpoint">
        <gr-endpoint-decorator name=${pluginEndpointName}>
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </td>
    `;
  }

  private readonly onItemClick = (e: Event) => {
    // Check the path to verify that the item row itself was directly clicked.
    // This will allow users using screen readers like VoiceOver to select an
    // item with j/k and go to the selected change with Ctrl+Option+Space, but
    // not interfere with clicks on interactive elements within the
    // gr-change-list-item such as account links, which will bubble through
    // without triggering this extra navigation.
    if (this.change && e.composedPath()[0] === this) {
      this.getNavigation().setUrl(createChangeUrl({change: this.change}));
    }
  };

  private computeChangeURL() {
    if (!this.change) return '';
    return createChangeUrl({change: this.change, usp: this.usp});
  }

  private computeRepoUrl() {
    if (!this.change) return '';
    return createSearchUrl({repo: this.change.project, statuses: ['open']});
  }

  private computeRepoBranchURL() {
    if (!this.change) return '';
    return createSearchUrl({
      branch: this.change.branch,
      repo: this.change.project,
    });
  }

  private computeTopicURL() {
    if (!this.change?.topic) return '';
    return createSearchUrl({topic: this.change.topic});
  }

  private toggleCheckbox() {
    assertIsDefined(this.change, 'change');
    this.checked = !this.checked;
    this.triggerSelectionCallback?.(this.globalIndex);
    this.getBulkActionsModel().toggleSelectedChangeNum(this.change._number);
  }

  // private but used in test
  computeSizeTooltip() {
    if (
      !this.change ||
      this.change.insertions + this.change.deletions === 0 ||
      isNaN(this.change.insertions + this.change.deletions)
    ) {
      return 'Size unknown';
    } else {
      return `added ${this.change.insertions}, removed ${this.change.deletions} lines`;
    }
  }

  private hasAttention(account: AccountInfo) {
    if (!this.change || !this.change.attention_set || !account._account_id) {
      return false;
    }
    return hasOwnProperty(this.change.attention_set, account._account_id);
  }

  /**
   * Computes the array of all reviewers with sorting the reviewers in the
   * attention set before others, and the current user first.
   *
   * private but used in test
   */
  computeReviewers() {
    if (!this.change?.reviewers || !this.change?.reviewers.REVIEWER) return [];
    const reviewers = [...this.change.reviewers.REVIEWER].filter(
      r =>
        (!this.change?.owner ||
          this.change?.owner._account_id !== r._account_id) &&
        !isServiceUser(r)
    );
    reviewers.sort((r1, r2) => {
      if (this.loggedInUser) {
        if (isSelf(r1, this.loggedInUser)) return -1;
        if (isSelf(r2, this.loggedInUser)) return 1;
      }
      if (this.hasAttention(r1) && !this.hasAttention(r2)) return -1;
      if (this.hasAttention(r2) && !this.hasAttention(r1)) return 1;
      return (r1.name || '').localeCompare(r2.name || '');
    });
    return reviewers;
  }

  private computePrimaryReviewers() {
    return this.computeReviewers().slice(0, PRIMARY_REVIEWERS_COUNT);
  }

  private computeAdditionalReviewers() {
    return this.computeReviewers().slice(PRIMARY_REVIEWERS_COUNT);
  }

  private computeAdditionalReviewersCount() {
    return this.computeAdditionalReviewers().length;
  }

  private computeAdditionalReviewersTitle() {
    if (!this.change || !this.config) return '';
    return this.computeAdditionalReviewers()
      .map(user => getDisplayName(this.config, user, true))
      .join(', ');
  }

  /**
   * TShirt sizing is based on the following paper:
   * http://dirkriehle.com/wp-content/uploads/2008/09/hicss-42-csdistr-final-web.pdf
   *
   * private but used in test
   */
  computeChangeSize() {
    if (!this.change) return null;
    const delta = this.change.insertions + this.change.deletions;
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

  private computeWaiting(): Timestamp | undefined {
    // TODO: dashboardUser comes from DashboardViewState and can be an
    // Email Address. In this case the attention_set lookup will return
    // undefined.
    const userId =
      this.dashboardUser === 'self'
        ? this.loggedInUser?._account_id
        : this.dashboardUser;
    if (!userId || !this.change?.attention_set) return undefined;
    return this.change?.attention_set[userId]?.last_update;
  }

  private computeIsColumnHidden(columnToCheck?: string) {
    if (!columnToCheck) return false;
    const columnsToDisplay = this.visibleChangeTableColumns ?? [];
    return (
      columnsToDisplay.length > 0 && !columnsToDisplay.includes(columnToCheck)
    );
  }

  private formatDate(date: Timestamp | undefined): string | undefined {
    if (!date) return undefined;
    return date.toString();
  }

  private handleChangeClick() {
    // Don't prevent the default and neither stop bubbling. We just want to
    // report the click, but then let the browser handle the click on the link.

    const selfId = (this.loggedInUser && this.loggedInUser._account_id) || -1;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;

    this.reporting.reportInteraction('change-row-clicked', {
      section: this.sectionName,
      isOwner: selfId === ownerId,
    });
  }

  private computeCommaHidden(index: number) {
    const additionalCount = this.computeAdditionalReviewersCount();
    const primaryCount = this.computePrimaryReviewers().length;
    const isLast = index === primaryCount - 1;
    return isLast && additionalCount === 0;
  }
}
