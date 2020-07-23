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

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

/** The shared styles for all hover cards. */
const GrHoverCardSharedStyle = document.createElement('dom-module');
GrHoverCardSharedStyle.innerHTML = `<template>
    <style include="shared-styles">
      :host {
        position: absolute;
        display: none;
        z-index: 200;
        max-width: 600px;
      }
      :host(.hovered) {
        display: block;
      }
      :host(.hide) {
        visibility: hidden;
      }
      /* You have to use a <div class="container"> in your hovercard in order
         to pick up this consistent styling. */
      #container {
        background: var(--dialog-background-color);
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-5);
      }
    </style>
  </template>`;

GrHoverCardSharedStyle.register('gr-hovercard-shared-style');
