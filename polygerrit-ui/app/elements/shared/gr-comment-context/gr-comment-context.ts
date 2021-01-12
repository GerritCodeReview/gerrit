/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {customElement, property} from '@polymer/decorators';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {htmlTemplate} from './gr-comment-context_html';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {UIComment} from '../../../utils/comment-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-context': GrCommentContext;
  }
}

@customElement('gr-comment-context')
export class GrCommentContext extends LegacyElementMixin(
  KeyboardShortcutMixin(TooltipMixin(GestureEventListeners(PolymerElement)))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  comments: UIComment[] = [];

  _getContextLine(comments: UIComment[]) {
    if (comments.length && comments[0].context_lines)
      return comments[0].context_lines;
    return '';
  }
}
