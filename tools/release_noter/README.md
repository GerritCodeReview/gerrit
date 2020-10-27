# Release Noter

## Setup

```bash
make setup
make deploy
```

* The `deploy` target may not succeed if `Pipfile.lock` is out of date.
  * The `setup` target can be used first in such a case.
* Using `make all` will run the `deploy` target, among the other key targets.

## Warning

The make `clean` target removes any previously made `release_noter*.md` file(s).

Running `release_noter.py` multiple times without cleaning creates the next `N`
`release_noter-N.md` file, without overwriting the previous one(s).

## Usage

```bash
make help
```

* The resulting `release_noter*.md` file(s) can be edited then copied over to the `homepage`.
  * The markdown file name should be `x.y.md`, where `x.y` is the major release version.
  * Alternatively, an existing `x.y.md` can be edited with `release_noter*.md` snippets.

## Testing

```bash
make test
make test COMMITS=100
```

This target will use the `-l` option, which takes more time as `COMMITS` increases.

## Examples

```bash
pipenv run python release_noter.py v3.2.3..HEAD
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0 -c
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0 -l
```

## Coding

```bash
make black
make flake
```
