/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dropdown-list/gr-dropdown-list';
import '../../shared/gr-select/gr-select';
import '../../shared/gr-weblink/gr-weblink';
import {convertToString, pluralize} from '../../../utils/string-util';
import {getAppContext} from '../../../services/app-context';
import {
  computeLatestPatchNum,
  findSortedIndex,
  getParentIndex,
  getRevisionByPatchNum,
  isMergeParent,
  PatchSet,
  convertToPatchSetNum,
  getParentInfoString,
  shorten,
  getParentCommit,
} from '../../../utils/patch-set-util';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  BasePatchSetNum,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RevisionInfo,
  RevisionPatchSetNum,
  Timestamp,
  WebLinkInfo,
} from '../../../types/common';
import {RevisionInfo as RevisionInfoClass} from '../../shared/revision-info/revision-info';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {EditRevisionInfo} from '../../../types/types';
import {a11yStyles} from '../../../styles/gr-a11y-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {resolve} from '../../../models/dependency';
import {ValueChangedEvent} from '../../../types/events';
import {
  changeModelToken,
  RevisionFileUpdateStatus,
  RevisionUpdatedFiles,
} from '../../../models/change/change-model';
import {changeViewModelToken} from '../../../models/views/change';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {FlagsService, KnownExperimentId} from '../../../services/flags/flags';

// Maximum length for patch set descriptions.
const PATCH_DESC_MAX_LENGTH = 500;

export interface PatchRangeChangeDetail {
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
}

export type PatchRangeChangeEvent = CustomEvent<PatchRangeChangeDetail>;

export interface FilesWebLinks {
  meta_a: WebLinkInfo[];
  meta_b: WebLinkInfo[];
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-patch-range-select': GrPatchRangeSelect;
  }
}

