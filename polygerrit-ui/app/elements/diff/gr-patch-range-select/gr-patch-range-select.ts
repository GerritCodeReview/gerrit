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
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-select/gr-select';
import {pluralize} from '../../../utils/string-util';
import {appContext} from '../../../services/app-context';
import {
  computeLatestPatchNum,
  findSortedIndex,
  getParentIndex,
  getRevisionByPatchNum,
  isMergeParent,
  sortRevisions,
  PatchSet,
  convertToPatchSetNum,
} from '../../../utils/patch-set-util';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  BasePatchSetNum,
  ParentPatchSetNum,
  PatchSetNum,
  RevisionInfo,
  Timestamp,
} from '../../../types/common';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {
  DropdownItem,
  DropDownValueChangeEvent,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GeneratedWebLink} from '../../core/gr-navigation/gr-navigation';
import {EditRevisionInfo} from '../../../types/types';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;

export interface PatchRangeChangeDetail {
  patchNum?: PatchSetNum;
  basePatchNum?: BasePatchSetNum;
}

export type PatchRangeChangeEvent = CustomEvent<PatchRangeChangeDetail>;

export interface FilesWebLinks {
  meta_a: GeneratedWebLink[];
  meta_b: GeneratedWebLink[];
}

declare global {
  interface HTMLElementEventMap {
    'value-change': DropDownValueChangeEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-patch-range-select': GrPatchRangeSelect;
  }
}

/**
 * Fired when the patch range changes
 *
 * @event patch-range-change
 *
 * @property {string} patchNum
 * @property {string} basePatchNum
 */
@customElement('gr-patch-range-select')
export class GrPatchRangeSelect extends LitElement {
  @query('#patchNumDropdown')
  patchNumDropdown?: GrDropdownList;

  @property({type: Array})
  availablePatches?: PatchSet[];

