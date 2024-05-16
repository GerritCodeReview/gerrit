/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-commit-info/gr-commit-info';
import '../../shared/gr-button/gr-button';
import {customElement, state} from 'lit/decorators.js';
import {css, html, HTMLTemplateResult, LitElement} from 'lit';
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
import {branchName} from '../../../utils/patch-set-util';
import {when} from 'lit/directives/when.js';
import {createChangeUrl} from '../../../models/views/change';
import {sharedStyles} from '../../../styles/shared-styles';
import {configModelToken} from '../../../models/config/config-model';
import {getDocUrl} from '../../../utils/url-util';

@customElement('gr-revision-parents')
export class GrRevisionParents extends LitElement {
  @state() repo?: RepoName;

  @state() revision?: RevisionInfo;

  @state() baseRevision?: RevisionInfo;

  @state() showDetails = false;

  @state() docsBaseUrl = '';

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

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
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      x => (this.docsBaseUrl = x)
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
        }
        .sections {
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
        gr-commit-info {
          display: inline-block;
        }
      `,
    ];
  }

  override render() {
    return html`${this.renderMessage()}${this.renderDetails()}`;
  }

  private renderMessage() {
    if (!this.baseRevision || !this.revision) return;
    // For merges we are only interested in the target branch parent, which is [0].
    // And for non-merges there is no more than 1 parent, so [0] is the only choice.
    const parentLeft = this.baseRevision?.parents_data?.[0];
    const parentRight = this.revision?.parents_data?.[0];
    // Note that is you diff a patchset against its base, then baseRevision will be
    // `undefined`. Thus after this line we know that we are dealing with diffs
    // of the type "patchset x vs patchset y".
    if (!parentLeft || !parentRight) return;

    const psLeft = this.baseRevision?._number;
    const psRight = this.revision?._number;
    const parentCommitLeft = parentLeft.commit_id;
    const parentCommitRight = parentRight.commit_id;
    const branchLeft = branchName(parentLeft.branch_name);
    const branchRight = branchName(parentRight.branch_name);
    const isMergedLeft = parentLeft.is_merged_in_target_branch;
    const isMergedRight = parentRight.is_merged_in_target_branch;
    const changeNumLeft = parentLeft.change_number;
    const changeNumRight = parentRight.change_number;
    const changePsLeft = parentLeft.patch_set_number;
    const changePsRight = parentRight.patch_set_number;

    if (parentCommitLeft === parentCommitRight) return;

    // Subsequently: different commit

    if (branchLeft !== branchRight) {
      return html`
        ${this.renderWarning(
          'warning',
          html`
            Patchset ${psLeft} and ${psRight} are targeting different branches.
          `
        )}
      `;
    }

    // Subsequently: different commit, same target branch

    // Such a situation is really rare and weird. You have to do something like committing to one
    // branch and then uploading to another. This warning should actually also be shown, if
    // you are not comparing PS X and PS Y, because it is generally a weird patchset state.
    const isWeirdLeft = !isMergedLeft && !changeNumLeft;
    const isWeirdRight = !isMergedRight && !changeNumRight;
    if (isWeirdLeft || isWeirdRight) {
      const weirdPs =
        isWeirdLeft && isWeirdRight
          ? `${psLeft} and ${psRight} are`
          : isWeirdLeft
          ? `${psLeft} is`
          : `${psRight} is`;
      return html`
        ${this.renderWarning(
          'warning',
          html`
            Patchset ${weirdPs} based on a commit that neither exists in its
            target branch, nor is it a commit of another active change.
          `
        )}
      `;
    }

    if (
      changeNumLeft &&
      changeNumRight &&
      changeNumLeft === changeNumRight &&
      // This check is probably redundant, because "same change and ps" should mean "same commit".
      psLeft !== psRight
    ) {
      return html`
        ${this.renderWarning(
          'info',
          html`
            The change was rebased from patchset
            ${this.renderPatchsetLink(changeNumLeft, changePsLeft)} onto
            patchset ${this.renderPatchsetLink(changeNumLeft, changePsRight)} of
            change ${this.renderChangeLink(changeNumLeft)}
            ${when(isMergedRight, () => html` (MERGED)`)}.
          `
        )}
      `;
    }

    // No additional info? Then "different commit" and "same branch" means "standard rebase".
    if (isMergedLeft && isMergedRight) {
      return html`
        ${this.renderWarning(
          'info',
          html`
            The change was rebased from
            ${this.renderCommitLink(parentCommitLeft, false)} onto
            ${this.renderCommitLink(parentCommitRight, false)}.
          `
        )}
      `;
    }

    // By now we know that we have different commit, same target branch, no weird parent,
    // and not a standard rebase. So let's spell out what the left and right side are based on.
    return this.renderWarning(
      'warning',
      html`${this.renderInfo(this.baseRevision)}<br />${this.renderInfo(
          this.revision
        )}`
    );
  }

  private renderInfo(rev: RevisionInfo) {
    const parent = rev.parents_data?.[0];
    if (!parent) return;
    const ps = rev._number;
    const isMerged = parent.is_merged_in_target_branch;
    const changeNum = parent.change_number;

    if (changeNum && !isMerged) {
      return html`
        Patchset ${ps} is based on patchset
        ${this.renderPatchsetLink(changeNum, parent.patch_set_number)} of change
        ${this.renderChangeLink(changeNum)}.
      `;
    } else {
      return html`
        Patchset ${ps} is based on commit
        ${this.renderCommitLink(parent.commit_id, false)} in the target branch
        (${branchName(parent.branch_name)}).
      `;
    }
  }

  private renderWarning(icon: string, message: HTMLTemplateResult) {
    const isWarning = icon === 'warning';
    return html`
      <div class="messageContainer ${icon}">
        <div class="icon">
          <gr-icon icon=${icon}></gr-icon>
        </div>
        <div class="text">
          <p>
            ${message}${when(
              isWarning,
              () => html`
                <br />
                The diff below may not be meaningful and may <br />
                even be hiding relevant changes.
                <a
                  href=${getDocUrl(
                    this.docsBaseUrl,
                    'user-review-ui.html#hazardous-rebases'
                  )}
                  >Learn more</a
                >
              `
            )}
          </p>
          ${when(
            isWarning,
            () => html`
              <p>
                <gr-button
                  link
                  @click=${() => (this.showDetails = !this.showDetails)}
                  >${this.showDetails ? 'Hide' : 'Show'} details</gr-button
                >
              </p>
            `
          )}
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
        <div class="sections">
          ${this.renderSection(
            this.baseRevision,
            parentLeft,
            parentLeft.change_number === parentRight.change_number
          )}
          ${this.renderSection(
            this.revision,
            parentRight,
            parentLeft.change_number === parentRight.change_number
          )}
        </div>
      </div>
    `;
  }

  private renderCommitLink(commit?: CommitId, showCopyButton = true) {
    if (!commit) return;
    return html`<gr-commit-info
      .commitInfo=${{commit}}
      .showCopyButton=${showCopyButton}
    ></gr-commit-info>`;
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

  private renderSection(
    revision: RevisionInfo,
    parent: ParentInfo,
    sameChange: boolean
  ) {
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
        <div>Target branch: ${branch}</div>
        <div>Base commit: ${this.renderCommitLink(commit)}</div>
        ${when(
          !changeNum && !isMerged,
          () => html`
            <div>
              <gr-icon icon="warning"></gr-icon>
              <span
                >Warning: The base commit is not known (aka reachable) in the
                target branch.</span
              >
            </div>
          `
        )}
        ${when(
          changeNum && (sameChange || !isMerged),
          () => html`
            <div>
              Base change: ${this.renderChangeLink(changeNum)}, patchset
              ${this.renderPatchsetLink(changeNum, changePs)}
              ${when(isMerged, () => html`(MERGED)`)}
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
