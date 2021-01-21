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
  CheckRun,
  Link,
  RunStatus,
  Tag,
} from '../plugins/gr-checks-api/gr-checks-api-types';
import {sharedStyles} from '../../styles/shared-styles';
import {assertNever} from '../../utils/common-util';
import {RunResult} from '../../services/checks/checks-model';
import {hasCompletedWithoutResults} from '../../services/checks/checks-util';

function renderSuccessfulRun(run: CheckRun) {
  const adaptedRun: RunResult = {
    category: Category.INFO, // will not be used, but is required
    summary: run.statusDescription ?? 'Completed without results.',
    ...run,
  };
  return renderResult(adaptedRun);
}

function renderRun(category: Category, run: CheckRun) {
  return html`${run.results
    ?.filter(result => result.category === category)
    .map(result => renderResult({...run, ...result}))}`;
}

function renderResult(result: RunResult) {
  return html`<tr class="resultRow">
    <td class="iconCol">
      <div>${renderIcon(result)}</div>
    </td>
    <td class="nameCol">
      <div><span>${result.checkName}</span></div>
    </td>
    <td class="summaryCol">
      <div class="summary-cell">
        ${(result.links ?? []).map(renderLink)}
        <!-- The &nbsp; is for being able to shrink a tiny amount without
             the text itself getting shrunk with an ellipsis. -->
        <div class="summary">${result.summary}&nbsp;</div>
        <div class="message">${result.message}</div>
        <div class="tags">${(result.tags ?? []).map(renderTag)}</div>
        ${renderLabel(result.labelName)}
      </div>
    </td>
    <td class="expanderCol">
      <div><iron-icon icon="gr-icons:expand-more"></iron-icon></div>
    </td>
  </tr>`;
}

function renderLink(link: Link) {
  return html`
    <a href="${link.url}" target="_blank">
      <iron-icon class="launch" icon="gr-icons:launch"></iron-icon>
    </a>
  `;
}

function renderIcon(result: RunResult) {
  if (result.status !== RunStatus.RUNNING) return;
  return html`<iron-icon icon="gr-icons:timelapse"></iron-icon>`;
}

function renderLabel(label?: string) {
  if (!label) return;
  return html`<div class="label">${label}</div>`;
}

function renderTag(tag: Tag) {
  return html`<div class="tag">${tag.name}</div>`;
}

export function iconForCategory(category: Category) {
  switch (category) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info-outline';
    case Category.WARNING:
      return 'warning';
    default:
      assertNever(category, `Unsupported category: ${category}`);
  }
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
          margin-top: var(--spacing-xxl);
          margin-left: var(--spacing-l);
          text-transform: capitalize;
        }
        .categoryHeader iron-icon {
          position: relative;
          top: 1px;
        }
        .categoryHeader iron-icon.error {
          color: var(--error-foreground);
        }
        .categoryHeader iron-icon.warning {
          color: var(--warning-foreground);
        }
        .categoryHeader iron-icon.info {
          color: var(--info-foreground);
        }
        .categoryHeader iron-icon.success {
          color: var(--success-foreground);
        }
        iron-icon.launch {
          color: var(--link-color);
          margin-right: var(--spacing-s);
        }
        table.resultsTable {
          width: 100%;
          max-width: 1280px;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.resultRow td.iconCol {
          padding-left: var(--spacing-l);
          padding-right: var(--spacing-m);
        }
        .iconCol div {
          width: 20px;
        }
        .nameCol div {
          width: 165px;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .summaryCol {
          /* Forces this column to get the remaining space that is left over by
             the other columns. */
          width: 99%;
        }
        .expanderCol div {
          width: 20px;
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s);
        }
        tr.resultRow {
          border-top: 1px solid var(--border-color);
        }
        tr.resultRow td {
          white-space: nowrap;
          padding: var(--spacing-s);
        }
        tr.resultRow td .summary-cell {
          display: flex;
          max-width: calc(100vw - 579px);
        }
        tr.resultRow td .summary-cell .summary {
          font-weight: bold;
          flex-shrink: 1;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        tr.resultRow td .summary-cell .message {
          margin-left: var(--spacing-s);
          flex-grow: 1;
          /* Looks a bit stupid, but the idea is that .message shrinks first,
             and only when that has shrunken to 0, then .summary should also
             start shrinking (substantially). */
          flex-shrink: 1000000;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        tr.resultRow td .summary-cell .tags {
        }
        tr.resultRow td .summary-cell .tags .tag {
          color: var(--deemphasized-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
        tr.resultRow td .summary-cell .label {
          color: var(--deemphasized-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--label-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
      `,
    ];
  }

  render() {
    return html`
      <div><h2 class="heading-2">Results</h2></div>
      ${this.renderSection(Category.ERROR)}
      ${this.renderSection(Category.WARNING)}
      ${this.renderSection(Category.INFO)} ${this.renderSuccess()}
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
      <table class="resultsTable">
        <tr class="headerRow">
          <th class="iconCol"></th>
          <th class="nameCol">Run</th>
          <th class="summaryCol">Summary</th>
          <th class="expanderCol"></th>
        </tr>
        ${runs.map(run => renderRun(category, run))}
      </table>
    `;
  }

  renderSuccess() {
    const runs = this.runs.filter(hasCompletedWithoutResults);
    if (runs.length === 0) return;
    return html`
      <h3 class="categoryHeader heading-3">
        <iron-icon icon="gr-icons:check-circle" class="success"></iron-icon>
        Success
      </h3>
      <table class="resultsTable">
        <tr class="headerRow">
          <th class="iconCol"></th>
          <th class="nameCol">Run</th>
          <th class="summaryCol">Summary</th>
          <th class="expanderCol"></th>
        </tr>
        ${runs.map(run => renderSuccessfulRun(run))}
      </table>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-results': GrChecksResults;
  }
}
