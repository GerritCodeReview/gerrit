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
import {css, customElement, property, PropertyValues} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {Category, CheckRun, Link, RunStatus, Tag} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {RunResult} from '../../services/checks/checks-model';
import {
  hasCompleted,
  hasCompletedWithoutResults,
  iconForCategory,
  isRunning,
} from '../../services/checks/checks-util';

@customElement('gr-result-row')
class GrResultRow extends GrLitElement {
  @property()
  result?: RunResult;

  @property()
  isExpanded = false;

  @property({type: Boolean, reflect: true})
  isExpandable = false;

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: contents;
        }
        :host([isexpandable]) {
          cursor: pointer;
        }
        tr {
          border-top: 1px solid var(--border-color);
        }
        iron-icon.launch {
          color: var(--link-color);
          margin-right: var(--spacing-s);
        }
        td.iconCol {
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
        td {
          white-space: nowrap;
          padding: var(--spacing-s);
        }
        td .summary-cell {
          display: flex;
          max-width: calc(100vw - 579px);
        }
        td .summary-cell .summary {
          font-weight: var(--font-weight-bold);
          flex-shrink: 1;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        td .summary-cell .message {
          margin-left: var(--spacing-s);
          flex-grow: 1;
          /* Looks a bit stupid, but the idea is that .message shrinks first,
             and only when that has shrunken to 0, then .summary should also
             start shrinking (substantially). */
          flex-shrink: 1000000;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        td .summary-cell .tags .tag {
          color: var(--deemphasized-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
        td .summary-cell .label {
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

  update(changedProperties: PropertyValues) {
    if (changedProperties.has('result')) {
      this.isExpandable = !!this.result?.message;
    }
    super.update(changedProperties);
  }

  render() {
    if (!this.result) return '';
    return html`
      <tr class="container" @click="${this.toggleExpanded}">
        <td class="iconCol">
          <div>${this.renderIcon()}</div>
        </td>
        <td class="nameCol">
          <div><span>${this.result.checkName}</span></div>
        </td>
        <td class="summaryCol">
          <div class="summary-cell">
            ${(this.result.links ?? []).map(this.renderLink)}
            <!-- The &nbsp; is for being able to shrink a tiny amount without
                 the text itself getting shrunk with an ellipsis. -->
            <div class="summary">${this.result.summary}&nbsp;</div>
            <div class="message">
              ${this.isExpanded ? '' : this.result.message}
            </div>
            <div class="tags">
              ${(this.result.tags ?? []).map(t => this.renderTag(t))}
            </div>
            ${this.renderLabel()}
          </div>
          <gr-result-expanded
            .result="${this.result}"
            ?hidden="${!this.isExpanded}"
          ></gr-result-expanded>
        </td>
        <td class="expanderCol">
          <div
            class="show-hide"
            role="switch"
            tabindex="0"
            ?hidden="${!this.isExpandable}"
            ?aria-checked="${this.isExpanded}"
            aria-label="${this.isExpanded
              ? 'Collapse result row'
              : 'Expand result row'}"
            @click="${this.toggleExpanded}"
            @keydown="${this.toggleExpanded}"
          >
            <iron-icon
              icon="${this.isExpanded
                ? 'gr-icons:expand-less'
                : 'gr-icons:expand-more'}"
            ></iron-icon>
          </div>
        </td>
      </tr>
    `;
  }

  private toggleExpanded() {
    if (!this.isExpandable) return;
    this.isExpanded = !this.isExpanded;
  }

  renderLink(link: Link) {
    return html`
      <a href="${link.url}" target="_blank">
        <iron-icon
          aria-label="external link to details"
          class="launch"
          icon="gr-icons:launch"
        ></iron-icon>
      </a>
    `;
  }

  renderIcon() {
    if (this.result?.status !== RunStatus.RUNNING) return;
    return html`<iron-icon icon="gr-icons:timelapse"></iron-icon>`;
  }

  renderLabel() {
    const label = this.result?.labelName;
    if (!label) return;
    return html`<div class="label">${label}</div>`;
  }

  renderTag(tag: Tag) {
    return html`<div class="tag">${tag.name}</div>`;
  }
}

@customElement('gr-result-expanded')
class GrResultExpanded extends GrLitElement {
  @property()
  result?: RunResult;

  static get styles() {
    return [
      sharedStyles,
      css`
        .message {
          padding: var(--spacing-m) var(--spacing-m) var(--spacing-m) 0;
        }
      `,
    ];
  }

  render() {
    if (!this.result) return '';
    return html`
      <div class="message">
        ${this.result.message}
      </div>
    `;
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
        .noCompleted {
          margin-top: var(--spacing-l);
        }
        table.resultsTable {
          width: 100%;
          max-width: 1280px;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s);
        }
      `,
    ];
  }

  render() {
    return html`
      <div><h2 class="heading-2">Results</h2></div>
      ${this.renderNoCompleted()} ${this.renderSection(Category.ERROR)}
      ${this.renderSection(Category.WARNING)}
      ${this.renderSection(Category.INFO)} ${this.renderSuccess()}
    `;
  }

  renderNoCompleted() {
    if (this.runs.some(hasCompleted)) return;
    let text = 'No results.';
    if (this.runs.some(isRunning)) {
      text = 'Checks are running ...';
    }
    return html`<div class="noCompleted">${text}</div>`;
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
        <thead>
          <tr class="headerRow">
            <th class="iconCol"></th>
            <th class="nameCol">Run</th>
            <th class="summaryCol">Summary</th>
            <th class="expanderCol"></th>
          </tr>
        </thead>
        <tbody>
          ${runs.map(run => this.renderRun(category, run))}
        </tbody>
      </table>
    `;
  }

  renderRun(category: Category, run: CheckRun) {
    return html`${run.results
      ?.filter(result => result.category === category)
      .map(
        result =>
          html`<gr-result-row .result="${{...run, ...result}}"></gr-result-row>`
      )}`;
  }

  renderSuccess() {
    const runs = this.runs.filter(hasCompletedWithoutResults);
    if (runs.length === 0) return;
    return html`
      <h3 class="categoryHeader heading-3">
        <iron-icon
          icon="gr-icons:check-circle-outline"
          class="success"
        ></iron-icon>
        Success
      </h3>
      <table class="resultsTable">
        <tr class="headerRow">
          <th class="iconCol"></th>
          <th class="nameCol">Run</th>
          <th class="summaryCol">Summary</th>
          <th class="expanderCol"></th>
        </tr>
        ${runs.map(run => this.renderSuccessfulRun(run))}
      </table>
    `;
  }

  renderSuccessfulRun(run: CheckRun) {
    const adaptedRun: RunResult = {
      category: Category.INFO, // will not be used, but is required
      summary: run.statusDescription ?? 'Completed without results.',
      ...run,
    };
    return html`<gr-result-row .result="${adaptedRun}"></gr-result-row>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-result-row': GrResultRow;
    'gr-result-expanded': GrResultExpanded;
    'gr-checks-results': GrChecksResults;
  }
}
