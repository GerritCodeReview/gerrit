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

import {FetchRequest} from '../types/types';
import {
  DialogChangeEventDetail,
  EventType,
  SwitchTabEventDetail,
  TabState,
} from '../types/events';

export function fireEvent(target: EventTarget, type: string) {
  target.dispatchEvent(
    new CustomEvent(type, {
      composed: true,
      bubbles: true,
    })
  );
}

export type HTMLElementEventDetailType<K extends keyof HTMLElementEventMap> =
  HTMLElementEventMap[K] extends CustomEvent<infer DT>
    ? unknown extends DT
      ? never
      : DT
    : never;

type DocumentEventDetailType<K extends keyof DocumentEventMap> =
  DocumentEventMap[K] extends CustomEvent<infer DT>
    ? unknown extends DT
      ? never
      : DT
    : never;

export function fire<K extends keyof DocumentEventMap>(
  target: Document,
  type: K,
  detail: DocumentEventDetailType<K>
): void;

export function fire<K extends keyof HTMLElementEventMap>(
  target: EventTarget,
  type: K,
  detail: HTMLElementEventDetailType<K>
): void;

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
  fire(target, EventType.SHOW_ALERT, {message, showDismiss: true});
}

export function firePageError(response?: Response | null) {
  if (response === null) response = undefined;
  fire(document, EventType.PAGE_ERROR, {response});
}

export function fireServerError(response: Response, request?: FetchRequest) {
  fire(document, EventType.SERVER_ERROR, {
    response,
    request,
  });
}

export function fireNetworkError(error: Error) {
  fire(document, EventType.NETWORK_ERROR, {error});
}

export function fireTitleChange(target: EventTarget, title: string) {
  fire(target, EventType.TITLE_CHANGE, {title});
}

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export function fireDialogChange(
  target: EventTarget,
  detail: DialogChangeEventDetail
) {
  fire(target, EventType.DIALOG_CHANGE, detail);
}

export function fireIronAnnounce(target: EventTarget, text: string) {
  fire(target, EventType.IRON_ANNOUNCE, {text});
}

export function fireShowPrimaryTab(
  target: EventTarget,
  tab: string,
  scrollIntoView?: boolean,
  tabState?: TabState
) {
  const detail: SwitchTabEventDetail = {tab, scrollIntoView, tabState};
  fire(target, EventType.SHOW_PRIMARY_TAB, detail);
}

export function fireCloseFixPreview(target: EventTarget, fixApplied: boolean) {
  fire(target, EventType.CLOSE_FIX_PREVIEW, {fixApplied});
}

export function fireReload(target: EventTarget, clearPatchset?: boolean) {
  fire(target, EventType.RELOAD, {clearPatchset: !!clearPatchset});
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