  @property({type: String})
  changeNum?: string;

  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Object})
  filesWeblinks?: FilesWebLinks;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  basePatchNum?: BasePatchSetNum;

  @property({type: Object})
  revisions?: RevisionInfo[];

  @property({type: Object})
  revisionInfo?: RevisionInfoClass;

  @property({type: Array})
  _sortedRevisions?: RevisionInfo[];

  private readonly reporting: ReportingService = appContext.reportingService;

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  static override get styles() {
    return [
      a11yStyles,
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: flex;
        }
        select {
          max-width: 15em;
        }
        .arrow {
          color: var(--deemphasized-text-color);
          margin: 0 var(--spacing-m);
        }
        gr-dropdown-list {
          --trigger-style-text-color: var(--deemphasized-text-color);
          --trigger-style-font-family: var(--font-family);
        }
        @media screen and (max-width: 50em) {
          .filesWeblinks {
            display: none;
          }
          gr-dropdown-list {
            --native-select-style: {
              max-width: 5.25em;
            }
          }
        }
      `,
    ];
  }

  override render() {
    return html`
      <h3 class="assistive-tech-only">Patchset Range Selection</h3>
      <span class="patchRange" aria-label="patch range starts with">
        <gr-dropdown-list
          id="basePatchDropdown"
          .value="${this._convertToString(this.basePatchNum)}"
          .items="${this._baseDropdownContent}"
          @value-change=${this._handlePatchChange}
        >
        </gr-dropdown-list>
      </span>
      ${this.filesWeblinks?.meta_a
        ? html`<span class="filesWeblinks">
            ${this.filesWeblinks?.meta_a.map(
              weblink => html`
                <a target="_blank" rel="noopener" href="${weblink.url}">
                  ${weblink.name}
                </a>
              `
            )}</span
          >`
        : ''}
      <span aria-hidden="true" class="arrow">→</span>
      <span class="patchRange" aria-label="patch range ends with">
        <gr-dropdown-list
          id="patchNumDropdown"
          .value="${this._convertToString(this.patchNum)}"
          .items="${this._patchDropdownContent}"
          @value-change=${this._handlePatchChange}
        >
        </gr-dropdown-list>
        ${this.filesWeblinks?.meta_b
          ? html`<span class="filesWeblinks">
              ${this.filesWeblinks?.meta_b.map(
                weblink => html`
                  <a target="_blank" rel="noopener" href="${weblink.url}">
                    ${weblink.name}
                  </a>
                `
              )}</span
            >`
          : ''}
      </span>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('revisions')) {
      this._updateSortedRevisions(this.revisions);
    }
  }

  _updateSortedRevisions(revisions?: RevisionInfo[]) {
    if (!revisions) return;
    this._sortedRevisions = sortRevisions(Object.values(revisions));
  }

  get _baseDropdownContent(): DropdownItem[] {
    return this._computeBaseDropdownContent(
      this.availablePatches,
      this.patchNum,
      this._sortedRevisions,
      this.changeComments,
      this.revisionInfo
    );
  }

  get _patchDropdownContent(): DropdownItem[] {
    return this._computePatchDropdownContent(
      this.availablePatches,
      this.basePatchNum,
      this._sortedRevisions,
      this.changeComments
    );
  }

  _getShaForPatch(patch: PatchSet) {
    return patch.sha.substring(0, 10);
  }

  _computeBaseDropdownContent(
    availablePatches?: PatchSet[],
    patchNum?: PatchSetNum,
    _sortedRevisions?: (RevisionInfo | EditRevisionInfo)[],
    changeComments?: ChangeComments,
    revisionInfo?: RevisionInfoClass
  ): DropdownItem[] {
    // Polymer 2: check for undefined
    if (
      availablePatches === undefined ||
      patchNum === undefined ||
      _sortedRevisions === undefined ||
      changeComments === undefined ||
      revisionInfo === undefined
    ) {
      return [];
    }

    const parentCounts = revisionInfo.getParentCountMap();
    const currentParentCount = hasOwnProperty(parentCounts, patchNum)
      ? parentCounts[patchNum as number]
      : 1;
    const maxParents = revisionInfo.getMaxParents();
    const isMerge = currentParentCount > 1;

    const dropdownContent: DropdownItem[] = [];
    for (const basePatch of availablePatches) {
      const basePatchNum = basePatch.num;
      const entry: DropdownItem = this._createDropdownEntry(
        basePatchNum,
        'Patchset ',
        _sortedRevisions,
        changeComments,
        this._getShaForPatch(basePatch)
      );
      dropdownContent.push({
        ...entry,
        disabled: this._computeLeftDisabled(
          basePatch.num,
          patchNum,
          _sortedRevisions
        ),
      });
    }

    dropdownContent.push({
      text: isMerge ? 'Auto Merge' : 'Base',
      value: 'PARENT',
    });

    for (let idx = 0; isMerge && idx < maxParents; idx++) {
      dropdownContent.push({
        disabled: idx >= currentParentCount,
        triggerText: `Parent ${idx + 1}`,
        text: `Parent ${idx + 1}`,
        mobileText: `Parent ${idx + 1}`,
        value: -(idx + 1),
      });
    }

    return dropdownContent;
  }

  _computeMobileText(
    patchNum: PatchSetNum,
    changeComments: ChangeComments,
    revisions: (RevisionInfo | EditRevisionInfo)[]
  ) {
    return (
      `${patchNum}` +
      `${this._computePatchSetCommentsString(changeComments, patchNum)}` +
      `${this._computePatchSetDescription(revisions, patchNum, true)}`
    );
  }

  _computePatchDropdownContent(
    availablePatches?: PatchSet[],
    basePatchNum?: BasePatchSetNum,
    _sortedRevisions?: (RevisionInfo | EditRevisionInfo)[],
    changeComments?: ChangeComments
  ): DropdownItem[] {
    // Polymer 2: check for undefined
    if (
      availablePatches === undefined ||
      basePatchNum === undefined ||
      _sortedRevisions === undefined ||
      changeComments === undefined
    ) {
      return [];
    }

    const dropdownContent: DropdownItem[] = [];
    for (const patch of availablePatches) {
      const patchNum = patch.num;
      const entry = this._createDropdownEntry(
        patchNum,
        patchNum === 'edit' ? '' : 'Patchset ',
        _sortedRevisions,
        changeComments,
        this._getShaForPatch(patch)
      );
      dropdownContent.push({
        ...entry,
        disabled: this._computeRightDisabled(
          basePatchNum,
          patchNum,
          _sortedRevisions
        ),
      });
    }
    return dropdownContent;
  }

  _computeText(
    patchNum: PatchSetNum,
    prefix: string,
    changeComments: ChangeComments,
    sha: string
  ) {
    return (
      `${prefix}${patchNum}` +
      `${this._computePatchSetCommentsString(changeComments, patchNum)}` +
      ` | ${sha}`
    );
  }

  _createDropdownEntry(
    patchNum: PatchSetNum,
    prefix: string,
    sortedRevisions: (RevisionInfo | EditRevisionInfo)[],
    changeComments: ChangeComments,
    sha: string
  ) {
    const entry: DropdownItem = {
      triggerText: `${prefix}${patchNum}`,
      text: this._computeText(patchNum, prefix, changeComments, sha),
      mobileText: this._computeMobileText(
        patchNum,
        changeComments,
        sortedRevisions
      ),
      bottomText: `${this._computePatchSetDescription(
        sortedRevisions,
        patchNum
      )}`,
      value: patchNum,
    };
    const date = this._computePatchSetDate(sortedRevisions, patchNum);
    if (date) {
      entry.date = date;
    }
    return entry;
  }

  /**
   * The basePatchNum should always be <= patchNum -- because sortedRevisions
   * is sorted in reverse order (higher patchset nums first), invalid base
   * patch nums have an index greater than the index of patchNum.
   *
   * @param basePatchNum The possible base patch num.
   * @param patchNum The current selected patch num.
   */
  _computeLeftDisabled(
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    sortedRevisions: (RevisionInfo | EditRevisionInfo)[]
  ): boolean {
    return (
      findSortedIndex(basePatchNum, sortedRevisions) <=
      findSortedIndex(patchNum, sortedRevisions)
    );
  }

  /**
   * The basePatchNum should always be <= patchNum -- because sortedRevisions
   * is sorted in reverse order (higher patchset nums first), invalid patch
   * nums have an index greater than the index of basePatchNum.
   *
   * In addition, if the current basePatchNum is 'PARENT', all patchNums are
   * valid.
   *
   * If the current basePatchNum is a parent index, then only patches that have
   * at least that many parents are valid.
   *
   * @param basePatchNum The current selected base patch num.
   * @param patchNum The possible patch num.
   */
  _computeRightDisabled(
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum,
    sortedRevisions: (RevisionInfo | EditRevisionInfo)[]
  ): boolean {
    if (basePatchNum === ParentPatchSetNum) {
      return false;
    }

    if (isMergeParent(basePatchNum)) {
      if (!this.revisionInfo) {
        return true;
      }
      // Note: parent indices use 1-offset.
      return (
        this.revisionInfo.getParentCount(patchNum) <
        getParentIndex(basePatchNum)
      );
    }

    return (
      findSortedIndex(basePatchNum, sortedRevisions) <=
      findSortedIndex(patchNum, sortedRevisions)
    );
  }

  // TODO(dhruvsri): have ported comments contribute to this count
  _computePatchSetCommentsString(
    changeComments: ChangeComments,
    patchNum: PatchSetNum
  ) {
    if (!changeComments) {
      return;
    }

    const commentThreadCount = changeComments.computeCommentThreadCount(
      {
        patchNum,
      },
      true
    );
    const commentThreadString = pluralize(commentThreadCount, 'comment');

    const unresolvedCount = changeComments.computeUnresolvedNum({patchNum});
    const unresolvedString =
      unresolvedCount === 0 ? '' : `${unresolvedCount} unresolved`;

    if (!commentThreadString.length && !unresolvedString.length) {
      return '';
    }

    return (
      ` (${commentThreadString}` +
      // Add a comma + space if both comment threads and unresolved
      (commentThreadString && unresolvedString ? ', ' : '') +
      `${unresolvedString})`
    );
  }

  _computePatchSetDescription(
    revisions: (RevisionInfo | EditRevisionInfo)[],
    patchNum: PatchSetNum,
    addFrontSpace?: boolean
  ) {
    const rev = getRevisionByPatchNum(revisions, patchNum);
    return rev?.description
      ? (addFrontSpace ? ' ' : '') +
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH)
      : '';
  }

  _computePatchSetDate(
    revisions: (RevisionInfo | EditRevisionInfo)[],
    patchNum: PatchSetNum
  ): Timestamp | undefined {
    const rev = getRevisionByPatchNum(revisions, patchNum);
    return rev ? rev.created : undefined;
  }

  /**
   * Catches value-change events from the patchset dropdowns and determines
   * whether or not a patch change event should be fired.
   */
  _handlePatchChange(e: DropDownValueChangeEvent) {
    const detail: PatchRangeChangeDetail = {
      patchNum: this.patchNum,
      basePatchNum: this.basePatchNum,
    };
    const target = e.target;
    const patchSetValue = convertToPatchSetNum(e.detail.value)!;
    const latestPatchNum = computeLatestPatchNum(this.availablePatches);
    if (target === this.patchNumDropdown) {
      if (detail.patchNum === e.detail.value) return;
      this.reporting.reportInteraction('right-patchset-changed', {
        previous: detail.patchNum,
        current: e.detail.value,
        latest: latestPatchNum,
        commentCount: this.changeComments?.computeCommentThreadCount({
          patchNum: e.detail.value as PatchSetNum,
        }),
      });
      detail.patchNum = patchSetValue;
    } else {
      if (detail.basePatchNum === patchSetValue) return;
      this.reporting.reportInteraction('left-patchset-changed', {
        previous: detail.basePatchNum,
        current: e.detail.value,
        commentCount: this.changeComments?.computeCommentThreadCount({
          patchNum: patchSetValue,
        }),
      });
      detail.basePatchNum = patchSetValue as BasePatchSetNum;
    }

    this.dispatchEvent(
      new CustomEvent('patch-range-change', {detail, bubbles: false})
    );
  }

  /**
   * value has type string so we have to convert
   * anything inputed to string.
   *
   * This is so typescript checker doesn't fail.
   */
  _convertToString(key?: PatchSetNum) {
    return key !== undefined ? String(key) : '';
  }
}