declare global {
  interface HTMLElementEventMap {
    'patch-range-change': PatchRangeChangeEvent;
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

  @state()
  availablePatches: PatchSet[] = [];

  @state()
  changeNum?: NumericChangeId;

  @property()
  path?: string;

  @property({type: Object})
  filesWeblinks?: FilesWebLinks;

  @state()
  patchNum?: RevisionPatchSetNum;

  @state()
  basePatchNum?: BasePatchSetNum;

  @state()
  revisionInfo?: RevisionInfoClass;

  @state()
  sortedRevisions: (RevisionInfo | EditRevisionInfo)[] = [];

  @state()
  changeComments?: ChangeComments;

  @state()
  revisionUpdatedFiles?: RevisionUpdatedFiles;

  private readonly reporting: ReportingService =
    getAppContext().reportingService;

  private readonly flags: FlagsService = getAppContext().flagsService;

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getViewModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.revisionInfo = x ? new RevisionInfoClass(x) : undefined)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchNum$,
      x => (this.patchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().basePatchNum$,
      x => (this.basePatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().patchsets$,
      x => (this.availablePatches = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().revisions$,
      x => (this.sortedRevisions = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      x => (this.changeComments = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().revisionUpdatedFiles$,
      x => (this.revisionUpdatedFiles = x)
    );
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
        .filesWeblinks gr-weblink {
          vertical-align: baseline;
        }
        @media screen and (max-width: 50em) {
          .filesWeblinks {
            display: none;
          }
          /* prettier formatter removes semi-colons after css mixins. */
          /* prettier-ignore */
          gr-dropdown-list {
            --native-select-style: {
              max-width: 5.25em;
            };
          }
        }
      `,
    ];
  }

  override render() {
    if (!this.changeNum || !this.patchNum || !this.basePatchNum) {
      return nothing;
    }
    return html`
      <h3 class="assistive-tech-only">Patchset Range Selection</h3>
      <span class="patchRange" aria-label="patch range starts with">
        <gr-dropdown-list
          id="basePatchDropdown"
          .value=${convertToString(this.basePatchNum)}
          .items=${this.computeBaseDropdownContent()}
          @value-change=${this.handlePatchChange}
        >
        </gr-dropdown-list>
      </span>
      ${this.renderWeblinks(this.filesWeblinks?.meta_a)}
      <span aria-hidden="true" class="arrow">→</span>
      <span class="patchRange" aria-label="patch range ends with">
        <gr-dropdown-list
          id="patchNumDropdown"
          .value=${convertToString(this.patchNum)}
          .items=${this.computePatchDropdownContent()}
          @value-change=${this.handlePatchChange}
        >
        </gr-dropdown-list>
        ${this.renderWeblinks(this.filesWeblinks?.meta_b)}
      </span>
    `;
  }

  private renderWeblinks(fileLinks?: WebLinkInfo[]) {
    if (!fileLinks) return;
    return html`<span class="filesWeblinks">
      ${fileLinks.map(
        weblink => html`<gr-weblink .info=${weblink}></gr-weblink>`
      )}</span
    > `;
  }

  // Private method, but visible for testing.
  computeBaseDropdownContent(): DropdownItem[] {
    if (
      this.patchNum === undefined ||
      this.changeComments === undefined ||
      this.revisionInfo === undefined
    ) {
      return [];
    }

    const maxParents = this.revisionInfo.getMaxParents();
    const isMerge = this.revisionInfo.isMergeCommit(this.patchNum);
    const parentCount = this.revisionInfo.getParentCount(this.patchNum);
    const rev = getRevisionByPatchNum(this.sortedRevisions, this.patchNum);

    const dropdownContent: DropdownItem[] = [];
    for (const basePatch of this.availablePatches) {
      const basePatchNum = basePatch.num;
      const entry: DropdownItem = this.createDropdownEntry(
        basePatchNum,
        'Patchset ',
        basePatch.sha
      );
      dropdownContent.push({
        ...entry,
        disabled: this.computeLeftDisabled(basePatch.num, this.patchNum),
      });
    }

    const showParentsData = this.flags.isEnabled(
      KnownExperimentId.REVISION_PARENTS_DATA
    );
    dropdownContent.push({
      triggerText: isMerge ? 'Auto Merge' : 'Base',
      text: isMerge ? 'Auto Merge' : `Base | ${getParentCommit(rev, 0)}`,
      bottomText:
        showParentsData && !isMerge ? getParentInfoString(rev, 0) : undefined,
      value: PARENT,
    });

    for (let idx = 0; isMerge && idx < maxParents; idx++) {
      dropdownContent.push({
        disabled: idx >= parentCount,
        triggerText: `Parent ${idx + 1}`,
        text: `Parent ${idx + 1} | ${getParentCommit(rev, idx)}`,
        bottomText: showParentsData ? getParentInfoString(rev, idx) : undefined,
        mobileText: `Parent ${idx + 1}`,
        value: -(idx + 1),
      });
    }

    return dropdownContent;
  }

  private computeMobileText(patchNum: PatchSetNum) {
    return (
      `${patchNum}` +
      `${this.computePatchSetCommentsString(patchNum)}` +
      `${this.computePatchSetDescription(patchNum, true)}`
    );
  }

  // Private method, but visible for testing.
  computePatchDropdownContent(): DropdownItem[] {
    if (
      this.availablePatches === undefined ||
      this.basePatchNum === undefined ||
      this.changeComments === undefined
    ) {
      return [];
    }

    const dropdownContent: DropdownItem[] = [];
    for (const patch of this.availablePatches) {
      const patchNum = patch.num;
      const entry = this.createDropdownEntry(
        patchNum,
        patchNum === EDIT ? '' : 'Patchset ',
        patch.sha
      );
      dropdownContent.push({
        ...entry,
        disabled: this.computeRightDisabled(this.basePatchNum, patchNum),
      });
    }
    return dropdownContent;
  }

  private computeText(patchNum: PatchSetNum, prefix: string, sha: string) {
    return `${prefix}${patchNum} | ${sha}`;
  }

  // Private method, but visible for testing.
  createDropdownEntry(patchNum: PatchSetNum, prefix: string, sha: string) {
    const entry: DropdownItem = {
      triggerText: `${prefix}${patchNum}`,
      text: this.computeText(patchNum, prefix, shorten(sha)!),
      mobileText: this.computeMobileText(patchNum),
      bottomText: `${this.computePatchSetDescription(patchNum)}`,
      value: patchNum,
      commentThreads: this.changeComments?.computeCommentThreads(
        {
          path: this.path,
          patchNum,
        },
        // don't ignore patchset level comments if the path is not set
        !!this.path /* ignorePatchsetLevelComments*/
      ),
      deemphasizeReason: this.computeDeemphasizeReason(sha),
    };
    const date = this.computePatchSetDate(patchNum);
    if (date) {
      entry.date = date;
    }
    return entry;
  }

  private computeDeemphasizeReason(sha: string) {
    if (!this.path || !this.revisionUpdatedFiles) {
      return undefined;
    }

    return this.revisionUpdatedFiles[sha]?.[this.path] ===
      RevisionFileUpdateStatus.SAME
      ? 'unmodified'
      : undefined;
  }

  /**
   * The basePatchNum should always be <= patchNum -- because sortedRevisions
   * is sorted in reverse order (higher patchset nums first), invalid base
   * patch nums have an index greater than the index of patchNum.
   *
   * Private method, but visible for testing.
   *
   * @param basePatchNum The possible base patch num.
   * @param patchNum The current selected patch num.
   */
  computeLeftDisabled(
    basePatchNum: PatchSetNum,
    patchNum: PatchSetNum
  ): boolean {
    return (
      findSortedIndex(basePatchNum, this.sortedRevisions) <=
      findSortedIndex(patchNum, this.sortedRevisions)
    );
  }

  /**
   * The basePatchNum should always be <= patchNum -- because sortedRevisions
   * is sorted in reverse order (higher patchset nums first), invalid patch
   * nums have an index greater than the index of basePatchNum.
   *
   * In addition, if the current basePatchNum is PARENT, all patchNums are
   * valid.
   *
   * If the current basePatchNum is a parent index, then only patches that have
   * at least that many parents are valid.
   *
   * Private method, but visible for testing.
   *
   * @param basePatchNum The current selected base patch num.
   * @param patchNum The possible patch num.
   */
  computeRightDisabled(
    basePatchNum: BasePatchSetNum,
    patchNum: RevisionPatchSetNum
  ): boolean {
    if (basePatchNum === PARENT) {
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
      findSortedIndex(basePatchNum, this.sortedRevisions) <=
      findSortedIndex(patchNum, this.sortedRevisions)
    );
  }

  // TODO(dhruvsri): have ported comments contribute to this count
  // Private method, but visible for testing.
  computePatchSetCommentsString(patchNum: PatchSetNum): string {
    if (!this.changeComments) return '';

    const commentThreadCount = this.changeComments.computeCommentThreads(
      {
        path: this.path,
        patchNum,
      },
      // don't ignore patchset level comments if the path is not set
      !!this.path /* ignorePatchsetLevelComments*/
    ).length;
    const commentThreadString = pluralize(commentThreadCount, 'comment');

    const unresolvedCount = this.changeComments.computeUnresolvedNum(
      {
        path: this.path,
        patchNum,
      },
      // don't ignore patchset level comments if the path is not set
      !!this.path /* ignorePatchsetLevelComments*/
    );
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

  private computePatchSetDescription(
    patchNum: PatchSetNum,
    addFrontSpace?: boolean
  ) {
    const rev = getRevisionByPatchNum(this.sortedRevisions, patchNum);

    return rev?.description
      ? (addFrontSpace ? ' ' : '') +
          rev.description.substring(0, PATCH_DESC_MAX_LENGTH)
      : '';
  }

  private computePatchSetDate(patchNum: PatchSetNum): Timestamp | undefined {
    const rev = getRevisionByPatchNum(this.sortedRevisions, patchNum);
    return rev ? rev.created : undefined;
  }

  /**
   * Catches value-change events from the patchset dropdowns and determines
   * whether or not a patch change event should be fired.
   */
  private handlePatchChange(e: ValueChangedEvent<string>) {
    const detail: PatchRangeChangeDetail = {
      patchNum: this.patchNum,
      basePatchNum: this.basePatchNum,
    };
    const target = e.target;
    const patchSetValue = convertToPatchSetNum(
      e.detail.value
    ) as RevisionPatchSetNum;
    const latestPatchNum = computeLatestPatchNum(this.availablePatches);
    if (target === this.patchNumDropdown) {
      if (detail.patchNum === patchSetValue) return;
      this.reporting.reportInteraction('right-patchset-changed', {
        path: this.path,
        previous: detail.patchNum,
        current: patchSetValue,
        latest: latestPatchNum,
        commentCount: this.changeComments?.computeCommentThreads({
          path: this.path,
          patchNum: patchSetValue,
        }).length,
      });
      detail.patchNum = patchSetValue;
    } else {
      if (detail.basePatchNum === patchSetValue) return;
      this.reporting.reportInteraction('left-patchset-changed', {
        path: this.path,
        previous: detail.basePatchNum,
        current: patchSetValue,
        commentCount: this.changeComments?.computeCommentThreads({
          path: this.path,
          patchNum: patchSetValue,
        }).length,
      });
      detail.basePatchNum = patchSetValue as BasePatchSetNum;
    }

    fireNoBubbleNoCompose(this, 'patch-range-change', detail);
  }
}
