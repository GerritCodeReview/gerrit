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
import {Binding} from '../../utils/dom-util';
import {ShortcutsService} from '../../services/shortcuts/shortcuts-service';
import {appContext} from '../../services/app-context';

interface ShortcutListener {
  binding: Binding;
  listener: (e: KeyboardEvent) => void;
}

type Cleanup = () => void;

export class ShortcutController implements ReactiveController {
  private readonly service: ShortcutsService = appContext.shortcutsService;

  private readonly listenersLocal: ShortcutListener[] = [];

  private readonly listenersGlobal: ShortcutListener[] = [];

  private cleanups: Cleanup[] = [];

  constructor(private readonly host: ReactiveControllerHost & HTMLElement) {
    host.addController(this);
  }

  // Note that local shortcuts are *not* suppressed when the user has shortcuts
  // disabled or when the event comes from elements like <input>. So this method
  // is intended for shortcuts like ESC and Ctrl-ENTER.
  addLocal(binding: Binding, listener: (e: KeyboardEvent) => void) {
    this.listenersLocal.push({binding, listener});
  }

  addGlobal(binding: Binding, listener: (e: KeyboardEvent) => void) {
    this.listenersGlobal.push({binding, listener});
  }

  hostConnected() {
    for (const {binding, listener} of this.listenersLocal) {
      const cleanup = this.service.addShortcut(this.host, binding, listener, {
        shouldSuppress: false,
      });
      this.cleanups.push(cleanup);
    }
    for (const {binding, listener} of this.listenersGlobal) {
      const cleanup = this.service.addShortcut(
        document.body,
        binding,
        listener
      );
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
