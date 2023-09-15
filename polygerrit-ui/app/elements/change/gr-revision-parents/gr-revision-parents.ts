/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import {changeModelToken} from '../../../models/change/change-model';
import {EDIT, RevisionInfo} from '../../../api/rest-api';
import {fontStyles} from '../../../styles/gr-font-styles';

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
      sharedStyles,
      css`
        :host {
          display: block;
        }
        div.container {
          padding: var(--spacing-m) var(--spacing-l);
          border-top: 1px solid var(--border-color);
          background-color: var(--yellow-50);
        }
        .section {
          margin-top: var(--spacing-m);
        }
        .section > div {
          margin-left: var(--spacing-xl);
        }
        .title {
          font-weight: var(--font-weight-bold);
        }
      `,
    ];
  }

  override render() {
    // TODO: Figure out what to do about multiple parents.
    const baseParent = this.baseRevision?.parents_data?.[0];
    const parent = this.revision?.parents_data?.[0];
    if (!parent || !baseParent) return;
    // TODO: Design something nicer for the various cases outlined in the design doc.
    return html`
      <div class="container">
        <h3 class="heading-3">Parent Information</h3>
        <div class="section">
          <h4 class="heading-4">Left Revision</h4>
          <div>Branch: ${baseParent.branch_name}</div>
          <div>Commit ID: ${baseParent.commit_id}</div>
          <div>Is Merged: ${baseParent.is_merged_in_target_branch}</div>
          <div>Change ID: ${baseParent.change_id}</div>
          <div>Change Number: ${baseParent.change_number}</div>
          <div>Patchset Number: ${baseParent.patch_set_number}</div>
          <div>Change Status: ${baseParent.change_status}</div>
        </div>
        <div class="section">
          <h4 class="heading-4">Right Revision</h4>
          <div>Branch: ${parent.branch_name}</div>
          <div>Commit ID: ${parent.commit_id}</div>
          <div>Is Merged: ${parent.is_merged_in_target_branch}</div>
          <div>Change ID: ${parent.change_id}</div>
          <div>Change Number: ${parent.change_number}</div>
          <div>Patchset Number: ${parent.patch_set_number}</div>
          <div>Change Status: ${parent.change_status}</div>
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
