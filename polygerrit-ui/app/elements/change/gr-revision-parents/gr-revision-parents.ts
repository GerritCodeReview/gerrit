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

  @state() showDetails = false;

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
        .messageContainer {
          display: flex;
          padding: var(--spacing-m) var(--spacing-l);
          border-top: 1px solid var(--border-color);
        }
        .messageContainer.info {
          background-color: var(--info-background);
        }
        .messageContainer.warning {
          background-color: var(--warning-background);
        }
        .messageContainer gr-icon {
          margin-right: var(--spacing-m);
        }
        .messageContainer.info gr-icon {
          color: var(--info-foreground);
        }
        .messageContainer.warning gr-icon {
          color: var(--warning-foreground);
        }
        .messageContainer .text {
          max-width: 600px;
        }
        .messageContainer .text p {
          margin: 0;
        }
        .messageContainer .text gr-button {
          margin-left: -4px;
        }
      `,
    ];
  }

  private renderWarning(icon: string, messages: string[]) {
    return html`
      <div class="messageContainer ${icon}">
        <div class="icon">
          <gr-icon icon=${icon}></gr-icon>
        </div>
        <div class="text">
          ${messages.map(msg => html`<p>${msg}</p>`)}
          <p>
            <gr-button
              link
              @click=${() => (this.showDetails = !this.showDetails)}
              >${this.showDetails ? 'Hide' : 'Show'} details</gr-button
            >
          </p>
        </div>
      </div>
    `;
  }

  override render() {
    // TODO(revision-parents): Figure out what to do about multiple parents.
    const parentLeft = this.baseRevision?.parents_data?.[0];
    const parentRight = this.revision?.parents_data?.[0];
    const psLeft = this.baseRevision?._number;
    const psRight = this.revision?._number;
    const commitLeft = shorten(parentLeft?.commit_id);
    const commitRight = shorten(parentRight?.commit_id);
    const branchLeft = branchName(parentLeft?.branch_name);
    const branchRight = branchName(parentRight?.branch_name);
    const changeNumLeft = parentLeft?.change_number;
    const changeNumRight = parentRight?.change_number;
    const changePsLeft = parentLeft?.patch_set_number;
    const changePsRight = parentRight?.patch_set_number;
    const changeStatusLeft = parentLeft?.change_status;
    const changeStatusRight = parentRight?.change_status;

    if (!parentRight || !parentLeft) return;
    // TODO(revision-parents): Design something nicer for the various cases.
    return html`
      ${this.renderWarning('warning', [
        `Patchset ${psLeft} and ${psRight} are based on commits in different branches.`,
        `Patchset ${psLeft} is based on commit ${commitLeft} in branch ${branchLeft}.`,
        `Patchset ${psRight} is based on commit ${commitRight} in branch ${branchRight}.`,
        'The diff below may not be meaningful and may even be hiding relevant changes.',
      ])}
      ${this.renderWarning('info', [
        `The change was rebased from ${commitLeft} onto ${commitRight} ` +
          `between patchset ${psLeft} and patchset ${psRight}.`,
      ])}
      ${this.renderWarning('info', [
        `This change is based on another change ${changeNumLeft} and was rebased ` +
          `from patchset ${changePsLeft} (${changeStatusLeft}) ` +
          `onto patchset ${changePsRight} (${changeStatusRight}) ` +
          `between patchset ${psLeft} and ${psRight}.`,
      ])}
      ${this.renderWarning('warning', [
        `Patchset ${psLeft} and ${psRight} are based on different changes.`,
        `Patchset ${psLeft} is based on change ${changeNumLeft} (patchset ${changePsLeft}).`,
        `Patchset ${psRight} is based on change ${changeNumRight} (patchset ${changePsRight}).`,
        'The diff below may not be meaningful and may even be hiding relevant changes.',
      ])}
      ${this.renderWarning('warning', [
        `Patchset ${psLeft} is based on change ${changeNumLeft} (patchset ${changePsLeft}).`,
        `Patchset ${psRight} is based on commit ${commitRight} in the target branch (${branchRight}).`,
        'The diff below may not be meaningful and may even be hiding relevant changes.',
      ])}
      ${when(
        this.showDetails,
        () => html`
          <div class="container">
            <div class="flex">
              <div class="section">
                <h4 class="heading-4">
                  Base of Patchset ${this.baseRevision?._number}
                </h4>
                <div>Branch: ${branchName(parentRight.branch_name)}</div>
                <div>Commit: ${shorten(parentRight.commit_id)}</div>
                <div>Is Merged: ${parentRight.is_merged_in_target_branch}</div>
                ${when(
                  !!parentRight.change_number,
                  () => html` <div>
                      Change ID: ${parentRight.change_id?.substring(0, 10)}
                    </div>
                    <div>Change Number: ${parentRight.change_number}</div>
                    <div>Patchset Number: ${parentRight.patch_set_number}</div>
                    <div>Change Status: ${parentRight.change_status}</div>`
                )}
              </div>
              <div class="section">
                <h4 class="heading-4">
                  Base of Patchset ${this.revision?._number}
                </h4>
                <div>Branch: ${branchName(parentLeft.branch_name)}</div>
                <div>Commit: ${shorten(parentLeft.commit_id)}</div>
                <div>Is Merged: ${parentLeft.is_merged_in_target_branch}</div>
                ${when(
                  !!parentLeft.change_number,
                  () => html`<div>
                      Change ID: ${parentLeft.change_id?.substring(0, 10)}
                    </div>
                    <div>Change Number: ${parentLeft.change_number}</div>
                    <div>Patchset Number: ${parentLeft.patch_set_number}</div>
                    <div>Change Status: ${parentLeft.change_status}</div>`
                )}
              </div>
            </div>
          </div>
        `
      )}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-revision-parents': GrRevisionParents;
  }
}
