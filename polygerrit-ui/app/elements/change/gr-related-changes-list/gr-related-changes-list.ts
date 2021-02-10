/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-related-changes-list_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {ChangeStatus} from '../../../constants/constants';

import {changeIsOpen, getRevisionKey} from '../../../utils/change-util';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {customElement, observe, property} from '@polymer/decorators';
import {
  ChangeId,
  ChangeInfo,
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  RepoName,
  SubmittedTogetherInfo,
} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {pluralize} from '../../../utils/string-util';
import {ParsedChangeInfo} from '../../../types/types';

function getEmptySubmitTogetherInfo(): SubmittedTogetherInfo {
  return {changes: [], non_visible_changes: 0};
}

function isChangeInfo(
  x: ChangeInfo | RelatedChangeAndCommitInfo
): x is ChangeInfo {
  return (x as ChangeInfo)._number !== undefined;
}

@customElement('gr-related-changes-list')
export class GrRelatedChangesList extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when a new section is loaded so that the change view can determine
   * a show more button is needed, sometimes before all the sections finish
   * loading.
   *
   * @event new-section-loaded
   */

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Boolean, notify: true})
  hasParent = false;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean, reflectToAttribute: true})
  hidden = false;

  @property({type: Boolean, notify: true})
  loading?: boolean;

  @property({type: Boolean})
  mergeable?: boolean;

  @property({
    type: Array,
    computed:
      '_computeConnectedRevisions(change, patchNum, ' +
      '_relatedResponse.changes)',
  })
  _connectedRevisions?: CommitId[];

  @property({type: Object})
  _relatedResponse: RelatedChangesInfo = {changes: []};

  @property({type: Object})
  _submittedTogether?: SubmittedTogetherInfo = getEmptySubmitTogetherInfo();

  @property({type: Array})
  _conflicts: ChangeInfo[] = [];

  @property({type: Array})
  _cherryPicks: ChangeInfo[] = [];

  @property({type: Array})
  _sameTopic?: ChangeInfo[] = [];

  private readonly restApiService = appContext.restApiService;

  clear() {
    this.loading = true;
    this.hidden = true;

    this._relatedResponse = {changes: []};
    this._submittedTogether = getEmptySubmitTogetherInfo();
    this._conflicts = [];
    this._cherryPicks = [];
    this._sameTopic = [];
  }

  reload() {
    if (!this.change || !this.patchNum) {
      return Promise.resolve();
    }
    const change = this.change;
    this.loading = true;
    const promises: Array<Promise<void>> = [
      this.restApiService
        .getRelatedChanges(change._number, this.patchNum)
        .then(response => {
          if (!response) {
            throw new Error('getRelatedChanges returned undefined response');
          }
          this._relatedResponse = response;
          this._fireReloadEvent();
          this.hasParent = this._calculateHasParent(
            change.change_id,
            response.changes
          );
        }),
      this.restApiService
        .getChangesSubmittedTogether(change._number)
        .then(response => {
          this._submittedTogether = response;
          this._fireReloadEvent();
        }),
      this.restApiService
        .getChangeCherryPicks(change.project, change.change_id, change._number)
        .then(response => {
          this._cherryPicks = response || [];
          this._fireReloadEvent();
        }),
    ];

    // Get conflicts if change is open and is mergeable.
    if (changeIsOpen(change) && this.mergeable) {
      promises.push(
        this.restApiService
          .getChangeConflicts(change._number)
          .then(response => {
            // Because the server doesn't always return a response and the
            // template expects an array, always return an array.
            this._conflicts = response ? response : [];
            this._fireReloadEvent();
          })
      );
    }

    promises.push(
      this._getServerConfig().then(config => {
        if (change.topic) {
          if (!config) {
            throw new Error('_getServerConfig returned undefined ');
          }
          if (!config.change.submit_whole_topic) {
            return this.restApiService
              .getChangesWithSameTopic(change.topic, change._number)
              .then(response => {
                this._sameTopic = response;
              });
          }
        }
        this._sameTopic = [];
        return Promise.resolve();
      })
    );

    return Promise.all(promises).then(() => {
      this.loading = false;
    });
  }

  _fireReloadEvent() {
    // The listener on the change computes height of the related changes
    // section, so they have to be rendered first, and inside a dom-repeat,
    // that requires a flush.
    flush();
    this.dispatchEvent(new CustomEvent('new-section-loaded'));
  }

  /**
   * Determines whether or not the given change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, there is a parent.
   */
  _calculateHasParent(
    currentChangeId: ChangeId,
    relatedChanges: RelatedChangeAndCommitInfo[]
  ) {
    return (
      relatedChanges.length > 0 &&
      relatedChanges[relatedChanges.length - 1].change_id !== currentChangeId
    );
  }

  _getServerConfig() {
    return this.restApiService.getConfig();
  }

  _computeChangeURL(
    changeNum: NumericChangeId,
    project: RepoName,
    patchNum?: PatchSetNum
  ) {
    return GerritNav.getUrlForChangeById(changeNum, project, patchNum);
  }

  /**
   * Do the given objects describe the same change? Compares the changes by
   * their numbers.
   */
  _changesEqual(
    a: ChangeInfo | RelatedChangeAndCommitInfo,
    b: ChangeInfo | RelatedChangeAndCommitInfo
  ) {
    const aNum = this._getChangeNumber(a);
    const bNum = this._getChangeNumber(b);
    return aNum === bNum;
  }

  /**
   * Get the change number from either a ChangeInfo (such as those included in
   * SubmittedTogetherInfo responses) or get the change number from a
   * RelatedChangeAndCommitInfo (such as those included in a
   * RelatedChangesInfo response).
   */
  _getChangeNumber(change?: ChangeInfo | RelatedChangeAndCommitInfo) {
    // Default to 0 if change property is not defined.
    if (!change) return 0;

    if (isChangeInfo(change)) {
      return change._number;
    }
    return change._change_number;
  }

  _computeLinkClass(change: ParsedChangeInfo) {
    const statuses = [];
    if (change.status === ChangeStatus.ABANDONED) {
      statuses.push('strikethrough');
    }
    if (change.submittable) {
      statuses.push('submittable');
    }
    return statuses.join(' ');
  }

  _computeChangeStatusClass(change: RelatedChangeAndCommitInfo) {
    const classes = ['status'];
    if (change._revision_number !== change._current_revision_number) {
      classes.push('notCurrent');
    } else if (this._isIndirectAncestor(change)) {
      classes.push('indirectAncestor');
    } else if (change.submittable) {
      classes.push('submittable');
    } else if (change.status === ChangeStatus.NEW) {
      classes.push('hidden');
    }
    return classes.join(' ');
  }

  _computeChangeStatus(change: RelatedChangeAndCommitInfo) {
    switch (change.status) {
      case ChangeStatus.MERGED:
        return 'Merged';
      case ChangeStatus.ABANDONED:
        return 'Abandoned';
    }
    if (change._revision_number !== change._current_revision_number) {
      return 'Not current';
    } else if (this._isIndirectAncestor(change)) {
      return 'Indirect ancestor';
    } else if (change.submittable) {
      return 'Submittable';
    }
    return '';
  }

  /** @override */
  attached() {
    super.attached();
    // We listen to `new-section-loaded` events to allow plugins to trigger
    // visibility computations, if their content or visibility changed.
    this.addEventListener('new-section-loaded', () =>
      this._handleNewSectionLoaded()
    );
  }

  _handleNewSectionLoaded() {
    // A plugin sent a `new-section-loaded` event, so its visibility likely
    // changed. Hence, we update our visibility if needed.
    this._resultsChanged(
      this._relatedResponse,
      this._submittedTogether,
      this._conflicts,
      this._cherryPicks,
      this._sameTopic
    );
  }

  @observe(
    '_relatedResponse',
    '_submittedTogether',
    '_conflicts',
    '_cherryPicks',
    '_sameTopic'
  )
  _resultsChanged(
    related: RelatedChangesInfo,
    submittedTogether: SubmittedTogetherInfo | undefined,
    conflicts: ChangeInfo[],
    cherryPicks: ChangeInfo[],
    sameTopic?: ChangeInfo[]
  ) {
    if (!submittedTogether || !sameTopic) {
      return;
    }
    const submittedTogetherChangesCount =
      (submittedTogether.changes || []).length +
      (submittedTogether.non_visible_changes || 0);
    const results = [
      related && related.changes,
      // If there are either visible or non-visible changes, we need a
      // non-empty list to fire the event and set visibility.
      submittedTogetherChangesCount ? [{}] : [],
      conflicts,
      cherryPicks,
      sameTopic,
    ];
    for (let i = 0; i < results.length; i++) {
      if (results[i] && results[i].length > 0) {
        this.hidden = false;
        this.dispatchEvent(
          new CustomEvent('update', {
            composed: true,
            bubbles: false,
          })
        );
        return;
      }
    }

    this._computeHidden();
  }

  _computeHidden() {
    // None of the built-in change lists had elements. So all of them are
    // hidden. But since plugins might have injected visible content, we need
    // to check for that and stay visible if we find any such visible content.
    // (We consider plugins visible except if it's main element has the hidden
    // attribute set to true.)
    const plugins = getPluginEndpoints().getDetails('related-changes-section');
    this.hidden = !plugins.some(
      plugin =>
        !plugin.domHook ||
        plugin.domHook.getAllAttached().some(instance => !instance.hidden)
    );
  }

  _isIndirectAncestor(change: RelatedChangeAndCommitInfo) {
    return (
      this._connectedRevisions &&
      !this._connectedRevisions.includes(change.commit.commit)
    );
  }

  _computeConnectedRevisions(
    change?: ParsedChangeInfo,
    patchNum?: PatchSetNum,
    relatedChanges?: RelatedChangeAndCommitInfo[]
  ) {
    if (patchNum === undefined || relatedChanges === undefined) {
      return undefined;
    }
    if (!change) {
      return [];
    }

    const connected: CommitId[] = [];
    const changeRevision = getRevisionKey(change, patchNum);
    const commits = relatedChanges.map(c => c.commit);
    let pos = commits.length - 1;

    while (pos >= 0) {
      const commit: CommitId = commits[pos].commit;
      connected.push(commit);
      // TODO(TS): Ensure that both (commit and changeRevision) are string and use === instead
      // eslint-disable-next-line eqeqeq
      if (commit == changeRevision) {
        break;
      }
      pos--;
    }
    while (pos >= 0) {
      for (let i = 0; i < commits[pos].parents.length; i++) {
        if (connected.includes(commits[pos].parents[i].commit)) {
          connected.push(commits[pos].commit);
          break;
        }
      }
      --pos;
    }
    return connected;
  }

  _computeSubmittedTogetherClass(submittedTogether?: SubmittedTogetherInfo) {
    if (
      !submittedTogether ||
      (submittedTogether.changes.length === 0 &&
        !submittedTogether.non_visible_changes)
    ) {
      return 'hidden';
    }
    return '';
  }

  _computeNonVisibleChangesNote(n: number) {
    return `(+ ${pluralize(n, 'non-visible change')})`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-changes-list': GrRelatedChangesList;
  }
}
