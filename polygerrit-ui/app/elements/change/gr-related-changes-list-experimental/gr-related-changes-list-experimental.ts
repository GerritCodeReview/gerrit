/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {html, nothing} from 'lit-html';
import './gr-related-change';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import {classMap} from 'lit-html/directives/class-map';
import {GrLitElement} from '../../lit/gr-lit-element';
import {
  customElement,
  property,
  css,
  internalProperty,
  TemplateResult,
} from 'lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  SubmittedTogetherInfo,
  ChangeInfo,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  PatchSetNum,
  CommitId,
} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {pluralize} from '../../../utils/string-util';
import {
  changeIsOpen,
  getRevisionKey,
  isChangeInfo,
} from '../../../utils/change-util';

/** What is the maximum number of shown changes in collapsed list? */
const DEFALT_NUM_CHANGES_WHEN_COLLAPSED = 3;

export interface ChangeMarkersInList {
  showCurrentChangeArrow: boolean;
  showWhenCollapsed: boolean;
  showTopArrow: boolean;
  showBottomArrow: boolean;
}

export enum Section {
  RELATED_CHANGES = 'related changes',
  SUBMITTED_TOGETHER = 'submitted together',
  SAME_TOPIC = 'same topic',
  MERGE_CONFLICTS = 'merge conflicts',
  CHERRY_PICKS = 'cherry picks',
}

@customElement('gr-related-changes-list-experimental')
export class GrRelatedChangesListExperimental extends GrLitElement {
  @property()
  change?: ParsedChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property()
  mergeable?: boolean;

  @internalProperty()
  submittedTogether?: SubmittedTogetherInfo = {
    changes: [],
    non_visible_changes: 0,
  };

  @internalProperty()
  relatedChanges: RelatedChangeAndCommitInfo[] = [];

  @internalProperty()
  conflictingChanges: ChangeInfo[] = [];

  @internalProperty()
  cherryPickChanges: ChangeInfo[] = [];

  @internalProperty()
  sameTopicChanges: ChangeInfo[] = [];

  private readonly restApiService = appContext.restApiService;

  static get styles() {
    return [
      sharedStyles,
      css`
        .note {
          color: var(--error-text-color);
          margin-left: 1.2em;
        }
        section {
          margin-bottom: var(--spacing-l);
        }
        gr-related-change {
          display: flex;
        }
        .marker {
          position: absolute;
          margin-left: calc(-1 * var(--spacing-s));
        }
        .arrowToCurrentChange {
          position: absolute;
        }
      `,
    ];
  }

