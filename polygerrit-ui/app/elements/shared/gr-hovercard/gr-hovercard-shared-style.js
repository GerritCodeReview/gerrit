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

/** The shared styles for all hover cards. */
const GrHoverCardSharedStyle = document.createElement('dom-module');
GrHoverCardSharedStyle.innerHTML =
  `<template>
    <style include="shared-styles">
      :host {
        box-sizing: border-box;
        opacity: 0;
        position: absolute;
        transition: opacity 200ms;
        visibility: hidden;
        z-index: 200;
      }
      :host(.hovered) {
        visibility: visible;
        opacity: 1;
      }
      /* You have to use a <div class="container"> in your hovercard in order
         to pick up this consistent styling. */
      #container {
        background: var(--dialog-background-color);
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-5);
      }
    </style>
  </template>`;

GrHoverCardSharedStyle.register('gr-hovercard-shared-style');
