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

import {customElement, property} from 'lit/decorators';
import {LitElement, html, css} from 'lit-element/lit-element';
import '../shared/gr-button/gr-button';
import {ChangeInfo} from '../../api/rest-api';

// TODO: copied from gr-change-list-item. Extract both places to a util.
enum ChangeSize {
  XS = 10,
  SMALL = 50,
  MEDIUM = 250,
  LARGE = 1000,
}

/**
 * A single change shown as part of the topic tree.
 */
@customElement('gr-topic-tree-row')
export class GrTopicTreeRow extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  static override styles = css`
    :host {
      display: contents;
    }
  `;

  override render() {
    if (this.change === undefined) {
      return;
    }
    const authorName =
      this.change.revisions?.[this.change.current_revision!].commit?.author
        .name;
    return html`
      <tr>
        <td>${this.computeSize(this.change)}</td>
        <td>${this.change.subject}</td>
        <td>${this.change.topic}</td>
        <td>${this.change.branch}</td>
        <td>${authorName}</td>
        <td>${this.change.status}</td>
      </tr>
    `;
  }

  // TODO: copied from gr-change-list-item. Extract both places to a util.
  private computeSize(change: ChangeInfo) {
    const delta = change.insertions + change.deletions;
    if (isNaN(delta) || delta === 0) {
      return;
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-topic-tree-row': GrTopicTreeRow;
  }
}