  render() {
    const sectionSize = this.sectionSizeFactory(
      this.relatedChanges.length,
      this.submittedTogether?.changes.length || 0,
      this.sameTopicChanges.length,
      this.conflictingChanges.length,
      this.cherryPickChanges.length
    );
    const relatedChangesMarkersPredicate = this.markersPredicateFactory(
      this.relatedChanges.length,
      this.relatedChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      ),
      sectionSize(Section.RELATED_CHANGES)
    );
    const connectedRevisions = this._computeConnectedRevisions(
      this.change,
      this.patchNum,
      this.relatedChanges
    );
    let firstNonEmptySectionFound = false;
    let isFirstNonEmpty =
      !firstNonEmptySectionFound && !!this.relatedChanges.length;
    firstNonEmptySectionFound = firstNonEmptySectionFound || isFirstNonEmpty;
    const relatedChangeSection = html` <section
      id="relatedChanges"
      ?hidden=${!this.relatedChanges.length}
    >
      <gr-related-collapse
        title="Relation chain"
        class="${classMap({first: isFirstNonEmpty})}"
        .length=${this.relatedChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.RELATED_CHANGES)}
      >
        ${this.relatedChanges.map(
          (change, index) =>
            html`${this.renderMarkers(
                relatedChangesMarkersPredicate(index)
              )}<gr-related-change
                class="${classMap({
                  ['show-when-collapsed']: relatedChangesMarkersPredicate(index)
                    .showWhenCollapsed,
                })}"
                .change="${change}"
                .connectedRevisions="${connectedRevisions}"
                .href="${change?._change_number
                  ? GerritNav.getUrlForChangeById(
                      change._change_number,
                      change.project,
                      change._revision_number as PatchSetNum
                    )
                  : ''}"
                .showChangeStatus=${true}
                >${change.commit.subject}</gr-related-change
              >`
        )}
      </gr-related-collapse>
    </section>`;

    const submittedTogetherChanges = this.submittedTogether?.changes ?? [];
    const countNonVisibleChanges =
      this.submittedTogether?.non_visible_changes ?? 0;
    const submittedTogetherMarkersPredicate = this.markersPredicateFactory(
      submittedTogetherChanges.length,
      submittedTogetherChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      ),
      sectionSize(Section.SUBMITTED_TOGETHER)
    );
    isFirstNonEmpty =
      !firstNonEmptySectionFound &&
      (!!submittedTogetherChanges?.length ||
        !!this.submittedTogether?.non_visible_changes);
    firstNonEmptySectionFound = firstNonEmptySectionFound || isFirstNonEmpty;
    const submittedTogetherSection = html`<section
      id="submittedTogether"
      ?hidden=${!submittedTogetherChanges?.length &&
      !this.submittedTogether?.non_visible_changes}
    >
      <gr-related-collapse
        title="Submitted together"
        class="${classMap({first: isFirstNonEmpty})}"
        .length=${submittedTogetherChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.SUBMITTED_TOGETHER)}
      >
        ${submittedTogetherChanges.map(
          (change, index) =>
            html`${this.renderMarkers(
                submittedTogetherMarkersPredicate(index)
              )}<gr-related-change
                class="${classMap({
                  ['show-when-collapsed']: submittedTogetherMarkersPredicate(
                    index
                  ).showWhenCollapsed,
                })}"
                .change="${change}"
                .href="${GerritNav.getUrlForChangeById(
                  change._number,
                  change.project
                )}"
                .showSubmittableCheck=${true}
                >${change.project}: ${change.branch}:
                ${change.subject}</gr-related-change
              >`
        )}
      </gr-related-collapse>
      <div class="note" ?hidden=${!countNonVisibleChanges}>
        (+ ${pluralize(countNonVisibleChanges, 'non-visible change')})
      </div>
    </section>`;

    const sameTopicMarkersPredicate = this.markersPredicateFactory(
      this.sameTopicChanges.length,
      -1,
      sectionSize(Section.SAME_TOPIC)
    );
    isFirstNonEmpty =
      !firstNonEmptySectionFound && !!this.sameTopicChanges?.length;
    firstNonEmptySectionFound = firstNonEmptySectionFound || isFirstNonEmpty;
    const sameTopicSection = html`<section
      id="sameTopic"
      ?hidden=${!this.sameTopicChanges?.length}
    >
      <gr-related-collapse
        title="Same topic"
        class="${classMap({first: isFirstNonEmpty})}"
        .length=${this.sameTopicChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.SAME_TOPIC)}
      >
        ${this.sameTopicChanges.map(
          (change, index) =>
            html`${this.renderMarkers(
                sameTopicMarkersPredicate(index)
              )}<gr-related-change
                class="${classMap({
                  ['show-when-collapsed']: sameTopicMarkersPredicate(index)
                    .showWhenCollapsed,
                })}"
                .change="${change}"
                .href="${GerritNav.getUrlForChangeById(
                  change._number,
                  change.project
                )}"
                >${change.project}: ${change.branch}:
                ${change.subject}</gr-related-change
              >`
        )}
      </gr-related-collapse>
    </section>`;

    const mergeConflictsMarkersPredicate = this.markersPredicateFactory(
      this.conflictingChanges.length,
      -1,
      sectionSize(Section.MERGE_CONFLICTS)
    );
    isFirstNonEmpty =
      !firstNonEmptySectionFound && !!this.conflictingChanges?.length;
    firstNonEmptySectionFound = firstNonEmptySectionFound || isFirstNonEmpty;
    const mergeConflictsSection = html`<section
      id="mergeConflicts"
      ?hidden=${!this.conflictingChanges?.length}
    >
      <gr-related-collapse
        title="Merge conflicts"
        class="${classMap({first: isFirstNonEmpty})}"
        .length=${this.conflictingChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.MERGE_CONFLICTS)}
      >
        ${this.conflictingChanges.map(
          (change, index) =>
            html`${this.renderMarkers(
                mergeConflictsMarkersPredicate(index)
              )}<gr-related-change
                class="${classMap({
                  ['show-when-collapsed']: mergeConflictsMarkersPredicate(index)
                    .showWhenCollapsed,
                })}"
                .change="${change}"
                .href="${GerritNav.getUrlForChangeById(
                  change._number,
                  change.project
                )}"
                >${change.subject}</gr-related-change
              >`
        )}
      </gr-related-collapse>
    </section>`;

    const cherryPicksMarkersPredicate = this.markersPredicateFactory(
      this.cherryPickChanges.length,
      -1,
      sectionSize(Section.CHERRY_PICKS)
    );
    isFirstNonEmpty =
      !firstNonEmptySectionFound && !!this.cherryPickChanges?.length;
    firstNonEmptySectionFound = firstNonEmptySectionFound || isFirstNonEmpty;
    const cherryPicksSection = html`<section
      id="cherryPicks"
      ?hidden=${!this.cherryPickChanges?.length}
    >
      <gr-related-collapse
        title="Cherry picks"
        class="${classMap({first: isFirstNonEmpty})}"
        .length=${this.cherryPickChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.CHERRY_PICKS)}
      >
        ${this.cherryPickChanges.map(
          (change, index) =>
            html`${this.renderMarkers(
                cherryPicksMarkersPredicate(index)
              )}<gr-related-change
                class="${classMap({
                  ['show-when-collapsed']: cherryPicksMarkersPredicate(index)
                    .showWhenCollapsed,
                })}"
                .change="${change}"
                .href="${GerritNav.getUrlForChangeById(
                  change._number,
                  change.project
                )}"
                >${change.branch}: ${change.subject}</gr-related-change
              >`
        )}
      </gr-related-collapse>
    </section>`;

    return html`<gr-endpoint-decorator name="related-changes-section">
      <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
      <gr-endpoint-slot name="top"></gr-endpoint-slot>
      ${relatedChangeSection} ${submittedTogetherSection} ${sameTopicSection}
      ${mergeConflictsSection} ${cherryPicksSection}
      <gr-endpoint-slot name="bottom"></gr-endpoint-slot>
    </gr-endpoint-decorator>`;
  }

  sectionSizeFactory(
    relatedChangesLen: number,
    submittedTogetherLen: number,
    sameTopicLen: number,
    mergeConflictsLen: number,
    cherryPicksLen: number
  ) {
    const calcDefaultSize = (length: number) =>
      Math.min(length, DEFALT_NUM_CHANGES_WHEN_COLLAPSED);

    const sectionSizes = [
      {
        section: Section.RELATED_CHANGES,
        size: calcDefaultSize(relatedChangesLen),
        len: relatedChangesLen,
      },
      {
        section: Section.SUBMITTED_TOGETHER,
        size: calcDefaultSize(submittedTogetherLen),
        len: submittedTogetherLen,
      },
      {
        section: Section.SAME_TOPIC,
        size: calcDefaultSize(sameTopicLen),
        len: sameTopicLen,
      },
      {
        section: Section.MERGE_CONFLICTS,
        size: calcDefaultSize(mergeConflictsLen),
        len: mergeConflictsLen,
      },
      {
        section: Section.CHERRY_PICKS,
        size: calcDefaultSize(cherryPicksLen),
        len: cherryPicksLen,
      },
    ];

    const FILLER = 1; // space for header
    let totalSize = sectionSizes.reduce(
      (acc, val) => acc + val.size + (val.size !== 0 ? FILLER : 0),
      0
    );

    const MAX_SIZE = 16;
    for (let i = 0; i < sectionSizes.length; i++) {
      if (totalSize >= MAX_SIZE) break;
      const sizeObj = sectionSizes[i];
      if (sizeObj.size === sizeObj.len) continue;
      const newSize = Math.min(
        MAX_SIZE - totalSize + sizeObj.size,
        sizeObj.len
      );
      totalSize += newSize - sizeObj.size;
      sizeObj.size = newSize;
    }

    return (section: Section) => {
      const sizeObj = sectionSizes.find(sizeObj => sizeObj.section === section);
      if (sizeObj) return sizeObj.size;
      return DEFALT_NUM_CHANGES_WHEN_COLLAPSED;
    };
  }

  markersPredicateFactory(
    length: number,
    highlightIndex: number,
    numChangesShownWhenCollapsed = DEFALT_NUM_CHANGES_WHEN_COLLAPSED
  ): (index: number) => ChangeMarkersInList {
    const showWhenCollapsedPredicate = (index: number) => {
      if (highlightIndex === -1) return index < numChangesShownWhenCollapsed;
      if (highlightIndex === 0)
        return index <= numChangesShownWhenCollapsed - 1;
      if (highlightIndex === length - 1)
        return index >= length - numChangesShownWhenCollapsed;
      let numBeforeHighlight = Math.floor(numChangesShownWhenCollapsed / 2);
      let numAfterHighlight =
        Math.floor(numChangesShownWhenCollapsed / 2) -
        (numChangesShownWhenCollapsed % 2 ? 0 : 1);
      numBeforeHighlight += Math.max(
        highlightIndex + numAfterHighlight - length + 1,
        0
      );
      numAfterHighlight -= Math.min(0, highlightIndex - numBeforeHighlight);
      return (
        highlightIndex - numBeforeHighlight <= index &&
        index <= highlightIndex + numAfterHighlight
      );
    };
    return (index: number) => {
      return {
        showCurrentChangeArrow:
          highlightIndex !== -1 && index === highlightIndex,
        showWhenCollapsed: showWhenCollapsedPredicate(index),
        showTopArrow:
          index >= 1 &&
          index !== highlightIndex &&
          showWhenCollapsedPredicate(index) &&
          !showWhenCollapsedPredicate(index - 1),
        showBottomArrow:
          index <= length - 2 &&
          index !== highlightIndex &&
          showWhenCollapsedPredicate(index) &&
          !showWhenCollapsedPredicate(index + 1),
      };
    };
  }

  renderMarkers(changeMarkers: ChangeMarkersInList) {
    if (changeMarkers.showCurrentChangeArrow) {
      return html`<span
        role="img"
        class="arrowToCurrentChange"
        aria-label="Arrow marking current change"
        >➔</span
      >`;
    }
    if (changeMarkers.showTopArrow) {
      return html`<span
        role="img"
        class="marker"
        aria-label="Arrow marking change has collapsed ancestors"
        ><iron-icon icon="gr-icons:arrowDropUp"></iron-icon
      ></span> `;
    }
    if (changeMarkers.showBottomArrow) {
      return html`<span
        role="img"
        class="marker"
        aria-label="Arrow marking change has collapsed descendants"
        ><iron-icon icon="gr-icons:arrowDropDown"></iron-icon
      ></span> `;
    }
    return nothing;
  }

  reload(getRelatedChanges?: Promise<RelatedChangesInfo | undefined>) {
    const change = this.change;
    if (!change) return Promise.reject(new Error('change missing'));
    if (!this.patchNum) return Promise.reject(new Error('patchNum missing'));
    if (!getRelatedChanges) {
      getRelatedChanges = this.restApiService.getRelatedChanges(
        change._number,
        this.patchNum
      );
    }
    const promises: Array<Promise<void>> = [
      getRelatedChanges.then(response => {
        if (!response) {
          throw new Error('getRelatedChanges returned undefined response');
        }
        this.relatedChanges = response?.changes ?? [];
      }),
      this.restApiService
        .getChangesSubmittedTogether(change._number)
        .then(response => {
          this.submittedTogether = response;
        }),
      this.restApiService
        .getChangeCherryPicks(change.project, change.change_id, change._number)
        .then(response => {
          this.cherryPickChanges = response || [];
        }),
    ];

    // Get conflicts if change is open and is mergeable.
    // Mergeable is output of restApiServict.getMergeable from gr-change-view
    if (changeIsOpen(change) && this.mergeable) {
      promises.push(
        this.restApiService
          .getChangeConflicts(change._number)
          .then(response => {
            this.conflictingChanges = response ?? [];
          })
      );
    }
    if (change.topic) {
      const changeTopic = change.topic;
      promises.push(
        this.restApiService.getConfig().then(config => {
          if (config && !config.change.submit_whole_topic) {
            return this.restApiService
              .getChangesWithSameTopic(changeTopic, change._number)
              .then(response => {
                if (changeTopic === this.change?.topic) {
                  this.sameTopicChanges = response ?? [];
                }
              });
          }
          this.sameTopicChanges = [];
          return Promise.resolve();
        })
      );
    }

    return Promise.all(promises);
  }

  /**
   * Do the given objects describe the same change? Compares the changes by
   * their numbers.
   */
  _changesEqual(
    a?: ChangeInfo | RelatedChangeAndCommitInfo,
    b?: ChangeInfo | ParsedChangeInfo | RelatedChangeAndCommitInfo
  ) {
    const aNum = this._getChangeNumber(a);
    const bNum = this._getChangeNumber(b);
    return aNum === bNum;
  }

  /**
   * Get the change number from either a ChangeInfo (such as those included in
   * SubmittedTogetherInfo responses) or get the change number from a
   * RelatedChangeAndCommitInfo (such as those included in a
   * RelatedChangesInfo response).
   */
  _getChangeNumber(
    change?: ChangeInfo | ParsedChangeInfo | RelatedChangeAndCommitInfo
  ) {
    // Default to 0 if change property is not defined.
    if (!change) return 0;

    if (isChangeInfo(change)) {
      return change._number;
    }
    return change._change_number;
  }

  /*
   * A list of commit ids connected to change to understand if other change
   * is direct or indirect ancestor / descendant.
   */
  _computeConnectedRevisions(
    change?: ParsedChangeInfo,
    patchNum?: PatchSetNum,
    relatedChanges?: RelatedChangeAndCommitInfo[]
  ) {
    if (!patchNum || !relatedChanges || !change) {
      return [];
    }

    const connected: CommitId[] = [];
    const changeRevision = getRevisionKey(change, patchNum);
    const commits = relatedChanges.map(c => c.commit);
    let pos = commits.length - 1;

    while (pos >= 0) {
      const commit: CommitId = commits[pos].commit;
      connected.push(commit);
      // TODO(TS): Ensure that both (commit and changeRevision) are string and use === instead
      // eslint-disable-next-line eqeqeq
      if (commit == changeRevision) {
        break;
      }
      pos--;
    }
    while (pos >= 0) {
      for (let i = 0; i < commits[pos].parents.length; i++) {
        if (connected.includes(commits[pos].parents[i].commit)) {
          connected.push(commits[pos].commit);
          break;
        }
      }
      --pos;
    }
    return connected;
  }
}

