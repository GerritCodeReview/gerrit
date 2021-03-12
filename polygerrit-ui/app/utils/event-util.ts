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
import {
  DialogChangeEventDetail,
  EventType,
  NetworkErrorEventDetail,
  PageErrorEventDetail,
  ServerErrorEventDetail,
  ShowAlertEventDetail,
  SwitchTabEventDetail,
  TabState,
  ThreadListModifiedDetail,
  TitleChangeEventDetail,
} from '../types/events';

export function fireEvent(target: EventTarget, type: string) {
  target.dispatchEvent(
    new CustomEvent(type, {
      composed: true,
      bubbles: true,
    })
  );
}

export function fire<T>(target: EventTarget, type: string, detail: T) {
  target.dispatchEvent(
    new CustomEvent<T>(type, {
      detail,
      composed: true,
      bubbles: true,
    })
  );
}

export function fireAlert(target: EventTarget, message: string) {
  fire<ShowAlertEventDetail>(target, EventType.SHOW_ALERT, {message});
}

export function firePageError(response?: Response | null) {
  if (response === null) response = undefined;
  fire<PageErrorEventDetail>(document, EventType.PAGE_ERROR, {response});
}

export function fireServerError(response: Response, request?: FetchRequest) {
  fire<ServerErrorEventDetail>(document, EventType.SERVER_ERROR, {
    response,
    request,
  });
}

export function fireNetworkError(error: Error) {
  fire<NetworkErrorEventDetail>(document, EventType.NETWORK_ERROR, {error});
}

export function fireTitleChange(target: EventTarget, title: string) {
  fire<TitleChangeEventDetail>(target, EventType.TITLE_CHANGE, {title});
}

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export function fireDialogChange(
  target: EventTarget,
  detail: DialogChangeEventDetail
) {
  fire<DialogChangeEventDetail>(target, EventType.DIALOG_CHANGE, detail);
}

export function fireThreadListModifiedEvent(
  target: EventTarget,
  rootId: UrlEncodedCommentId,
  path: string
) {
  fire<ThreadListModifiedDetail>(target, EventType.THREAD_LIST_MODIFIED, {
    rootId,
    path,
  });
}

export function fireShowPrimaryTab(
  target: EventTarget,
  tab: string,
  scrollIntoView?: boolean,
  tabState?: TabState
) {
  const detail: SwitchTabEventDetail = {tab, scrollIntoView, tabState};
  fire<SwitchTabEventDetail>(target, EventType.SHOW_PRIMARY_TAB, detail);
}

export function waitForEventOnce<K extends keyof HTMLElementEventMap>(
  el: EventTarget,
  eventName: K
): Promise<HTMLElementEventMap[K]> {
  return new Promise<HTMLElementEventMap[K]>(resolve => {
    const callback = (event: HTMLElementEventMap[K]) => {
      resolve(event);
    };
    el.addEventListener(eventName, callback as EventListener, {once: true});
  });
}
