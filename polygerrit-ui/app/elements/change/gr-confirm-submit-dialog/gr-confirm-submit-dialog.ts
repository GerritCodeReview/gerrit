/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '@polymer/iron-icon/iron-icon';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-dialog/gr-dialog';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../../styles/shared-styles';
import '../gr-thread-list/gr-thread-list';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-submit-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {ChangeInfo, ActionInfo} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {pluralize} from '../../../utils/string-util';
import {CommentThread, isUnresolved} from '../../../utils/comment-util';

export interface GrConfirmSubmitDialog {
  $: {
    dialog: GrDialog;
  };
}
@customElement('gr-confirm-submit-dialog')
export class GrConfirmSubmitDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  action?: ActionInfo;

  @property({type: Array})
  commentThreads?: CommentThread[] = [];

  @property({type: Boolean})
  _initialised = false;

  init() {
    this._initialised = true;
  }

  resetFocus() {
    this.$.dialog.resetFocus();
  }

  _computeHasChangeEdit(change?: ChangeInfo) {
    return (
      !!change &&
      !!change.revisions &&
      Object.values(change.revisions).some(rev => rev._number === 'edit')
    );
  }

  _computeUnresolvedThreads(commentThreads?: CommentThread[]) {
    if (!commentThreads) return [];
    return commentThreads.filter(thread => isUnresolved(thread));
  }

  _computeUnresolvedCommentsWarning(change: ChangeInfo) {
    const unresolvedCount = change.unresolved_comment_count;
    if (!unresolvedCount) throw new Error('unresolved comments undefined or 0');
    return `Heads Up! ${pluralize(unresolvedCount, 'unresolved comment')}.`;
  }

  _handleConfirmTap(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
  }

  _handleCancelTap(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {bubbles: false}));
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-submit-dialog': GrConfirmSubmitDialog;
  }
}
