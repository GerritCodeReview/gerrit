/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {changeModelToken} from '../../../models/change/change-model';
import {EDIT, RevisionInfo} from '../../../api/rest-api';
import {fontStyles} from '../../../styles/gr-font-styles';
import {branchName, shorten} from '../../../utils/patch-set-util';
import {when} from 'lit/directives/when.js';

@customElement('gr-revision-parents')
export class GrRevisionParents extends LitElement {
  @state() revision?: RevisionInfo;

  @state() baseRevision?: RevisionInfo;

  private readonly getChangeModel = resolve(this, changeModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().revision$,
      x => {
        if (x?._number === EDIT) x = undefined;
        this.revision = x as RevisionInfo | undefined;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().baseRevision$,
      x => (this.baseRevision = x)
    );
  }

  static override get styles() {
    return [
      fontStyles,
      css`
        :host {
          display: block;
        }
        div.container {
          padding: var(--spacing-m) var(--spacing-l);
          border-top: 1px solid var(--border-color);
          background-color: var(--yellow-50);
        }
        .flex {
          display: flex;
        }
        .section {
          margin-top: 0;
          padding-right: var(--spacing-xxl);
        }
        .section h4 {
          margin: 0;
        }
        .title {
          font-weight: var(--font-weight-bold);
        }
      `,
    ];
  }

  override render() {
    // TODO(revision-parents): Figure out what to do about multiple parents.
    const baseParent = this.baseRevision?.parents_data?.[0];
    const parent = this.revision?.parents_data?.[0];
    if (!parent || !baseParent) return;
    // TODO(revision-parents): Design something nicer for the various cases.
    return html`
      <div class="container">
        <div class="flex">
          <div class="section">
            <h4 class="heading-4">Patchset ${this.baseRevision?._number}</h4>
            <div>Branch: ${branchName(parent.branch_name)}</div>
            <div>Commit: ${shorten(parent.commit_id)}</div>
            <div>Is Merged: ${parent.is_merged_in_target_branch}</div>
            ${when(
              !!parent.change_number,
              () => html` <div>
                  Change ID: ${parent.change_id?.substring(0, 10)}
                </div>
                <div>Change Number: ${parent.change_number}</div>
                <div>Patchset Number: ${parent.patch_set_number}</div>
                <div>Change Status: ${parent.change_status}</div>`
            )}
          </div>
          <div class="section">
            <h4 class="heading-4">Patchset ${this.revision?._number}</h4>
            <div>Branch: ${branchName(baseParent.branch_name)}</div>
            <div>Commit: ${shorten(baseParent.commit_id)}</div>
            <div>Is Merged: ${baseParent.is_merged_in_target_branch}</div>
            ${when(
              !!baseParent.change_number,
              () => html`<div>
                  Change ID: ${baseParent.change_id?.substring(0, 10)}
                </div>
                <div>Change Number: ${baseParent.change_number}</div>
                <div>Patchset Number: ${baseParent.patch_set_number}</div>
                <div>Change Status: ${baseParent.change_status}</div>`
            )}
          </div>
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-revision-parents': GrRevisionParents;
  }
}