@customElement('gr-related-collapse')
export class GrRelatedCollapse extends GrLitElement {
  @property()
  title = '';

  @property()
  showAll = false;

  @property()
  length = 0;

  @property()
  numChangesWhenCollapsed = DEFALT_NUM_CHANGES_WHEN_COLLAPSED;

  private readonly reporting = appContext.reportingService;

  static get styles() {
    return [
      sharedStyles,
      css`
        .title {
          font-weight: var(--font-weight-bold);
          color: var(--deemphasized-text-color);
          padding-left: var(--metadata-horizontal-padding);
        }
        h4 {
          display: flex;
          align-self: flex-end;
        }
        gr-button {
          display: flex;
        }
        /* This is a hacky solution from old gr-related-change-list
         * TODO(milutin): find layout without needing it
         */
        h4:before,
        gr-button:before,
        ::slotted(gr-related-change):before {
          content: ' ';
          flex-shrink: 0;
          width: 1.2em;
        }
        .collapsed ::slotted(gr-related-change.show-when-collapsed) {
          visibility: visible;
          height: auto;
        }
        .collapsed ::slotted(.marker) {
          display: block;
        }
        .show-all ::slotted(.marker) {
          display: none;
        }
        /* keep width, so width of section and position of show all button
         * are set according to width of all (even hidden) elements
         */
        .collapsed ::slotted(gr-related-change) {
          visibility: hidden;
          height: 0px;
        }
        ::slotted(gr-related-change) {
          visibility: visible;
          height: auto;
        }
        gr-button iron-icon {
          color: inherit;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .container {
          justify-content: space-between;
          display: flex;
          margin-bottom: var(--spacing-s);
        }
        :host(.first) .container {
          margin-bottom: var(--spacing-m);
        }
      `,
    ];
  }

  render() {
    const title = html`<h4 class="title">${this.title}</h4>`;

    const collapsible = this.length > this.numChangesWhenCollapsed;
    const items = html` <div
      class="${!this.showAll && collapsible ? 'collapsed' : 'show-all'}"
    >
      <slot></slot>
    </div>`;

    let button: TemplateResult | typeof nothing = nothing;
    if (collapsible) {
      let buttonText = 'Show less';
      let buttonIcon = 'expand-less';
      if (!this.showAll) {
        buttonText = `Show all (${this.length})`;
        buttonIcon = 'expand-more';
      }
      button = html`<gr-button link="" @click="${this.toggle}"
        >${buttonText}<iron-icon icon="gr-icons:${buttonIcon}"></iron-icon
      ></gr-button>`;
    }

    return html`<div class="container">${title}${button}</div>
      ${items}`;
  }

  private toggle(e: MouseEvent) {
    e.stopPropagation();
    this.showAll = !this.showAll;
    this.reporting.reportInteraction('toggle show all button', {
      sectionName: this.title,
      toState: this.showAll ? 'Show all' : 'Show less',
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-changes-list-experimental': GrRelatedChangesListExperimental;
    'gr-related-collapse': GrRelatedCollapse;
  }
}
