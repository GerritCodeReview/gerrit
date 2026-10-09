/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import '../gr-change-list-action-bar/gr-change-list-action-bar';
import {CLOSED, YOUR_TURN} from '../../../utils/dashboard-util';
import {getAppContext} from '../../../services/app-context';
import {AccountInfo, ChangeInfo} from '../../../api/rest-api';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {Metadata} from '../../../utils/change-metadata-util';
import {WAITING} from '../../../constants/constants';
import {provide, resolve} from '../../../models/dependency';
import {
  BulkActionsModel,
  bulkActionsModelToken,
  LoadingState,
} from '../../../models/bulk-actions/bulk-actions-model';
import {createSearchUrl} from '../../../models/views/search';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';
import {classMap} from 'lit/directives/class-map.js';
import {formStyles} from '../../../styles/form-styles';
import {UserId} from '../../../types/common';
import '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import {when} from 'lit/directives/when.js';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {
  computeLabelShortcut,
  getChangeListLabels,
} from '../../../utils/label-util';
export {computeLabelShortcut} from '../../../utils/label-util';

const NUMBER_FIXED_COLUMNS = 4;
const MAX_NARROW_LABELS = 5;
const INVALID_TOKENS = ['limit:', 'age:', '-age:'];

@customElement('gr-change-list-section')
export class GrChangeListSection extends LitElement {
  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Boolean})
  showNumber?: boolean; // No default value to prevent flickering.

  @property({type: Number})
  selectedIndex?: number; // The relative index of the change that is selected

  @property({type: Array})
  labelNames: string[] = [];

  @property({type: Array})
  dynamicHeaderEndpoints?: string[];

  @property({type: Object})
  changeSection!: ChangeListSection;

  @property({type: Boolean})
  isCursorMoving = false;

  @property({type: Boolean})
  starsLoading = false;

  /**
   * The logged-in user's account, or an empty object if no user is logged
   * in.
   */
  @property({type: Object})
  loggedInUser?: AccountInfo;

  /**
   * When the list is part of the dashboard, the user for which the dashboard is
   * generated.
   */
  @property({type: String})
  dashboardUser?: UserId | 'self';

  @property({type: String})
  usp?: string;

  /** Index of the first element in the section in the overall list order. */
  @property({type: Number})
  startIndex = 0;

  /** Callback to call to request the item to be selected in the list. */
  @property({type: Function})
  triggerSelectionCallback?: (globalIndex: number) => void;

  // private but used in tests
  @state()
  numSelected = 0;

  @state()
  private totalChangeCount = 0;

  // Private but used in test
  @state()
  bulkActionsLoaded = false;

  bulkActionsModel: BulkActionsModel = new BulkActionsModel(
    getAppContext().restApiService
  );

  private readonly getUserModel = resolve(this, userModelToken);

  private isLoggedIn = false;

  @state()
  private narrowScreen = window.matchMedia('(max-width: 50em)').matches;

  private labelResizeObserver?: ResizeObserver;

  private labelResizeFrame = 0;

  override connectedCallback() {
    super.connectedCallback();
    this.updateComplete.then(() => {
      if (!this.isConnected) return;
      const list = (this.getRootNode() as ShadowRoot).host;
      if (!(list instanceof HTMLElement)) return;
      this.labelResizeObserver = new ResizeObserver(() => {
        const narrowScreen = window.matchMedia('(max-width: 50em)').matches;
        if (narrowScreen === this.narrowScreen) return;
        cancelAnimationFrame(this.labelResizeFrame);
        this.labelResizeFrame = requestAnimationFrame(() => {
          this.narrowScreen = narrowScreen;
        });
      });
      this.labelResizeObserver.observe(list);
    });
  }

  override disconnectedCallback() {
    this.labelResizeObserver?.disconnect();
    cancelAnimationFrame(this.labelResizeFrame);
    super.disconnectedCallback();
  }

  private get narrowLabels() {
    return this.getNarrowLabels();
  }

  private getNarrowLabels(change?: ChangeInfo) {
    if (!this.narrowScreen) return [...(this.labelNames ?? [])];
    const applicableLabels = change ? getChangeListLabels(change) : undefined;
    const labels = (this.labelNames ?? []).filter(
      name => !applicableLabels || applicableLabels.includes(name)
    );
    const priority = ['Code-Review', 'Verified'];
    labels.sort((a, b) => {
      const rank = (name: string) => {
        const index = priority.indexOf(name);
        return index < 0 ? priority.length : index;
      };
      return rank(a) - rank(b);
    });
    return labels.slice(0, MAX_NARROW_LABELS);
  }

  static override get styles() {
    return [
      sharedStyles,
      changeListStyles,
      fontStyles,
      formStyles,
      materialStyles,
      spinnerStyles,
      css`
        :host {
          display: contents;
        }
        .section-count-label {
          color: var(--deemphasized-text-color);
          font-family: var(--font-family);
          font-size: var(--font-size-small);
          font-weight: var(--font-weight-normal);
          line-height: var(--line-height-small);
        }
        /*
         * checkbox styles match checkboxes in <gr-change-list-item> rows to
         * vertically align with them.
         */
        input.selection-checkbox {
          background-color: var(--background-color-primary);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          box-sizing: border-box;
          color: var(--primary-text-color);
          margin: 0px;
          padding: var(--spacing-s);
          vertical-align: middle;
        }
        .showSelectionBorder {
          border-bottom: 2px solid var(--input-focus-border-color);
        }
        md-checkbox {
          --md-checkbox-container-size: 15px;
          --md-checkbox-icon-size: 15px;
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .loadingSpin {
          width: 15px;
          height: 15px;
          display: inline-block;
          vertical-align: middle;
        }
        .selection:has(.loadingSpin):not(:has(md-checkbox)) {
          padding-right: 4px !important;
        }
        .labelOverflow {
          display: none;
        }
        @media only screen and (max-width: 50em) {
          .groupTitle.narrowVotes {
            display: none;
          }
        }
      `,
    ];
  }

  constructor() {
    super();
    provide(this, bulkActionsModelToken, () => this.bulkActionsModel);
    subscribe(
      this,
      () => this.bulkActionsModel.selectedChangeNums$,
      selectedChanges => {
        this.numSelected = selectedChanges.length;
      }
    );
    subscribe(
      this,
      () => this.bulkActionsModel.loadingState$,
      loadingState => {
        this.bulkActionsLoaded = loadingState === LoadingState.LOADED;
      }
    );
    subscribe(
      this,
      () => this.bulkActionsModel.totalChangeCount$,
      totalChangeCount => (this.totalChangeCount = totalChangeCount)
    );
    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      isLoggedIn => (this.isLoggedIn = isLoggedIn)
    );
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('changeSection') && this.isLoggedIn) {
      // In case the list of changes is updated due to auto reloading, we want
      // to ensure the model removes any stale change that is not a part of the
      // new section changes.
      this.bulkActionsModel.sync(this.changeSection.results);
    }
  }

  override render() {
    const columns = this.computeColumns();
    const colSpan = this.computeColspan(columns);
    return html`
      <tbody>
        <tr class="groupGap"></tr>
      </tbody>
      ${this.renderSectionHeader(colSpan)}
      <tbody class="groupContent">
        ${this.isEmpty()
          ? this.renderNoChangesRow(colSpan)
          : this.renderColumnHeaders(columns)}
        ${this.changeSection.results.map((change, index) =>
          this.renderChangeRow(change, index, columns)
        )}
      </tbody>
    `;
  }

  private renderNoChangesRow(colSpan: number) {
    return html`
      <tr class="noChanges">
        <td class="leftPadding" aria-hidden="true"></td>
        <td
          class="star"
          aria-hidden=${!this.isLoggedIn}
          ?hidden=${!this.isLoggedIn}
        ></td>
        <td class="cell" colspan=${colSpan}>
          ${this.changeSection.emptyStateSlotName
            ? html`<slot name=${this.changeSection.emptyStateSlotName}></slot>`
            : 'No changes'}
        </td>
      </tr>
    `;
  }

  private renderSectionHeader(colSpan: number) {
    if (
      this.changeSection.name === undefined ||
      this.changeSection.countLabel === undefined ||
      this.changeSection.query === undefined
    )
      return;

    return html`
      <tbody>
        <tr class="groupHeader">
          <td aria-hidden="true" class="leftPadding"></td>
          <td aria-hidden="true" class="star" ?hidden=${!this.isLoggedIn}></td>
          <td class="cell" colspan=${colSpan}>
            <h2 class="heading-2">
              <a
                href=${this.sectionHref(this.changeSection.query)}
                class="section-title"
              >
                <span class="section-name">${this.changeSection.name}</span>
                <span class="section-count-label"
                  >${this.changeSection.countLabel}</span
                >
              </a>
            </h2>
          </td>
        </tr>
      </tbody>
    `;
  }

  private renderColumnHeaders(columns: string[]) {
    const showBulkActionsHeader = this.numSelected > 0;
    return html`
      <tr
        class=${classMap({
          groupTitle: true,
          narrowVotes:
            !showBulkActionsHeader && (this.labelNames?.length ?? 0) > 0,
          showSelectionBorder: showBulkActionsHeader,
        })}
      >
        <td class="leftPadding"></td>
        ${this.renderSelectionHeader()}
        ${showBulkActionsHeader
          ? html`<gr-change-list-action-bar></gr-change-list-action-bar>`
          : html` <td
                class="star"
                aria-label="Star status column"
                ?hidden=${!this.isLoggedIn}
              ></td>
              <td class="number" ?hidden=${!this.showNumber}>#</td>
              ${columns.map(item => this.renderHeaderCell(item))}
              ${this.labelNames?.map(labelName =>
                this.renderLabelHeader(labelName)
              )}
              ${this.labelNames?.length > this.narrowLabels.length
                ? html`<td
                    class="label labelOverflow"
                    title=${this.labelNames
                      .filter(name => !this.narrowLabels.includes(name))
                      .join(', ')}
                    style="order: ${this.narrowLabels.length}"
                  >
                    …
                  </td>`
                : ''}
              ${this.dynamicHeaderEndpoints?.map(pluginHeader =>
                this.renderEndpointHeader(pluginHeader)
              )}`}
      </tr>
    `;
  }

  private renderSelectionHeader() {
    const checked = this.numSelected > 0;
    const indeterminate =
      this.numSelected > 0 && this.numSelected !== this.totalChangeCount;
    return html`
      <td class="selection" ?hidden=${!this.isLoggedIn}>
        ${when(
          this.bulkActionsLoaded,
          () => html`
            <md-checkbox
              class="selection-checkbox"
              ?checked=${checked}
              .indeterminate=${indeterminate}
              @change=${this.handleSelectAllCheckboxClicked}
            ></md-checkbox>
          `,
          () => html` <span
            class="loadingSpin"
            title="Bulk actions is loading"
          ></span>`
        )}
      </td>
    `;
  }

  private renderHeaderCell(item: string) {
    return html`<td class=${item.toLowerCase()}>${item}</td>`;
  }

  private renderLabelHeader(labelName: string) {
    return html`
      <td
        class="label ${this.narrowLabels.includes(labelName)
          ? ''
          : 'narrowHidden'}"
        title=${labelName}
        style="order: ${this.narrowLabels.indexOf(labelName)}"
      >
        ${computeLabelShortcut(labelName)}
      </td>
    `;
  }

  private renderEndpointHeader(pluginHeader: string) {
    return html`
      <td class="endpoint">
        <gr-endpoint-decorator .name=${pluginHeader}></gr-endpoint-decorator>
      </td>
    `;
  }

  private renderChangeRow(
    change: ChangeInfo,
    index: number,
    columns: string[]
  ) {
    const ariaLabel = this.computeAriaLabel(change);
    const selected = this.computeItemSelected(index);
    return html`
      <gr-change-list-item
        tabindex="0"
        .loggedInUser=${this.loggedInUser}
        .dashboardUser=${this.dashboardUser}
        .selected=${selected}
        .change=${change}
        .sectionName=${this.changeSection.name}
        .starsLoading=${this.starsLoading}
        .visibleChangeTableColumns=${columns}
        .showNumber=${!!this.showNumber}
        .usp=${this.usp}
        .labelNames=${this.labelNames}
        .narrowLabels=${this.getNarrowLabels(change)}
        .globalIndex=${this.startIndex + index}
        .triggerSelectionCallback=${this.triggerSelectionCallback}
        aria-label=${ariaLabel}
        role="button"
      ></gr-change-list-item>
    `;
  }

  private handleSelectAllCheckboxClicked() {
    if (this.numSelected === 0) {
      this.bulkActionsModel.selectAll();
    } else {
      this.bulkActionsModel.clearSelectedChangeNums();
    }
  }

  /**
   * This methods allows us to customize the columns per section.
   * Private but used in test
   *
   */
  computeColumns() {
    const section = this.changeSection;
    if (!section || !this.visibleChangeTableColumns) return [];
    const cols = [...this.visibleChangeTableColumns];
    const updatedIndex = cols.indexOf(Metadata.UPDATED);
    if (section.name === YOUR_TURN.name && updatedIndex !== -1) {
      cols[updatedIndex] = WAITING;
    }
    if (section.name === CLOSED.name && updatedIndex !== -1) {
      cols[updatedIndex] = Metadata.SUBMITTED;
    }
    return cols;
  }

  toggleChange(index: number) {
    this.bulkActionsModel.toggleSelectedChangeNum(
      this.changeSection.results[index]._number
    );
  }

  // private but used in test
  computeItemSelected(index: number) {
    return index === this.selectedIndex;
  }

  // private but used in test
  computeColspan(cols: string[]) {
    if (!cols || !this.labelNames) return 1;
    return cols.length + this.labelNames.length + NUMBER_FIXED_COLUMNS;
  }

  // private but used in test
  processQuery(query: string) {
    let tokens = query.split(' ');
    tokens = tokens.filter(
      token =>
        !INVALID_TOKENS.some(invalidToken => token.startsWith(invalidToken))
    );
    return tokens.join(' ');
  }

  private sectionHref(query?: string) {
    if (!query) return '';
    return createSearchUrl({query: this.processQuery(query)});
  }

  // private but used in test
  isEmpty() {
    return !this.changeSection.results?.length;
  }

  private computeAriaLabel(change?: ChangeInfo) {
    const sectionName = this.changeSection.name;
    if (!change) return '';
    return change.subject + (sectionName ? `, section: ${sectionName}` : '');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-section': GrChangeListSection;
  }
}
