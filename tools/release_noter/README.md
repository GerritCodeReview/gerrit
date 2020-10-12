# Release Noter

## Setup

```bash
pipenv install --dev
```

## Usage

```bash
pipenv run python release_noter.py -h
```

## Examples

```bash
pipenv run python release_noter.py -r v3.2.3..HEAD
pipenv run python release_noter.py -r v3.2.3..v3.3.0-rc0
pipenv run python release_noter.py -r v3.2.3..v3.3.0-rc0 -c
```

## Coding

```bash
pipenv run black release_noter.py
```
