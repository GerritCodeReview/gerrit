/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import { html, LitElement } from "lit";
import { customElement, property } from "lit/decorators.js";


 @customElement('gr-mentions-chip')
 export class GrMentionsChip extends LitElement {
    @property({type: String, attribute: 'email'})
    email?: string;  

    constructor() {
        super();
        console.log(this.email)
    }

    override render() {
      return html `Email: ${this.email ?? ''}`
    }
 }
 
 declare global {
    interface HTMLElementTagNameMap {
      'gr-mentions-chip': GrMentionsChip;
    }
  }
  