import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrCreateCommandsDialog} from '../../../../elements/change-list/gr-create-commands-dialog/gr-create-commands-dialog';

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

export class GrCreateCommandsDialogCheck extends GrCreateCommandsDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `commandsOverlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `commandsDialog`);
      el.confirmLabel = `Done`;
      el.cancelLabel = ``;
      el.confirmOnEnter = true;
      el.addEventListener('confirm', this._handleClose.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['ol'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-shell-command'] = null!;
      useVars(el);
      el.command = this._createNewCommitCommand;
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-shell-command'] = null!;
      useVars(el);
      el.command = this._amendExistingCommitCommand;
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-shell-command'] = null!;
      useVars(el);
      el.command = this._pushCommand;
    }
    {
      const el: HTMLElementTagNameMap['li'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
  }
}

