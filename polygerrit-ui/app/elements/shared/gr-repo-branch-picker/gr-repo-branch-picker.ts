/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-labeled-autocomplete/gr-labeled-autocomplete';
import '../gr-icon/gr-icon';
import {singleDecodeURL} from '../../../utils/url-util';
import {AutocompleteQuery} from '../gr-autocomplete/gr-autocomplete';
import {
  BranchName,
  RepoName,
  ProjectInfoWithName,
  BranchInfo,
} from '../../../types/common';
import {GrLabeledAutocomplete} from '../gr-labeled-autocomplete/gr-labeled-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state, query} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {BindValueChangeEvent} from '../../../types/events';
import {throwingErrorCallback} from '../gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

const SUGGESTIONS_LIMIT = 15;
const REF_PREFIX = 'refs/heads/';

@customElement('gr-repo-branch-picker')
export class GrRepoBranchPicker extends LitElement {
  @query('#repoInput') protected repoInput?: GrLabeledAutocomplete;

  @query('#branchInput') protected branchInput?: GrLabeledAutocomplete;

  @property({type: String})
  repo?: RepoName;

  @property({type: String})
  branch?: BranchName;

  @state() private branchDisabled = false;

  private readonly query: AutocompleteQuery = () => Promise.resolve([]);

  private readonly repoQuery: AutocompleteQuery = () => Promise.resolve([]);

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.query = input => this.getRepoBranchesSuggestions(input);
    this.repoQuery = input => this.getRepoSuggestions(input);
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.repo) {
      assertIsDefined(this.repoInput, 'repoInput');
      this.repoInput.setText(this.repo);
    }
    this.branchDisabled = !this.repo;
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        gr-labeled-autocomplete {
          display: inline-block;
        }
        gr-icon {
          margin-bottom: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    return html`
      <div>
        <gr-labeled-autocomplete
          id="repoInput"
          label="Repository"
          placeholder="Select repo"
          .query=${this.repoQuery}
          @commit=${(e: CustomEvent<{value: string}>) => {
            this.repoCommitted(e);
          }}
        >
        </gr-labeled-autocomplete>
        <gr-icon icon="chevron_right"></gr-icon>
        <gr-labeled-autocomplete
          id="branchInput"
          label="Branch"
          placeholder="Select branch"
          ?disabled=${this.branchDisabled}
          .query=${this.query}
          @commit=${(e: CustomEvent<{value: string}>) => {
            this.branchCommitted(e);
          }}
        >
        </gr-labeled-autocomplete>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.repoChanged();
    }
  }

  // private but used in test
  getRepoBranchesSuggestions(input: string) {
    if (!this.repo) {
      return Promise.resolve([]);
    }
    if (input.startsWith(REF_PREFIX)) {
      input = input.substring(REF_PREFIX.length);
    }
    return this.restApiService
      .getRepoBranches(
        input,
        this.repo,
        SUGGESTIONS_LIMIT,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(res => this.branchResponseToSuggestions(res));
  }

  private branchResponseToSuggestions(res: BranchInfo[] | undefined) {
    if (!res) return [];
    return res
      .filter(branchInfo => branchInfo.ref !== 'HEAD')
      .map(branchInfo => {
        let branch;
        if (branchInfo.ref.startsWith(REF_PREFIX)) {
          branch = branchInfo.ref.substring(REF_PREFIX.length);
        } else {
          branch = branchInfo.ref;
        }
        return {name: branch, value: branch};
      });
  }

  // private but used in test
  getRepoSuggestions(input: string) {
    return this.restApiService
      .getRepos(
        input,
        SUGGESTIONS_LIMIT,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(res => this.repoResponseToSuggestions(res));
  }

  private repoResponseToSuggestions(res: ProjectInfoWithName[] | undefined) {
    if (!res) return [];
    return res.map(repo => {
      return {
        name: repo.name,
        value: singleDecodeURL(repo.id),
      };
    });
  }

  private repoCommitted(e: CustomEvent<{value: string}>) {
    this.repo = e.detail.value as RepoName;
    fire(this, 'repo-changed', {value: e.detail.value});
  }

  private branchCommitted(e: CustomEvent<{value: string}>) {
    this.branch = e.detail.value as BranchName;
    fire(this, 'branch-changed', {value: e.detail.value});
  }

  private repoChanged() {
    assertIsDefined(this.branchInput, 'branchInput');
    this.branchInput.clear();
    this.branchDisabled = !this.repo;
  }
}

declare global {
  interface HTMLElementEventMap {
    'branch-changed': BindValueChangeEvent;
    'repo-changed': BindValueChangeEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-repo-branch-picker': GrRepoBranchPicker;
  }
}
