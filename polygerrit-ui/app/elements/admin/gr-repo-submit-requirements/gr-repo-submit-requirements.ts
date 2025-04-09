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
import {customElement, property} from 'lit/decorators.js';

@customElement('gr-repo-submit-requirements')
export class GrRepoSubmitRequirements extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: Boolean})
  _loading = true;

  @property({type: Array})
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
        .loading #submit-requirements,
        #loadingContainer {
          display: none;
        }
        .loading #loadingContainer {
          display: block;
        }
      `,
    ];
  }

  override render() {
    return html` <table
      id="list"
      class="genericList ${this._computeLoadingClass(this._loading)}"
    >
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
        <tr id="loadingContainer">
          <td>Loading...</td>
        </tr>
      </tbody>
      <tbody id="submit-requirements">
        ${(this.submitRequirements ?? []).map(
          item => html`
            <tr class="table">
              <td class="name">${item.name}</td>
              <td class="desc">${item.description}</td>
              <td class="applicability">${item.applicability_expression}</td>
              <td class="submittability">${item.submittability_expression}</td>
              <td class="override">${item.override_expression}</td>
              <td class="allowOverride">
                ${this.renderBoolean(item.allow_override_in_child_projects)}
              </td>
            </tr>
          `
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
    this._loading = true;
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
        this._loading = false;
      });
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  renderBoolean(check?: boolean) {
    return check ? '✓' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-submit-requirements': GrRepoSubmitRequirements;
  }
}
