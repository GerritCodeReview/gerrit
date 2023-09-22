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
import {
  CommitId,
  EDIT,
  NumericChangeId,
  ParentInfo,
  PatchSetNumber,
  RepoName,
  RevisionInfo,
} from '../../../api/rest-api';
import {fontStyles} from '../../../styles/gr-font-styles';
import {branchName, shorten} from '../../../utils/patch-set-util';
import {when} from 'lit/directives/when.js';
import {createChangeUrl} from '../../../models/views/change';

@customElement('gr-revision-parents')
export class GrRevisionParents extends LitElement {
  @state() repo?: RepoName;

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
      () => this.getChangeModel().repo$,
      x => (this.repo = x)
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

  override render() {
    return html`${this.renderMessage()}${this.renderDetails()}`;
  }

  private renderMessage() {
    // For merges we are only interested in the target branch parent, which is [0].
    const parentLeft = this.baseRevision?.parents_data?.[0];
    const parentRight = this.revision?.parents_data?.[0];
    if (!parentLeft || !parentRight) return;

    const psLeft = this.baseRevision?._number;
    const psRight = this.revision?._number;
    const commitLeft = shorten(parentLeft.commit_id);
    const commitRight = shorten(parentRight.commit_id);
    const branchLeft = branchName(parentLeft.branch_name);
    const branchRight = branchName(parentRight.branch_name);
    const isMergedLeft = parentLeft.is_merged_in_target_branch;
    const isMergedRight = parentRight.is_merged_in_target_branch;
    const changeNumLeft = parentLeft.change_number;
    const changeNumRight = parentRight.change_number;
    const changePsLeft = parentLeft.patch_set_number;
    const changePsRight = parentRight.patch_set_number;

    if (commitLeft === commitRight) return;

    // Subsequently: different commit

    if (branchLeft !== branchRight) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psLeft} and ${psRight} are targeting different branches.`,
          'The diff below may not be meaningful and may even be hiding relevant changes.',
        ])}
      `;
    }

    // Subsequently: different commit, same target branch

    if (!isMergedLeft && !changeNumLeft) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psLeft} is based on a commit that neither exists in its branch, `,
          'nor is it a commit of another active change.',
        ])}
      `;
    }
    if (!isMergedRight && !changeNumRight) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psRight} is based on a commit that neither exists in its branch, `,
          'nor is it a commit of another active change.',
        ])}
      `;
    }

    // Subsequently: different commit, same target branch

    if (!changeNumLeft && !changeNumRight) {
      return html`
        ${this.renderWarning('info', [
          `The change was rebased from ${commitLeft} onto ${commitRight} ` +
            `between patchset ${psLeft} and patchset ${psRight}.`,
        ])}
      `;
    }

    // Subsequently: different commit, same target branch, at least one change info

    if (!changeNumLeft) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psLeft} is based on commit ${commitLeft} in the target branch (${branchLeft}).`,
          `Patchset ${psRight} is based on patchset ${changePsRight} of change ${changeNumRight}.`,
          'The diff below may not be meaningful and may even be hiding relevant changes.',
        ])}
      `;
    }
    if (!changeNumRight) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psLeft} is based on patchset ${changePsLeft} of change ${changeNumLeft}.`,
          `Patchset ${psRight} is based on commit ${commitRight} in the target branch (${branchRight}).`,
          'The diff below may not be meaningful and may even be hiding relevant changes.',
        ])}
      `;
    }

    // Subsequently: different commit, same target branch, both parents have change info

    if (changeNumLeft !== changeNumRight) {
      return html`
        ${this.renderWarning('warning', [
          `Patchset ${psLeft} and ${psRight} are based on different changes.`,
          `Patchset ${psLeft} is based on patchset ${changePsLeft} of change ${changeNumLeft}.`,
          `Patchset ${psRight} is based on patchset ${changePsRight} of change ${changeNumRight}.`,
          'The diff below may not be meaningful and may even be hiding relevant changes.',
        ])}
      `;
    }

    // Subsequently: different commit, same target branch, both parents are patchsets of the same change

    if (psLeft !== psRight) {
      return html`
        ${this.renderWarning('info', [
          `This change is based on another change ${changeNumLeft} and was rebased ` +
            `from patchset ${changePsLeft} ${when(
              isMergedLeft,
              () => html` (MERGED)`
            )}` +
            `onto patchset ${changePsRight} ${when(
              isMergedRight,
              () => html` (MERGED)`
            )}` +
            `between patchset ${psLeft} and ${psRight}.`,
        ])}
      `;
    }

    // different commit, same branch, both parents are patchsets of the same change
    // same patchset => same commit id, which is a contradiction, thus this case is unexpected
    return html`
      ${this.renderWarning('warning', [
        `Unexpected base commits of patchset ${psLeft} and ${psRight} detected.`,
        'Please report a bug.',
      ])}
    `;
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

  private renderDetails() {
    if (!this.showDetails) return;
    if (!this.baseRevision || !this.revision) return;
    const parentLeft = this.baseRevision.parents_data?.[0];
    const parentRight = this.revision.parents_data?.[0];
    if (!parentRight || !parentLeft) return;

    return html`
      <div class="container">
        <div class="flex">
          ${this.renderSection(this.baseRevision, parentLeft)}
          ${this.renderSection(this.revision, parentRight)}
        </div>
      </div>
    `;
  }

  private renderCommitLink(commit?: CommitId) {
    if (!commit) return;
    return html`<gr-commit-info .commitInfo=${{commit}}></gr-commit-info>`;
  }

  private renderChangeLink(changeNum: NumericChangeId) {
    return html`
      <a href=${createChangeUrl({changeNum, repo: this.repo!})}>${changeNum}</a>
    `;
  }

  private renderPatchsetLink(
    changeNum: NumericChangeId,
    patchNum?: PatchSetNumber
  ) {
    if (!patchNum) return;
    return html`
      <a
        href=${createChangeUrl({
          changeNum,
          repo: this.repo!,
          patchNum,
        })}
        >${patchNum}</a
      >
    `;
  }

  private renderSection(revision: RevisionInfo, parent: ParentInfo) {
    const ps = revision._number;
    const commit = parent.commit_id;
    const branch = branchName(parent.branch_name);
    const isMerged = parent.is_merged_in_target_branch;
    const changeNum = parent.change_number as NumericChangeId;
    const changePs = parent.patch_set_number;

    createChangeUrl({changeNum, repo: this.repo!});

    return html`
      <div class="section">
        <h4 class="heading-4">Patchset ${ps}</h4>
        <div>Target Branch: ${branch}</div>
        <div>
          Base Commit: ${this.renderCommitLink(commit)}
          <gr-commit-info .commitInfo=${{commit}}></gr-commit-info>
        </div>
        ${when(
          !changeNum && !isMerged,
          () => html`
            <div>
              <gr-icon icon="warning"></gr-icon>
              <span
                >Warning: The base commit is not (yet?) merged into the target
                branch.</span
              >
            </div>
          `
        )}
        ${when(
          !!changeNum,
          () => html`
            <div>
              <span>The base commit is a patchset of another change.</span>
            </div>
            <div>Change Number: ${this.renderChangeLink(changeNum)}</div>
            <div>
              Patchset Number: ${this.renderPatchsetLink(changeNum, changePs)}
            </div>
          `
        )}
        ${when(
          !!changeNum && isMerged,
          () => html`
            <div>
              <span>This patchset has been merged into the target branch.</span>
            </div>
          `
        )}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-revision-parents': GrRevisionParents;
  }
}
