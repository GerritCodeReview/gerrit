/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {UrlEncodedCommentId} from '../types/common';
import {FetchRequest} from '../types/types';
import {DialogChangeEventDetail, TabState} from '../types/events';

export enum EventType {
  SHOW_ALERT = 'show-alert',
  PAGE_ERROR = 'page-error',
  SERVER_ERROR = 'server-error',
  NETWORK_ERROR = 'network-error',
  TITLE_CHANGE = 'title-change',
  THREAD_LIST_MODIFIED = 'thread-list-modified',
  DIALOG_CHANGE = 'dialog-change',
  SHOW_PRIMARY_TAB = 'show-primary-tab',
}

export function fireEvent(target: EventTarget, type: string) {
  target.dispatchEvent(
    new CustomEvent(type, {
      composed: true,
      bubbles: true,
    })
  );
}

export function fireAlert(target: EventTarget, message: string) {
  target.dispatchEvent(
    new CustomEvent(EventType.SHOW_ALERT, {
      detail: {message},
      composed: true,
      bubbles: true,
    })
  );
}

export function firePageError(response?: Response | null) {
  document.dispatchEvent(
    new CustomEvent(EventType.PAGE_ERROR, {
      detail: {response},
      composed: true,
      bubbles: true,
    })
  );
}

export function fireServerError(response: Response, request?: FetchRequest) {
  document.dispatchEvent(
    new CustomEvent(EventType.SERVER_ERROR, {
      detail: {response, request},
      composed: true,
      bubbles: true,
    })
  );
}

export function fireNetworkError(error: Error) {
  document.dispatchEvent(
    new CustomEvent(EventType.NETWORK_ERROR, {
      detail: {error},
      composed: true,
      bubbles: true,
    })
  );
}

export function fireTitleChange(target: EventTarget, title: string) {
  target.dispatchEvent(
    new CustomEvent(EventType.TITLE_CHANGE, {
      detail: {title},
      composed: true,
      bubbles: true,
    })
  );
}

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export function fireDialogChange(
  target: EventTarget,
  detail: DialogChangeEventDetail
) {
  target.dispatchEvent(
    new CustomEvent(EventType.DIALOG_CHANGE, {
      detail,
      composed: true,
      bubbles: true,
    })
  );
}

export function fireThreadListModifiedEvent(
  target: EventTarget,
  rootId: UrlEncodedCommentId,
  path: string
) {
  target.dispatchEvent(
    new CustomEvent(EventType.THREAD_LIST_MODIFIED, {
      detail: {rootId, path},
      composed: true,
      bubbles: true,
    })
  );
}

export function fireShowPrimaryTab(
  target: EventTarget,
  tab: string,
  scrollIntoView?: boolean,
  tabState?: TabState
) {
  target.dispatchEvent(
    new CustomEvent(EventType.SHOW_PRIMARY_TAB, {
      detail: {tab, scrollIntoView, tabState},
      composed: true,
      bubbles: true,
    })
  );
}
