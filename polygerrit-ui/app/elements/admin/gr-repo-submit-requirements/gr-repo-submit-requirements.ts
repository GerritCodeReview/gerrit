/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName, SubmitRequirementInfo} from '../../../types/common';
import {firePageError} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';

@customElement('gr-repo-submit-requirements')
export class GrRepoSubmitRequirements extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @state()
  loading = true;

  @state()
  submitRequirements?: SubmitRequirementInfo[];

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      tableStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
      `,
    ];
  }

  override render() {
    return html` <table id="list" class="genericList">
      <tbody>
        <tr class="headerRow">
          <th class="topHeader">Name</th>
          <th class="topHeader">Description</th>
          <th class="topHeader">Applicability Expression</th>
          <th class="topHeader">Submittability Expression</th>
          <th class="topHeader">Override Expression</th>
          <th
            class="topHeader"
            title="Whether override is allowed in child projects"
          >
            Allow Override
          </th>
        </tr>
      </tbody>
      <tbody id="submit-requirements">
        ${when(
          this.loading,
          () => html`<tr id="loadingContainer">
            <td>Loading...</td>
          </tr>`,
          () =>
            html` ${(this.submitRequirements ?? []).map(
              item => html`
                <tr class="table">
                  <td class="name">${item.name}</td>
                  <td class="desc">${item.description}</td>
                  <td class="applicability">
                    ${item.applicability_expression}
                  </td>
                  <td class="submittability">
                    ${item.submittability_expression}
                  </td>
                  <td class="override">${item.override_expression}</td>
                  <td class="allowOverride">
                    ${this.renderCheckmark(
                      item.allow_override_in_child_projects
                    )}
                  </td>
                </tr>
              `
            )}`
        )}
      </tbody>
    </table>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.repoChanged();
    }
  }

  private repoChanged() {
    const repo = this.repo;
    this.loading = true;
    if (!repo) {
      return Promise.resolve();
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getRepoSubmitRequirements(repo, errFn)
      .then((res?: SubmitRequirementInfo[]) => {
        if (!res) {
          return;
        }

        this.submitRequirements = res;
        this.loading = false;
      });
  }

  private renderCheckmark(check?: boolean) {
    return check ? '✓' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-submit-requirements': GrRepoSubmitRequirements;
  }
}
