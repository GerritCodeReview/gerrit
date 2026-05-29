% An example source file to be tested.

% Add common rules missing in Prolog Cafe.
memberchk(X, [H|T]) :-
  (X = H) -> true ; memberchk(X, T).

% A rule that can succeed/backtrack multiple times.
super_users(1001).
super_users(1002).

% Deterministic rule that pass/fail only once.
is_super_user(X) :- memberchk(X, [1001, 1002]).

% Another rule that can pass 5 times.
multi_users(101).
multi_users(102).
multi_users(103).
multi_users(104).
multi_users(105).

% Okay, single deterministic fact.
single_user(abc).

% Wrap calls to gerrit repository, to be redefined in tests.
change_owner(X) :- gerrit:change_owner(X).

% To test is_owner without gerrit:change_owner,
% we should redefine change_owner.
is_owner(X) :- change_owner(X).
