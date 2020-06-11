import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrEditableContent} from '../../../../elements/shared/gr-editable-content/gr-editable-content';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrEditableContentCheck extends GrEditableContent
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `viewer`);
      el.setAttribute('hidden', `${this.editing}`);
      el.setAttribute('collapsed', `${this._computeCommitMessageCollapsed(this._commitCollapsed, this.commitCollapsible)}`);
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `editor`);
      el.setAttribute('hidden', `${!this.editing}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-autogrow-textarea'] = null!;
      useVars(el);
      el.bindValue = this._newContent;
      this._newContent = el.bindValue;
      el.disabled = this.disabled;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `show-all-container`);
      el.setAttribute('hidden', `${this._hideShowAllContainer}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('class', `show-all-button`);
      el.addEventListener('click', this._toggleCommitCollapsed.bind(this));
      el.setAttribute('hidden', `${this._hideShowAllButton}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this._commitCollapsed}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this._commitCollapsed}`);
    }
    setTextContent(`
      ${this._computeCollapseText(this._commitCollapsed)}
    `);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('class', `edit-commit-message`);
      el.addEventListener('click', this._handleEditCommitMessage.bind(this));
      el.setAttribute('hidden', `${this.hideEditCommitMessage}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `editButtons`);
      el.setAttribute('hidden', `${!this.editing}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('class', `cancel-button`);
      el.addEventListener('click', this._handleCancel.bind(this));
      el.disabled = this.disabled;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('class', `save-button`);
      el.addEventListener('click', this._handleSave.bind(this));
      el.disabled = this._saveDisabled;
    }
  }
}

