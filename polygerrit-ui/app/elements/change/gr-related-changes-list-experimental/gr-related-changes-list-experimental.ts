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
import {classMap} from 'lit-html/directives/class-map';
import {GrLitElement} from '../../lit/gr-lit-element';
import {customElement, property, css} from 'lit-element';
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
import {getRevisionKey, isChangeInfo} from '../../../utils/change-util';

/** What is the maximum number of shown changes in collapsed list? */
const MAX_CHANGES_WHEN_COLLAPSED = 3;

@customElement('gr-related-changes-list-experimental')
export class GrRelatedChangesListExperimental extends GrLitElement {
  @property()
  change?: ParsedChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property()
  _submittedTogether?: SubmittedTogetherInfo = {
    changes: [],
    non_visible_changes: 0,
  };

  @property()
  _relatedResponse?: RelatedChangesInfo = {changes: []};

  private readonly restApiService = appContext.restApiService;

  static get styles() {
    return [
      sharedStyles,
      css`
        .note {
          color: var(--error-text-color);
        }
        section {
          margin-bottom: var(--spacing-m);
        }
      `,
    ];
  }

  render() {
    const relatedChanges = this._relatedResponse?.changes ?? [];
    let showWhenCollapsedPredicate = this.showWhenCollapsedPredicateFactory(
      relatedChanges.length,
      relatedChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      )
    );
    const connectedRevisions = this._computeConnectedRevisions(
      this.change,
      this.patchNum,
      relatedChanges
    );
    const relatedChangeSection = html` <section
      class="relatedChanges"
      ?hidden=${!relatedChanges.length}
    >
      <gr-related-collapse
        title="Relation chain"
        .length=${relatedChanges.length}
      >
        ${relatedChanges.map(
          (change, index) =>
            html`<gr-related-change
              class="${classMap({
                ['show-when-collapsed']: showWhenCollapsedPredicate(index),
              })}"
              .isCurrentChange="${this._changesEqual(change, this.change)}"
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

    const submittedTogetherChanges = this._submittedTogether?.changes ?? [];
    const countNonVisibleChanges =
      this._submittedTogether?.non_visible_changes ?? 0;
    showWhenCollapsedPredicate = this.showWhenCollapsedPredicateFactory(
      submittedTogetherChanges.length,
      submittedTogetherChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      )
    );
    const submittedTogetherSection = html`<section
      id="submittedTogether"
      ?hidden=${!submittedTogetherChanges?.length &&
      !this._submittedTogether?.non_visible_changes}
    >
      <gr-related-collapse
        title="Submitted together"
        .length=${submittedTogetherChanges.length}
      >
        ${submittedTogetherChanges.map(
          (change, index) =>
            html`<gr-related-change
              class="${classMap({
                ['show-when-collapsed']: showWhenCollapsedPredicate(index),
              })}"
              .isCurrentChange="${this._changesEqual(change, this.change)}"
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

    return html`${relatedChangeSection}${submittedTogetherSection}`;
  }

  showWhenCollapsedPredicateFactory(length: number, highlightIndex: number) {
    return (index: number) => {
      if (highlightIndex === 0) return index <= MAX_CHANGES_WHEN_COLLAPSED - 1;
      if (highlightIndex === length - 1)
        return index >= length - MAX_CHANGES_WHEN_COLLAPSED;
      return (
        highlightIndex - MAX_CHANGES_WHEN_COLLAPSED + 2 <= index &&
        index <= highlightIndex + MAX_CHANGES_WHEN_COLLAPSED - 2
      );
    };
  }

  reload(getRelatedChanges?: Promise<RelatedChangesInfo | undefined>) {
    if (!this.change) return Promise.reject(new Error('change missing'));
    const promises: Array<Promise<void>> = [
      this.restApiService
        .getChangesSubmittedTogether(this.change._number)
        .then(response => {
          this._submittedTogether = response;
        }),
    ];
    if (getRelatedChanges) {
      promises.push(
        getRelatedChanges.then(response => {
          if (!response) {
            throw new Error('getRelatedChanges returned undefined response');
          }
          this._relatedResponse = response;
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
          display: flex;
        }
        .collapsed ::slotted(gr-related-change) {
          display: none;
        }
        ::slotted(gr-related-change) {
          display: flex;
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
      `,
    ];
  }

  render() {
    const title = html`<h4 class="title">${this.title}</h4>`;

    const collapsible = this.length > MAX_CHANGES_WHEN_COLLAPSED;
    const items = html` <div
      class="${!this.showAll && collapsible ? 'collapsed' : ''}"
    >
      <slot></slot>
    </div>`;

    let button = nothing;
    if (collapsible) {
      if (this.showAll) {
        button = html`<gr-button link="" @click="${this.toggle}"
          >Show less<iron-icon icon="gr-icons:expand-less"></iron-icon
        ></gr-button>`;
      } else {
        button = html`<gr-button link="" @click="${this.toggle}"
          >Show all (${this.length})
          <iron-icon icon="gr-icons:expand-more"></iron-icon
        ></gr-button>`;
      }
    }

    return html`<div class="container">${title}${button}</div>
      ${items}`;
  }

  private toggle(e: MouseEvent) {
    e.stopPropagation();
    this.showAll = !this.showAll;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-changes-list-experimental': GrRelatedChangesListExperimental;
    'gr-related-collapse': GrRelatedCollapse;
  }
}
