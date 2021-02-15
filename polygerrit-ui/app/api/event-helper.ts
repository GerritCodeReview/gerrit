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
export type UnsubscribeCallback = () => void;

export interface EventHelperPluginApi {
  /**
   * Add a callback to arbitrary event.
   * The callback may return false to prevent event bubbling.
   */
  on(event: string, callback: (event: Event) => boolean): UnsubscribeCallback;

  /**
   * Alias for @see onClick
   */
  onTap(callback: (event: Event) => boolean): UnsubscribeCallback;

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   */
  onClick(callback: (event: Event) => boolean): UnsubscribeCallback;

  /**
   * Alias for @see captureClick
   */
  captureTap(callback: (event: Event) => boolean): UnsubscribeCallback;

  /**
   * Add a callback to element click or touch ahead of normal flow.
   * Callback is installed on parent during capture phase.
   * https://www.w3.org/TR/DOM-Level-3-Events/#event-flow
   * The callback may return false to cancel regular event listeners.
   */
  captureClick(callback: (event: Event) => boolean): UnsubscribeCallback;
}
