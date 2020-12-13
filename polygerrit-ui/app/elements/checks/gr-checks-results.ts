/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from 'lit-html';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {
  Category,
  CheckResult,
  CheckRun,
  Link,
} from '../plugins/gr-checks-api/gr-checks-api-types';
import {sharedStyles} from '../../styles/shared-styles';

function renderRun(category: Category, run: CheckRun) {
  return html`${run.results
    ?.filter(result => result.category === category)
    .map(res => renderResult(run, res))}`;
}

function renderResult(run: CheckRun, result: CheckResult) {
  return html`<div class="resultRow">
    <span>${run.checkName}</span
    ><span>${result.summary ? ': ' + result.summary : ''}</span
    ><span>${(result.links ?? []).map(renderLink)}</span>
  </div>`;
}

function renderLink(link: Link) {
  return html`, <a href="${link.url}">Details</a>`;
}

export function iconForCategory(category: Category) {
  switch (category) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info-outline';
    case Category.WARNING:
      return 'warning';
  }
  throw new Error(`Unexpected category: ${category}`);
}

@customElement('gr-checks-results')
export class GrChecksResults extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-xl);
        }
        .categoryHeader {
          margin-top: var(--spacing-xl);
          margin-left: var(--spacing-l);
          text-transform: capitalize;
        }
        .categoryHeader iron-icon {
          vertical-align: top;
          position: relative;
          top: 2px;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .categoryHeader iron-icon.error {
          color: #d93025;
        }
        .categoryHeader iron-icon.warning {
          color: #e37400;
        }
        .categoryHeader iron-icon.info {
          color: #174ea6;
        }
        .resultsTable {
          margin-top: var(--spacing-m);
          background-color: var(--primary-background-color);
          box-shadow: var(--elevation-level-1);
        }
        .headerRow {
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s) var(--spacing-xl);
        }
        .resultRow {
          border-top: 1px solid var(--border-color);
          padding: var(--spacing-s) var(--spacing-xl);
        }
      `,
    ];
  }

  render() {
    return html`
      <div><h2 class="heading-2">Results</h2></div>
      ${this.renderSection(Category.ERROR)}
      ${this.renderSection(Category.WARNING)}
      ${this.renderSection(Category.INFO)}
    `;
  }

  renderSection(category: Category) {
    const catString = category.toString().toLowerCase();
    const runs = this.runs.filter(r =>
      (r.results ?? []).some(res => res.category === category)
    );
    if (runs.length === 0) return;
    return html`
      <h3 class="categoryHeader heading-3">
        <iron-icon
          icon="gr-icons:${iconForCategory(category)}"
          class="${catString}"
        ></iron-icon>
        ${catString}
      </h3>
      <div class="resultsTable">
        <div class="headerRow">Run</div>
        ${runs.map(run => renderRun(category, run))}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-results': GrChecksResults;
  }
}
