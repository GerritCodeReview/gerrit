/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {addShortcut, Binding} from '../../utils/dom-util';

interface ShortcutListener {
  binding: Binding;
  listener: (e: KeyboardEvent) => void;
}

type Cleanup = () => void;

export class ShortcutController implements ReactiveController {
  private readonly listeners: ShortcutListener[] = [];

  private cleanups: Cleanup[] = [];

  constructor(private readonly host: ReactiveControllerHost & HTMLElement) {}

  addLocal(binding: Binding, listener: (e: KeyboardEvent) => void) {
    this.listeners.push({binding, listener});
  }

  hostConnected() {
    for (const {binding, listener} of this.listeners) {
      const cleanup = addShortcut(this.host, binding, listener);
      this.cleanups.push(cleanup);
    }
  }

  hostDisconnected() {
    for (const cleanup of this.cleanups) {
      cleanup();
    }
    this.cleanups = [];
  }
}
