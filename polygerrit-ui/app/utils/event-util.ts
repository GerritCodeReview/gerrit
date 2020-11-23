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

export enum EventType {
  SHOW_ALERT = 'show-alert',
  PAGE_ERROR = 'page-error',
  TITLE_CHANGE = 'title-change',
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

export function firePageError(target: EventTarget, response?: Response | null) {
  target.dispatchEvent(
    new CustomEvent(EventType.PAGE_ERROR, {
      detail: {response},
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
