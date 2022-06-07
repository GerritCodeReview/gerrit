/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
