import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrAccountEntry} from '../../../../elements/shared/gr-account-entry/gr-account-entry';

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

export class GrAccountEntryCheck extends GrAccountEntry
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.setAttribute('id', `input`);
      el.borderless = this.borderless;
      el.placeholder = this.placeholder;
      el.threshold = this.suggestFrom;
      el.query = this.querySuggestions;
      el.allowNonSuggestedValues = this.allowAnyInput;
      el.addEventListener('commit', this._handleInputCommit.bind(this));
      el.clearOnCommit = true;
      el.warnUncommitted = true;
      el.text = this._inputText;
      this._inputText = el.text;
      el.verticalOffset = 24;
    }
  }
}

