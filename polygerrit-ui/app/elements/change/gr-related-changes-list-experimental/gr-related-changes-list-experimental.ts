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
import {classMap} from 'lit-html/directives/class-map';
import {GrLitElement} from '../../lit/gr-lit-element';
import {customElement, property, css} from 'lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  SubmittedTogetherInfo,
  ChangeInfo,
  RelatedChangeAndCommitInfo,
} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {pluralize} from '../../../utils/string-util';
import {ChangeStatus} from '../../../constants/constants';

function isChangeInfo(
  x: ChangeInfo | RelatedChangeAndCommitInfo | ParsedChangeInfo
): x is ChangeInfo | ParsedChangeInfo {
  return (x as ChangeInfo)._number !== undefined;
}

@customElement('gr-related-changes-list-experimental')
export class GrRelatedChangesListExperimental extends GrLitElement {
  @property()
  change?: ParsedChangeInfo;

  @property()
  _submittedTogether?: SubmittedTogetherInfo = {
    changes: [],
    non_visible_changes: 0,
  };

  private readonly restApiService = appContext.restApiService;

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
        }
        h4:before,
        gr-related-change:before {
          content: ' ';
          flex-shrink: 0;
          width: 1.2em;
        }
        .note {
          color: var(--error-text-color);
        }
      `,
    ];
  }

  render() {
    const submittedTogetherChanges = this._submittedTogether?.changes ?? [];
    const countNonVisibleChanges =
      this._submittedTogether?.non_visible_changes ?? 0;
    const showWhenCollapsedPredicate = this.showWhenCollapsedPredicateFactory(
      submittedTogetherChanges.length,
      submittedTogetherChanges.findIndex(relatedChange =>
        this._changesEqual(relatedChange, this.change)
      )
    );
    return html` <section
      id="submittedTogether"
      ?hidden=${!submittedTogetherChanges?.length &&
      !this._submittedTogether?.non_visible_changes}
    >
      <h4 class="title">Submitted together</h4>
      <gr-related-collapse .length=${submittedTogetherChanges.length}>
        ${submittedTogetherChanges.map(
          (relatedChange, index) =>
            html`<gr-related-change
              class="${classMap({
                ['show-when-collapsed']: showWhenCollapsedPredicate(index),
              })}"
              .currentChange="${this._changesEqual(relatedChange, this.change)}"
              .change="${relatedChange}"
            ></gr-related-change>`
        )}
      </gr-related-collapse>
      <div class="note" ?hidden=${!countNonVisibleChanges}>
        (+ ${pluralize(countNonVisibleChanges, 'non-visible change')})
      </div>
    </section>`;
  }

  showWhenCollapsedPredicateFactory(length: number, highlightIndex: number) {
    return (index: number) => {
      if (highlightIndex === 0) return index <= 2;
      if (highlightIndex === length - 1) return index >= length - 3;
      return highlightIndex - 1 <= index && index <= highlightIndex + 1;
    };
  }

  reload() {
    if (!this.change) return Promise.reject(new Error('change missing'));
    return this.restApiService
      .getChangesSubmittedTogether(this.change._number)
      .then(response => {
        this._submittedTogether = response;
      });
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
}

@customElement('gr-related-collapse')
export class GrRelatedCollapse extends GrLitElement {
  @property()
  showAll = false;

  @property()
  length = 0;

  static get styles() {
    return [
      sharedStyles,
      css`
        gr-button {
          display: flex;
        }
        gr-button:before {
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
      `,
    ];
  }

  render() {
    const collapsible = this.length > 3;
    const items = html` <div
      class="${!this.showAll && collapsible ? 'collapsed' : ''}"
    >
      <slot></slot>
    </div>`;
    let button = nothing;
    if (collapsible) {
      if (this.showAll) {
        button = html`<gr-button link="" @click="${this.toggle}"
          >Show less</gr-button
        >`;
      } else {
        button = html`<gr-button link="" @click="${this.toggle}"
          >+ ${this.length - 3} more</gr-button
        >`;
      }
    }

    return html`${items}${button}`;
  }

  private toggle(e: MouseEvent) {
    e.stopPropagation();
    this.showAll = !this.showAll;
  }
}

@customElement('gr-related-change')
export class GrRelatedChange extends GrLitElement {
  @property()
  change?: ChangeInfo;

  @property()
  currentChange = false;

  static get styles() {
    return [
      sharedStyles,
      css`
        a {
          display: block;
        }
        .changeContainer,
        a {
          max-width: 100%;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .changeContainer {
          display: flex;
        }
        .strikethrough {
          color: var(--deemphasized-text-color);
          text-decoration: line-through;
        }
        .submittableCheck {
          padding-left: var(--spacing-s);
          color: var(--positive-green-text-color);
          display: none;
        }
        .submittableCheck.submittable {
          display: inline;
        }
        .arrowToCurrentChange {
          position: absolute;
        }
      `,
    ];
  }

  render() {
    const change = this.change;
    if (!change) throw new Error('Missing change');
    const linkClass = this._computeLinkClass(change);
    return html`<span
        role="img"
        class="arrowToCurrentChange"
        aria-label="Arrow marking current change"
        ?hidden=${!this.currentChange}
        >➔</span
      >
      <div class="changeContainer">
        <a
          href="${GerritNav.getUrlForChangeById(
            change._number,
            change.project
          )}"
          class="${linkClass}"
          >${change.project}: ${change.branch}: ${change.subject}</a
        >
        <span
          tabindex="-1"
          title="Submittable"
          class="submittableCheck ${linkClass}"
          role="img"
          aria-label="Submittable"
          >✓</span
        >
      </div> `;
  }

  _computeLinkClass(change: ChangeInfo) {
    const statuses = [];
    if (change.status === ChangeStatus.ABANDONED) {
      statuses.push('strikethrough');
    }
    if (change.submittable) {
      statuses.push('submittable');
    }
    return statuses.join(' ');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-changes-list-experimental': GrRelatedChangesListExperimental;
    'gr-related-collapse': GrRelatedCollapse;
    'gr-related-change': GrRelatedChange;
  }
}
