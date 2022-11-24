/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-related-change';
import './gr-related-collapse';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import '../../shared/gr-icon/gr-icon';
import {classMap} from 'lit/directives/class-map.js';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  ChangeInfo,
  CommitId,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RevisionPatchSetNum,
  SubmittedTogetherInfo,
} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {truncatePath} from '../../../utils/path-list-util';
import {pluralize} from '../../../utils/string-util';
import {
  changeIsOpen,
  getChangeNumber,
  getRevisionKey,
} from '../../../utils/change-util';
import {DEFALT_NUM_CHANGES_WHEN_COLLAPSED} from './gr-related-collapse';
import {createChangeUrl} from '../../../models/views/change';

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

@customElement('gr-related-changes-list')
export class GrRelatedChangesList extends LitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  mergeable?: boolean;

  @state()
  submittedTogether?: SubmittedTogetherInfo = {
    changes: [],
    non_visible_changes: 0,
  };

  @state()
  relatedChanges: RelatedChangeAndCommitInfo[] = [];

  @state()
  conflictingChanges: ChangeInfo[] = [];

  @state()
  cherryPickChanges: ChangeInfo[] = [];

  @state()
  sameTopicChanges: ChangeInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
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
        .relatedChangeLine {
          display: flex;
          visibility: visible;
          height: auto;
        }
        .marker.arrow {
          visibility: hidden;
          min-width: 20px;
        }
        .marker.arrowToCurrentChange {
          min-width: 20px;
          text-align: center;
        }
        .marker.space {
          height: 1px;
          min-width: 20px;
        }
        .repo {
          margin-left: var(--spacing-m);
        }
        .repo,
        .branch {
          color: var(--primary-text-color);
        }
        @media screen and (max-width: 1400px) {
          .repo,
          .branch {
            display: none;
          }
        }

        gr-related-collapse[collapsed] .marker.arrow {
          visibility: visible;
          min-width: auto;
        }
        gr-related-collapse[collapsed] .relatedChangeLine.show-when-collapsed {
          visibility: visible;
          height: auto;
        }
        /* keep width, so width of section and position of show all button
         * are set according to width of all (even hidden) elements
         */
        gr-related-collapse[collapsed] .relatedChangeLine {
          visibility: hidden;
          height: 0px;
        }
      `,
    ];
  }

  override render() {
    const sectionSize = this.sectionSizeFactory(
      this.relatedChanges.length,
      this.submittedTogether?.changes.length || 0,
      this.sameTopicChanges.length,
      this.conflictingChanges.length,
      this.cherryPickChanges.length
    );

    const sectionRenderers = [
      this.renderRelationChain,
      this.renderSubmittedTogether,
      this.renderSameTopic,
      this.renderMergeConflicts,
      this.renderCherryPicks,
    ];

    let firstNonEmptySectionFound = false;
    const sections = [];
    for (const renderer of sectionRenderers) {
      const section: TemplateResult<1> | undefined = renderer.call(
        this,
        !firstNonEmptySectionFound,
        sectionSize
      );
      firstNonEmptySectionFound = firstNonEmptySectionFound || !!section;
      sections.push(section);
    }

    return html`<gr-endpoint-decorator name="related-changes-section">
      <gr-endpoint-param
        name="change"
        .value=${this.change}
      ></gr-endpoint-param>
      <gr-endpoint-slot name="top"></gr-endpoint-slot>
      ${sections}
      <gr-endpoint-slot name="bottom"></gr-endpoint-slot>
    </gr-endpoint-decorator>`;
  }

  private renderRelationChain(
    isFirst: boolean,
    sectionSize: (section: Section) => number
  ) {
    if (this.relatedChanges.length === 0) {
      return undefined;
    }
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

    return html`<section id="relatedChanges">
      <gr-related-collapse
        title="Relation chain"
        class=${classMap({first: isFirst})}
        .length=${this.relatedChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.RELATED_CHANGES)}
      >
        ${this.relatedChanges.map(
          (change, index) =>
            html`<div
              class=${classMap({
                ['relatedChangeLine']: true,
                ['show-when-collapsed']:
                  relatedChangesMarkersPredicate(index).showWhenCollapsed,
              })}
            >
              ${this.renderMarkers(
                relatedChangesMarkersPredicate(index)
              )}<gr-related-change
                .change=${change}
                .connectedRevisions=${connectedRevisions}
                .href=${change?._change_number
                  ? createChangeUrl({
                      changeNum: change._change_number,
                      repo: change.project,
                      usp: 'related-change',
                      patchNum: change._revision_number as RevisionPatchSetNum,
                    })
                  : ''}
                show-change-status
                show-submittable-check
                >${change.commit.subject}</gr-related-change
              >
            </div>`
        )}
      </gr-related-collapse>
    </section>`;
  }

  private renderSubmittedTogether(
    isFirst: boolean,
    sectionSize: (section: Section) => number
  ) {
    const submittedTogetherChanges = this.submittedTogether?.changes ?? [];
    if (
      !submittedTogetherChanges.length &&
      !this.submittedTogether?.non_visible_changes
    ) {
      return undefined;
    }
    const countNonVisibleChanges =
      this.submittedTogether?.non_visible_changes ?? 0;
    const submittedTogetherMarkersPredicate = this.markersPredicateFactory(
      submittedTogetherChanges.length,
      submittedTogetherChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      ),
      sectionSize(Section.SUBMITTED_TOGETHER)
    );
    return html`<section id="submittedTogether">
      <gr-related-collapse
        title="Submitted together"
        class=${classMap({first: isFirst})}
        .length=${submittedTogetherChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.SUBMITTED_TOGETHER)}
      >
        ${submittedTogetherChanges.map(
          (change, index) =>
            html`<div
              class=${classMap({
                ['relatedChangeLine']: true,
                ['show-when-collapsed']:
                  submittedTogetherMarkersPredicate(index).showWhenCollapsed,
              })}
            >
              ${this.renderMarkers(
                submittedTogetherMarkersPredicate(index)
              )}${this.renderSubmittedTogetherLine(change, true)}
            </div>`
        )}
      </gr-related-collapse>
      <div class="note" ?hidden=${!countNonVisibleChanges}>
        (+ ${pluralize(countNonVisibleChanges, 'non-visible change')})
      </div>
    </section>`;
  }

  private renderSubmittedTogetherLine(
    change: ChangeInfo,
    showSubmittabilityCheck: boolean
  ) {
    const truncatedRepo = truncatePath(change.project, 2);
    return html`
      <gr-related-change
        .label=${this.renderChangeTitle(change)}
        .change=${change}
        .href=${createChangeUrl({change, usp: 'submitted-together'})}
        ?show-submittable-check=${showSubmittabilityCheck}
        >${change.subject}</gr-related-change
      >
      <span class="repo" .title=${change.project}>${truncatedRepo}</span
      ><span class="branch">&nbsp;|&nbsp;${change.branch}&nbsp;</span>
    `;
  }

  private renderSameTopic(
    isFirst: boolean,
    sectionSize: (section: Section) => number
  ) {
    if (!this.sameTopicChanges?.length) {
      return undefined;
    }

    const sameTopicMarkersPredicate = this.markersPredicateFactory(
      this.sameTopicChanges.length,
      -1,
      sectionSize(Section.SAME_TOPIC)
    );
    return html`<section id="sameTopic">
      <gr-related-collapse
        title="Same topic"
        class=${classMap({first: isFirst})}
        .length=${this.sameTopicChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.SAME_TOPIC)}
      >
        ${this.sameTopicChanges.map(
          (change, index) =>
            html`<div
              class=${classMap({
                ['relatedChangeLine']: true,
                ['show-when-collapsed']:
                  sameTopicMarkersPredicate(index).showWhenCollapsed,
              })}
            >
              ${this.renderMarkers(
                sameTopicMarkersPredicate(index)
              )}${this.renderSubmittedTogetherLine(change, false)}
            </div>`
        )}
      </gr-related-collapse>
    </section>`;
  }

  private renderMergeConflicts(
    isFirst: boolean,
    sectionSize: (section: Section) => number
  ) {
    if (!this.conflictingChanges?.length) {
      return undefined;
    }
    const mergeConflictsMarkersPredicate = this.markersPredicateFactory(
      this.conflictingChanges.length,
      -1,
      sectionSize(Section.MERGE_CONFLICTS)
    );
    return html`<section id="mergeConflicts">
      <gr-related-collapse
        title="Merge conflicts"
        class=${classMap({first: isFirst})}
        .length=${this.conflictingChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.MERGE_CONFLICTS)}
      >
        ${this.conflictingChanges.map(
          (change, index) =>
            html`<div
              class=${classMap({
                ['relatedChangeLine']: true,
                ['show-when-collapsed']:
                  mergeConflictsMarkersPredicate(index).showWhenCollapsed,
              })}
            >
              ${this.renderMarkers(
                mergeConflictsMarkersPredicate(index)
              )}<gr-related-change
                .change=${change}
                .href=${createChangeUrl({change, usp: 'merge-conflict'})}
                >${change.subject}</gr-related-change
              >
            </div>`
        )}
      </gr-related-collapse>
    </section>`;
  }

  private renderCherryPicks(
    isFirst: boolean,
    sectionSize: (section: Section) => number
  ) {
    if (!this.cherryPickChanges.length) {
      return undefined;
    }
    const cherryPicksMarkersPredicate = this.markersPredicateFactory(
      this.cherryPickChanges.length,
      -1,
      sectionSize(Section.CHERRY_PICKS)
    );
    return html`<section id="cherryPicks">
      <gr-related-collapse
        title="Cherry picks"
        class=${classMap({first: isFirst})}
        .length=${this.cherryPickChanges.length}
        .numChangesWhenCollapsed=${sectionSize(Section.CHERRY_PICKS)}
      >
        ${this.cherryPickChanges.map(
          (change, index) =>
            html`<div
              class=${classMap({
                ['relatedChangeLine']: true,
                ['show-when-collapsed']:
                  cherryPicksMarkersPredicate(index).showWhenCollapsed,
              })}
            >
              ${this.renderMarkers(
                cherryPicksMarkersPredicate(index)
              )}<gr-related-change
                .change=${change}
                .href=${createChangeUrl({change, usp: 'cherry-pick'})}
                show-change-status
                >${change.branch}: ${change.subject}</gr-related-change
              >
            </div>`
        )}
      </gr-related-collapse>
    </section>`;
  }

  private renderChangeTitle(change: ChangeInfo) {
    return `${change.project}: ${change.branch}: ${change.subject}`;
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
        class="marker arrowToCurrentChange"
        aria-label="Arrow marking current change"
        >➔</span
      >`;
    }
    if (changeMarkers.showTopArrow) {
      return html`<span
        role="img"
        class="marker arrow"
        aria-label="Arrow marking change has collapsed ancestors"
        ><gr-icon icon="arrow_drop_up"></gr-icon
      ></span> `;
    }
    if (changeMarkers.showBottomArrow) {
      return html`<span
        role="img"
        class="marker arrow"
        aria-label="Arrow marking change has collapsed descendants"
        ><gr-icon icon="arrow_drop_down"></gr-icon
      ></span> `;
    }
    return html`<span class="marker space"></span>`;
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
              .getChangesWithSameTopic(changeTopic, {
                openChangesOnly: true,
                changeToExclude: change._number,
              })
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
    if (!a || !b) return false;
    const aNum = getChangeNumber(a);
    const bNum = getChangeNumber(b);
    return aNum === bNum;
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

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-changes-list': GrRelatedChangesList;
  }
}
