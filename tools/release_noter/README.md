# Release Noter

## Setup

The `--deploy` option is to be removed if `Pipfile.lock` is out of date.

```bash
pipenv install --dev
pipenv install --dev --deploy
```

## Usage

```bash
pipenv run python release_noter.py -h
```

## Examples

```bash
pipenv run python release_noter.py v3.2.3..HEAD
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0 -c
pipenv run python release_noter.py v3.2.3..v3.3.0-rc0 -l
```

## Coding

```bash
pipenv run black release_noter.py
pipenv run flake8 release_noter.py
```
