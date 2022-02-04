/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeListSection} from '../gr-change-list/gr-change-list';
import {
  CLOSED,
  YOUR_TURN,
  GerritNav,
  WAITING,
} from '../../core/gr-navigation/gr-navigation';
import {html} from 'lit-html/static';
import {KnownExperimentId} from '../../../services/flags/flags';
import {getAppContext} from '../../../services/app-context';
import {ChangeInfo, ServerInfo, AccountInfo} from '../../../api/rest-api';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css} from '@lit/reactive-element';
import {Metadata} from '../../../utils/change-metadata-util';

const NUMBER_FIXED_COLUMNS = 3;
const LABEL_PREFIX_INVALID_PROLOG = 'Invalid-Prolog-Rules-Label-Name--';
const MAX_SHORTCUT_CHARS = 5;
const INVALID_TOKENS = ['limit:', 'age:', '-age:'];

@customElement('gr-change-list-section')
export class GrChangeListSection extends LitElement {
  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Boolean})
  showStar = false;

  @property({type: Boolean})
  showNumber?: boolean; // No default value to prevent flickering.

  @property({type: Number})
  selectedIndex?: number;

  @property({type: Number})
  sectionIndex?: number;

  @property({type: Array})
  labelNames: string[] = [];

  @property({type: Array})
  dynamicHeaderEndpoints?: string[];

  /**
   * ChangeInfo objects grouped into arrays. The sections and changes
   * properties should not be used together.
   */
  @property({type: Array})
  sections: ChangeListSection[] = [];

  @property({type: Object})
  changeSection?: ChangeListSection;

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

  private readonly flagsService = getAppContext().flagsService;

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
      `,
    ];
  }

  override render() {
    const {changeSection, labelNames, sectionIndex} = this;
    if (!changeSection || sectionIndex === undefined) return;
    return html`
      ${this.renderSectionHeader(changeSection, labelNames)}
      <tbody class="groupContent">
        ${this.isEmpty(changeSection)
          ? this.renderNoChangesRow(changeSection, labelNames)
          : this.renderColumnHeaders(changeSection, labelNames)}
        ${changeSection.results.map((change, index) =>
          this.renderChangeRow(
            changeSection,
            change,
            index,
            sectionIndex,
            labelNames
          )
        )}
      </tbody>
    `;
  }

  private renderNoChangesRow(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    return html`
      <tr class="noChanges">
        <td class="leftPadding" aria-hidden="true"></td>
        <td
          class="star"
          ?aria-hidden=${!this.showStar}
          ?hidden=${!this.showStar}
        ></td>
        <td
          class="cell"
          colspan="${this.computeColspan(changeSection, labelNames)}"
        >
          ${this.getSpecialEmptySlot(changeSection)
            ? html`<slot
                name="${this.getSpecialEmptySlot(changeSection)}"
              ></slot>`
            : 'No changes'}
        </td>
      </tr>
    `;
  }

  private renderSectionHeader(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    if (!changeSection.name) return;

    return html`
      <tbody>
        <tr class="groupHeader">
          <td aria-hidden="true" class="leftPadding"></td>
          ${this.renderSelectionHeader()}
          <td aria-hidden="true" class="star" ?hidden=${!this.showStar}></td>
          <td
            class="cell"
            colspan="${this.computeColspan(changeSection, labelNames)}"
          >
            <h2 class="heading-3">
              <a
                href="${this.sectionHref(changeSection.query)}"
                class="section-title"
              >
                <span class="section-name">${changeSection.name}</span>
                <span class="section-count-label"
                  >${changeSection.countLabel}</span
                >
              </a>
            </h2>
          </td>
        </tr>
      </tbody>
    `;
  }

  private renderColumnHeaders(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    return html`
      <tr class="groupTitle">
        <td class="leftPadding" aria-hidden="true"></td>
        ${this.renderSelectionHeader()}
        <td
          class="star"
          aria-label="Star status column"
          ?hidden=${!this.showStar}
        ></td>
        <td class="number" ?hidden=${!this.showNumber}>#</td>
        ${this.computeColumns(changeSection).map(item =>
          this.renderHeaderCell(item)
        )}
        ${labelNames?.map(labelName => this.renderLabelHeader(labelName))}
        ${this.dynamicHeaderEndpoints?.map(pluginHeader =>
          this.renderEndpointHeader(pluginHeader)
        )}
      </tr>
    `;
  }

  private renderSelectionHeader() {
    if (!this.flagsService.isEnabled(KnownExperimentId.BULK_ACTIONS)) return;
    return html`<td aria-hidden="true" class="selection"></td>`;
  }

  private renderHeaderCell(item: string) {
    return html`<td class="${item.toLowerCase()}">${item}</td>`;
  }

  private renderLabelHeader(labelName: string) {
    return html`
      <td class="label" title="${labelName}">
        ${this.computeLabelShortcut(labelName)}
      </td>
    `;
  }

  private renderEndpointHeader(pluginHeader: string) {
    return html`
      <td class="endpoint">
        <gr-endpoint-decorator .name="${pluginHeader}"></gr-endpoint-decorator>
      </td>
    `;
  }

  private renderChangeRow(
    changeSection: ChangeListSection,
    change: ChangeInfo,
    index: number,
    sectionIndex: number,
    labelNames: string[]
  ) {
    const ariaLabel = this.computeAriaLabel(change, changeSection.name);
    const selected = this.computeItemSelected(
      sectionIndex,
      index,
      this.selectedIndex
    );
    const tabindex = this.computeTabIndex(
      sectionIndex,
      index,
      this.isCursorMoving,
      this.selectedIndex
    );
    const visibleChangeTableColumns = this.computeColumns(changeSection);
    return html`
      <gr-change-list-item
        .account=${this.account}
        ?selected=${selected}
        .change=${change}
        .config=${this.config}
        .sectionName=${changeSection.name}
        .visibleChangeTableColumns=${visibleChangeTableColumns}
        .showNumber=${this.showNumber}
        .showStar=${this.showStar}
        ?tabindex=${tabindex}
        .labelNames=${labelNames}
        aria-label=${ariaLabel}
      ></gr-change-list-item>
    `;
  }

  /**
   * This methods allows us to customize the columns per section.
   *
   * @param visibleColumns are the columns according to configs and user prefs
   */
  private computeColumns(section?: ChangeListSection): string[] {
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

  // private but used in test
  getSpecialEmptySlot(section: ChangeListSection) {
    if (section.isOutgoing) return 'empty-outgoing';
    if (section.name === YOUR_TURN.name) return 'empty-your-turn';
    return '';
  }

  /**
   * Maps an index local to a particular section to the absolute index
   * across all the changes on the page.
   *
   * private but used in test
   *
   * @param sectionIndex index of section
   * @param localIndex index of row within section
   * @return absolute index of row in the aggregate dashboard
   */
  computeItemAbsoluteIndex(sectionIndex: number, localIndex: number) {
    let idx = 0;
    for (let i = 0; i < sectionIndex; i++) {
      idx += this.sections[i].results.length;
    }
    return idx + localIndex;
  }

  private computeItemSelected(
    sectionIndex: number,
    index: number,
    selectedIndex?: number
  ) {
    const idx = this.computeItemAbsoluteIndex(sectionIndex, index);
    return idx === selectedIndex;
  }

  private computeTabIndex(
    sectionIndex: number,
    index: number,
    isCursorMoving: boolean,
    selectedIndex?: number
  ) {
    if (isCursorMoving) return 0;
    return this.computeItemSelected(sectionIndex, index, selectedIndex)
      ? 0
      : undefined;
  }

  // private but used in test
  computeColspan(section?: ChangeListSection, labelNames?: string[]) {
    const cols = this.computeColumns(section);
    if (!cols || !labelNames) return 1;
    return cols.length + labelNames.length + NUMBER_FIXED_COLUMNS;
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
  computeLabelShortcut(labelName: string) {
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

  // private but used in test
  isEmpty(section: ChangeListSection) {
    return !section.results?.length;
  }

  private computeAriaLabel(change?: ChangeInfo, sectionName?: string) {
    if (!change) return '';
    return change.subject + (sectionName ? `, section: ${sectionName}` : '');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-section': GrChangeListSection;
  }
}
