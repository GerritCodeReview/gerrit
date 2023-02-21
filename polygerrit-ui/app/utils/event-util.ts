/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FetchRequest} from '../types/types';
import {
  DialogChangeEventDetail,
  SwitchTabEventDetail,
  TabState,
} from '../types/events';

export type HTMLElementEventDetailType<K extends keyof HTMLElementEventMap> =
  HTMLElementEventMap[K] extends CustomEvent<infer DT> ? DT : never;

type DocumentEventDetailType<K extends keyof DocumentEventMap> =
  DocumentEventMap[K] extends CustomEvent<infer DT> ? DT : never;

export function fire<K extends keyof DocumentEventMap>(
  target: Document | undefined,
  type: K,
  detail: DocumentEventDetailType<K>
): void;

export function fire<K extends keyof HTMLElementEventMap>(
  target: EventTarget | undefined,
  type: K,
  detail: HTMLElementEventDetailType<K>
): void;

export function fire<T>(
  target: EventTarget | undefined,
  type: string,
  detail: T
) {
  if (!target) return;
  target.dispatchEvent(
    new CustomEvent<T>(type, {
      detail,
      composed: true,
      bubbles: true,
    })
  );
}

export function fireNoBubble<K extends keyof HTMLElementEventMap, T>(
  target: EventTarget,
  type: K,
  detail: T
) {
  target.dispatchEvent(
    new CustomEvent<T>(type, {
      detail,
      composed: true,
      bubbles: false,
    })
  );
}

export function fireNoBubbleNoCompose<K extends keyof HTMLElementEventMap, T>(
  target: EventTarget,
  type: K,
  detail: T
) {
  target.dispatchEvent(
    new CustomEvent<T>(type, {
      detail,
      composed: false,
      bubbles: false,
    })
  );
}

export function fireAlert(target: EventTarget, message: string) {
  fire(target, 'show-alert', {message, showDismiss: true});
}

export function fireError(target: EventTarget, message: string) {
  fire(target, 'show-error', {message});
}

export function firePageError(response?: Response | null) {
  if (response === null) response = undefined;
  fire(document, 'page-error', {response});
}

export function fireServerError(response: Response, request?: FetchRequest) {
  fire(document, 'server-error', {
    response,
    request,
  });
}

export function fireNetworkError(error: Error) {
  fire(document, 'network-error', {error});
}

export function fireTitleChange(target: EventTarget, title: string) {
  fire(target, 'title-change', {title});
}

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export function fireDialogChange(
  target: EventTarget,
  detail: DialogChangeEventDetail
) {
  fire(target, 'dialog-change', detail);
}

export function fireIronAnnounce(target: EventTarget, text: string) {
  fire(target, 'iron-announce', {text});
}

export function fireShowTab(
  target: EventTarget,
  tab: string,
  scrollIntoView?: boolean,
  tabState?: TabState
) {
  const detail: SwitchTabEventDetail = {tab, scrollIntoView, tabState};
  fire(target, 'show-tab', detail);
}

export function fireReload(target: EventTarget, clearPatchset?: boolean) {
  fire(target, 'reload', {clearPatchset: !!clearPatchset});
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
