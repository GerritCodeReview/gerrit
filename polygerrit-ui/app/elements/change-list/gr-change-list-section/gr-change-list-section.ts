/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, html, css, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import '../gr-change-list-action-bar/gr-change-list-action-bar';
import {
  CLOSED,
  YOUR_TURN,
  GerritNav,
} from '../../core/gr-navigation/gr-navigation';
import {KnownExperimentId} from '../../../services/flags/flags';
import {getAppContext} from '../../../services/app-context';
import {ChangeInfo, ServerInfo, AccountInfo} from '../../../api/rest-api';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {Metadata} from '../../../utils/change-metadata-util';
import {WAITING} from '../../../constants/constants';
import {provide} from '../../../models/dependency';
import {
  bulkActionsModelToken,
  BulkActionsModel,
} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import {queryAll} from '../../../utils/common-util';
import {classMap} from 'lit/directives/class-map';
import {AriaGridCurrentFocus, AriaGridModel} from '../AriaGridModel';

const NUMBER_FIXED_COLUMNS = 3;
const LABEL_PREFIX_INVALID_PROLOG = 'Invalid-Prolog-Rules-Label-Name--';
const MAX_SHORTCUT_CHARS = 5;
const INVALID_TOKENS = ['limit:', 'age:', '-age:'];

export function computeLabelShortcut(labelName: string) {
  if (labelName.startsWith(LABEL_PREFIX_INVALID_PROLOG)) {
    labelName = labelName.slice(LABEL_PREFIX_INVALID_PROLOG.length);
  }
  // Compute label shortcut by splitting token by - and capitalizing first
  // letter of each token.
  return labelName
    .split('-')
    .reduce((previousValue, currentValue) => {
      if (!currentValue) {
        return previousValue;
      }
      return previousValue + currentValue[0].toUpperCase();
    }, '')
    .slice(0, MAX_SHORTCUT_CHARS);
}

@customElement('gr-change-list-section')
export class GrChangeListSection extends LitElement {
  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Boolean})
  showStar = false;

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

  @property({type: Object})
  config?: ServerInfo;

  @property({type: Boolean})
  isCursorMoving = false;

  /**
   * The logged-in user's account, or an empty object if no user is logged
   * in.
   */
  @property({type: Object})
  account: AccountInfo | undefined = undefined;

  @property({type: String})
  usp?: string;

  // private but used in tests
  @state()
  numSelected = 0;

  @state()
  private totalChangeCount = 0;

  @state() private gridFocus?: AriaGridCurrentFocus;

  private readonly flagsService = getAppContext().flagsService;

  bulkActionsModel: BulkActionsModel = new BulkActionsModel(
    getAppContext().restApiService
  );

  private ariaGridModel = new AriaGridModel();

  static override get styles() {
    return [
      changeListStyles,
      fontStyles,
      sharedStyles,
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
      () => this.bulkActionsModel.totalChangeCount$,
      totalChangeCount => (this.totalChangeCount = totalChangeCount)
    );
    subscribe(
      this,
      () => this.ariaGridModel.state$,
      ariaGridState => (this.gridFocus = ariaGridState.currentFocus)
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.addEventListener('keydown', this.onKeydown);
  }

  override disconnectedCallback() {
    this.removeEventListener('keydown', this.onKeydown);
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('changeSection')) {
      // In case the list of changes is updated due to auto reloading, we want
      // to ensure the model removes any stale change that is not a part of the
      // new section changes.
      if (this.flagsService.isEnabled(KnownExperimentId.BULK_ACTIONS)) {
        this.bulkActionsModel.sync(this.changeSection.results);
      }
      this.ariaGridModel.setGridSize(
        this.changeSection.results.length,
        this.visibleChangeTableColumns?.length ?? 0
      );
    }
  }

  override render() {
    const columns = this.computeColumns();
    const colSpan = this.computeColspan(columns);
    return html`
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
          ?aria-hidden=${!this.showStar}
          ?hidden=${!this.showStar}
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
          <td aria-hidden="true" class="star" ?hidden=${!this.showStar}></td>
          <td class="cell" colspan=${colSpan}>
            <h2 class="heading-3">
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
    const showBulkActionsHeader =
      this.numSelected > 0 &&
      this.flagsService.isEnabled(KnownExperimentId.BULK_ACTIONS);
    return html`
      <tr
        class=${classMap({
          groupTitle: true,
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
                ?hidden=${!this.showStar}
              ></td>
              <td class="number" ?hidden=${!this.showNumber}>#</td>
              ${columns.map(item => this.renderHeaderCell(item))}
              ${this.labelNames?.map(labelName =>
                this.renderLabelHeader(labelName)
              )}
              ${this.dynamicHeaderEndpoints?.map(pluginHeader =>
                this.renderEndpointHeader(pluginHeader)
              )}`}
      </tr>
    `;
  }

  private renderSelectionHeader() {
    if (!this.flagsService.isEnabled(KnownExperimentId.BULK_ACTIONS)) return;
    const checked = this.numSelected > 0;
    const indeterminate =
      this.numSelected > 0 && this.numSelected !== this.totalChangeCount;
    return html`
      <td class="selection">
        <!--
          The .checked property must be used rather than the attribute because
          the attribute only controls the default checked state and does not
          update the current checked state.
          See: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/checkbox#attr-checked
        -->
        <input
          class="selection-checkbox"
          type="checkbox"
          .checked=${checked}
          .indeterminate=${indeterminate}
          @click=${this.handleSelectAllCheckboxClicked}
        />
      </td>
    `;
  }

  private renderHeaderCell(item: string) {
    return html`<td class=${item.toLowerCase()}>${item}</td>`;
  }

  private renderLabelHeader(labelName: string) {
    return html`
      <td class="label" title=${labelName}>
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
    const rowFocused = this.gridFocus?.row === index;
    const columnToFocus = rowFocused ? this.gridFocus?.column : undefined;
    return html`
      <gr-change-list-item
        tabindex="0"
        .account=${this.account}
        ?selected=${selected}
        .change=${change}
        .config=${this.config}
        .sectionName=${this.changeSection.name}
        .visibleChangeTableColumns=${columns}
        .showNumber=${this.showNumber}
        .columnToFocus=${columnToFocus}
        ?showStar=${this.showStar}
        .usp=${this.usp}
        .labelNames=${this.labelNames}
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
    const items = queryAll<GrChangeListItem>(this, 'gr-change-list-item');
    if (index >= items.length) throw new Error('invalid item index');
    items[index].toggleCheckbox();
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
    if (!query) return;
    return GerritNav.getUrlForSearchQuery(this.processQuery(query));
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

  private readonly onKeydown = (e: KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowLeft':
        this.ariaGridModel.moveLeft();
        break;
      case 'ArrowRight':
        this.ariaGridModel.moveRight();
        break;
      case 'ArrowUp':
        this.ariaGridModel.moveUp();
        break;
      case 'ArrowDown':
        this.ariaGridModel.moveDown();
        break;
    }
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-section': GrChangeListSection;
  }
}
