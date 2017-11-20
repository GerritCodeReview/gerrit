---
title: " Gerrit Code Review - /accounts/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-accounts.html
---
This page describes the account related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Account Endpoints

### Query Account

*GET /accounts/*

Queries accounts visible to the caller. The [query
string](user-search-accounts.html#_search_operators) must be provided by
the `q` parameter. The `n` parameter can be used to limit the returned
results.

As result a list of [AccountInfo](#account-info) entities is returned.

**Request.**

``` 
  GET /accounts/?q=name:John+email:example.com&n=2 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000096,
    },
    {
      "_account_id": 1001439,
      "_more_accounts": true
    }
  ]
```

If the number of accounts matching the query exceeds either the internal
limit or a supplied `n` query parameter, the last account object has a
`_more_accounts: true` JSON field set.

The `S` or `start` query parameter can be supplied to skip a number of
accounts from the list.

Additional fields can be obtained by adding `o` parameters, each option
slows down the query response time to the client so they are generally
disabled by default. Optional fields are:

  - `DETAILS`: Includes full name, preferred email, username and avatars
    for each account.

<!-- end list -->

  - `ALL_EMAILS`: Includes all registered emails.

To get account suggestions set the parameter `suggest` and provide the
typed substring as query `q`. If a result limit `n` is not specified,
then the default 10 is used.

For account suggestions [account details](#details) and [all
emails](#all-emails) are always returned.

**Request.**

``` 
  GET /accounts/?suggest&q=John HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "john"
    },
    {
      "_account_id": 1001439,
      "name": "John Smith",
      "email": "john.smith@example.com",
      "username": "jsmith"
    },
  ]
```

### Get Account

*GET /accounts/[{account-id}](#account-id)*

Returns an account as an [AccountInfo](#account-info) entity.

**Request.**

``` 
  GET /accounts/self HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "_account_id": 1000096,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "username": "john"
  }
```

### Create Account

*PUT /accounts/[{username}](#username)*

Creates a new account.

In the request body additional data for the account can be provided as
[AccountInput](#account-input).

**Request.**

``` 
  PUT /accounts/john HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "ssh_key": "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw==",
    "http_password": "19D9aIn7zePb",
    "groups": [
      "MyProject-Owners"
    ]
  }
```

As response a detailed [AccountInfo](#account-info) entity is returned
that describes the created account.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "_account_id": 1000195,
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
```

### Get Account Details

*GET /accounts/[{account-id}](#account-id)/detail*

Retrieves the details of an account as an
[AccountDetailInfo](#account-detail-info) entity.

**Request.**

``` 
  GET /accounts/self/detail HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "registered_on": "2015-07-23 07:01:09.296000000",
    "_account_id": 1000096,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "username": "john"
  }
```

### Get Account Name

*GET /accounts/[{account-id}](#account-id)/name*

Retrieves the full name of an account.

**Request.**

``` 
  GET /accounts/self/name HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "John Doe"
```

If the account does not have a name an empty string is returned.

### Set Account Name

*PUT /accounts/[{account-id}](#account-id)/name*

Sets the full name of an account.

The new account name must be provided in the request body inside an
[AccountNameInput](#account-name-input) entity.

**Request.**

``` 
  PUT /accounts/self/name HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "name": "John F. Doe"
  }
```

As response the new account name is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "John F. Doe"
```

If the name was deleted the response is "`204 No Content`".

Some realms may not allow to modify the account name. In this case the
request is rejected with "`405 Method Not Allowed`".

### Delete Account Name

*DELETE /accounts/[{account-id}](#account-id)/name*

Deletes the name of an account.

**Request.**

``` 
  DELETE /accounts/self/name HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Account Status

*GET /accounts/[{account-id}](#account-id)/status*

Retrieves the status of an account.

**Request.**

``` 
  GET /accounts/self/status HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Available"
```

If the account does not have a status an empty string is returned.

### Set Account Status

*PUT /accounts/[{account-id}](#account-id)/status*

Sets the status of an account.

The new account status must be provided in the request body inside an
[AccountStatusInput](#account-status-input) entity.

**Request.**

``` 
  PUT /accounts/self/status HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "status": "Out Of Office"
  }
```

As response the new account status is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Out Of Office"
```

If the name was deleted the response is "`204 No Content`".

### Get Username

*GET /accounts/[{account-id}](#account-id)/username*

Retrieves the username of an account.

**Request.**

``` 
  GET /accounts/self/username HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "john.doe"
```

If the account does not have a username the response is "`404 Not
Found`".

### Set Username

*PUT /accounts/[{account-id}](#account-id)/username*

The new username must be provided in the request body inside a
[UsernameInput](#username-input) entity.

Once set, the username cannot be changed or deleted. If attempted this
fails with "`405 Method Not Allowed`".

**Request.**

``` 
  PUT /accounts/self/username HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "username": "jdoe"
  }
```

As response the new username is returned.

### Get Active

*GET /accounts/[{account-id}](#account-id)/active*

Checks if an account is active.

**Request.**

``` 
  GET /accounts/john.doe@example.com/active HTTP/1.0
```

If the account is active the string `ok` is returned.

**Response.**

``` 
  HTTP/1.1 200 OK

  ok
```

If the account is inactive the response is "`204 No Content`".

### Set Active

*PUT /accounts/[{account-id}](#account-id)/active*

Sets the account state to active.

**Request.**

``` 
  PUT /accounts/john.doe@example.com/active HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 201 Created
```

If the account was already active the response is "`200 OK`".

### Delete Active

*DELETE /accounts/[{account-id}](#account-id)/active*

Sets the account state to inactive.

**Request.**

``` 
  DELETE /accounts/john.doe@example.com/active HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

If the account was already inactive the response is "`409 Conflict`".

### Set/Generate HTTP Password

*PUT /accounts/[{account-id}](#account-id)/password.http*

Sets/Generates the HTTP password of an account.

The options for setting/generating the HTTP password must be provided in
the request body inside a [HttpPasswordInput](#http-password-input)
entity.

**Request.**

``` 
  PUT /accounts/self/password.http HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "generate": true
  }
```

As response the new HTTP password is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "ETxgpih8xrNs"
```

If the HTTP password was deleted the response is "`204 No Content`".

### Delete HTTP Password

*DELETE /accounts/[{account-id}](#account-id)/password.http*

Deletes the HTTP password of an account.

**Request.**

``` 
  DELETE /accounts/self/password.http HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get OAuth Access Token

*GET /accounts/[{account-id}](#account-id)/oauthtoken*

Returns a previously obtained OAuth access token.

**Request.**

``` 
  GET /accounts/self/oauthtoken HTTP/1.1
```

As a response, an [OAuthTokenInfo](#oauth-token-info) entity is returned
that describes the OAuth access token.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

   )]}'
    {
      "username": "johndow",
      "resource_host": "gerrit.example.org",
      "access_token": "eyJhbGciOiJSUzI1NiJ9.eyJqdGkiOi",
      "provider_id": "oauth-plugin:oauth-provider",
      "expires_at": "922337203775807",
      "type": "bearer"
    }
```

If there is no token available, or the token has already expired, "`404
Not Found`" is returned as response. Requests to obtain an access token
of another user are rejected with "`403 Forbidden`".

### List Account Emails

*GET /accounts/[{account-id}](#account-id)/emails*

Returns the email addresses that are configured for the specified user.

**Request.**

``` 
  GET /accounts/self/emails HTTP/1.0
```

As response the email addresses of the user are returned as a list of
[EmailInfo](#email-info) entities.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "email": "john.doe@example.com",
      "preferred": true
    },
    {
      "email": "j.doe@example.com"
    }
  ]
```

### Get Account Email

*GET
/accounts/[{account-id}](#account-id)/emails/[{email-id}](#email-id)*

Retrieves an email address of a user.

**Request.**

``` 
  GET /accounts/self/emails/john.doe@example.com HTTP/1.0
```

As response an [EmailInfo](#email-info) entity is returned that
describes the email address.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "email": "john.doe@example.com",
    "preferred": true
  }
```

### Create Account Email

*PUT
/accounts/[{account-id}](#account-id)/emails/[{email-id}](#email-id)*

Registers a new email address for the user. A verification email is sent
with a link that needs to be visited to confirm the email address,
unless `DEVELOPMENT_BECOME_ANY_ACCOUNT` is used as authentication type.
For the development mode email addresses are directly added without
confirmation. A Gerrit administrator may add an email address without
confirmation by setting `no_confirmation` in the
[EmailInput](#email-input). If
[sendemail.allowrcpt](config-gerrit.html#sendemail.allowrcpt) is
configured, the added email address must belong to a domain that is
allowed, unless `no_confirmation` is set.

The [EmailInput](#email-input) object in the request body may contain
additional options for the email address.

**Request.**

``` 
  PUT /accounts/self/emails/john.doe@example.com HTTP/1.0
  Content-Type: application/json; charset=UTF-8
  Content-Length: 3

  {}
```

As response the new email address is returned as
[EmailInfo](#email-info) entity.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "email": "john.doe@example.com",
    "pending_confirmation": true
  }
```

### Delete Account Email

*DELETE
/accounts/[{account-id}](#account-id)/emails/[{email-id}](#email-id)*

Deletes an email address of an account.

**Request.**

``` 
  DELETE /accounts/self/emails/john.doe@example.com HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Set Preferred Email

*PUT
/accounts/[{account-id}](#account-id)/emails/[{email-id}](#email-id)/preferred*

Sets an email address as preferred email address for an account.

**Request.**

``` 
  PUT /accounts/self/emails/john.doe@example.com/preferred HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 201 Created
```

If the email address was already the preferred email address of the
account the response is "`200 OK`".

### List SSH Keys

*GET /accounts/[{account-id}](#account-id)/sshkeys*

Returns the SSH keys of an account.

**Request.**

``` 
  GET /accounts/self/sshkeys HTTP/1.0
```

As response the SSH keys of the account are returned as a list of
[SshKeyInfo](#ssh-key-info) entities.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "seq": 1,
      "ssh_public_key": "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d john.doe@example.com",
      "encoded_key": "AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d",
      "algorithm": "ssh-rsa",
      "comment": "john.doe@example.com",
      "valid": true
    }
  ]
```

### Get SSH Key

*GET
/accounts/[{account-id}](#account-id)/sshkeys/[{ssh-key-id}](#ssh-key-id)*

Retrieves an SSH key of a user.

**Request.**

``` 
  GET /accounts/self/sshkeys/1 HTTP/1.0
```

As response an [SshKeyInfo](#ssh-key-info) entity is returned that
describes the SSH key.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "seq": 1,
    "ssh_public_key": "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d john.doe@example.com",
    "encoded_key": "AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d",
    "algorithm": "ssh-rsa",
    "comment": "john.doe@example.com",
    "valid": true
  }
```

### Add SSH Key

*POST /accounts/[{account-id}](#account-id)/sshkeys*

Adds an SSH key for a user.

The SSH public key must be provided as raw content in the request body.

Trying to add an SSH key that already exists succeeds, but no new SSH
key is persisted.

**Request.**

``` 
  POST /accounts/self/sshkeys HTTP/1.0
  Content-Type: plain/text

  AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d
```

As response an [SshKeyInfo](#ssh-key-info) entity is returned that
describes the new SSH key.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "seq": 2,
    "ssh_public_key": "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d john.doe@example.com",
    "encoded_key": "AAAAB3NzaC1yc2EAAAABIwAAAQEA0T...YImydZAw\u003d\u003d",
    "algorithm": "ssh-rsa",
    "comment": "john.doe@example.com",
    "valid": true
  }
```

### Delete SSH Key

*DELETE
/accounts/[{account-id}](#account-id)/sshkeys/[{ssh-key-id}](#ssh-key-id)*

Deletes an SSH key of a user.

**Request.**

``` 
  DELETE /accounts/self/sshkeys/2 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### List GPG Keys

*GET /accounts/[{account-id}](#account-id)/gpgkeys*

Returns the GPG keys of an account.

**Request.**

``` 
  GET /accounts/self/gpgkeys HTTP/1.0
```

As a response, the GPG keys of the account are returned as a map of
[GpgKeyInfo](#gpg-key-info) entities, keyed by ID.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "AFC8A49B": {
      "fingerprint": "0192 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B",
      "user_ids": [
        "John Doe \u003cjohn.doe@example.com\u003e"
      ],
      "key": "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: BCPG v1.52\n\nmQENBFXUpNcBCACv4paCiyKxZ0EcKy8VaWVNkJlNebRBiyw9WxU85wPOq5Gz/3GT\nRQwKqeY0SxVdQT8VNBw2sBe2m6eqcfZ2iKmesSlbXMe15DA7k8Bg4zEpQ0tXNG1L\nhceZDVQ1Xk06T2sgkunaiPsXi82nwN3UWYtDXxX4is5e6xBNL48Jgz4lbqo6+8D5\nvsVYiYMx4AwRkJyt/oA3IZAtSlY8Yd445nY14VPcnsGRwGWTLyZv9gxKHRUppVhQ\nE3o6ePXKEVgmONnQ4CjqmkGwWZvjMF2EPtAxvQLAuFa8Hqtkq5cgfgVkv/Vrcln4\nnQZVoMm3a3f5ODii2tQzNh6+7LL1bpqAmVEtABEBAAG0H0pvaG4gRG9lIDxqb2hu\nLmRvZUBleGFtcGxlLmNvbT6JATgEEwECACIFAlXUpNcCGwMGCwkIBwMCBhUIAgkK\nCwQWAgMBAh4BAheAAAoJEJNQnkuvyKSbfjoH/2OcSQOu1kJ20ndjhgY2yNChm7gd\ntU7TEBbB0TsLeazkrrLtKvrpW5+CRe07ZAG9HOtp3DikwAyrhSxhlYgVsQDhgB8q\nG0tYiZtQ88YyYrncCQ4hwknrcWXVW9bK3V4ZauxzPv3ADSloyR9tMURw5iHCIeL5\nfIw/pLvA3RjPMx4Sfow/bqRCUELua39prGw5Tv8a2ZRFbj2sgP5j8lUFegyJPQ4z\ntJhe6zZvKOzvIyxHO8llLmdrImsXRL9eqroWGs0VYqe6baQpY6xpSjbYK0J5HYcg\nTO+/u80JI+ROTMHE6unGp5Pgh/xIz6Wd34E0lWL1eOyNfGiPLyRWn1d0",
      "status": "TRUSTED",
      "problems": [],
    },
  }
```

### Get GPG Key

*GET
/accounts/[{account-id}](#account-id)/gpgkeys/[{gpg-key-id}](#gpg-key-id)*

Retrieves a GPG key of a user.

**Request.**

``` 
  GET /accounts/self/gpgkeys/AFC8A49B HTTP/1.0
```

As a response, a [GpgKeyInfo](#gpg-key-info) entity is returned that
describes the GPG key.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "AFC8A49B",
    "fingerprint": "0192 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B",
    "user_ids": [
      "John Doe \u003cjohn.doe@example.com\u003e"
    ],
    "key": "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: BCPG v1.52\n\nmQENBFXUpNcBCACv4paCiyKxZ0EcKy8VaWVNkJlNebRBiyw9WxU85wPOq5Gz/3GT\nRQwKqeY0SxVdQT8VNBw2sBe2m6eqcfZ2iKmesSlbXMe15DA7k8Bg4zEpQ0tXNG1L\nhceZDVQ1Xk06T2sgkunaiPsXi82nwN3UWYtDXxX4is5e6xBNL48Jgz4lbqo6+8D5\nvsVYiYMx4AwRkJyt/oA3IZAtSlY8Yd445nY14VPcnsGRwGWTLyZv9gxKHRUppVhQ\nE3o6ePXKEVgmONnQ4CjqmkGwWZvjMF2EPtAxvQLAuFa8Hqtkq5cgfgVkv/Vrcln4\nnQZVoMm3a3f5ODii2tQzNh6+7LL1bpqAmVEtABEBAAG0H0pvaG4gRG9lIDxqb2hu\nLmRvZUBleGFtcGxlLmNvbT6JATgEEwECACIFAlXUpNcCGwMGCwkIBwMCBhUIAgkK\nCwQWAgMBAh4BAheAAAoJEJNQnkuvyKSbfjoH/2OcSQOu1kJ20ndjhgY2yNChm7gd\ntU7TEBbB0TsLeazkrrLtKvrpW5+CRe07ZAG9HOtp3DikwAyrhSxhlYgVsQDhgB8q\nG0tYiZtQ88YyYrncCQ4hwknrcWXVW9bK3V4ZauxzPv3ADSloyR9tMURw5iHCIeL5\nfIw/pLvA3RjPMx4Sfow/bqRCUELua39prGw5Tv8a2ZRFbj2sgP5j8lUFegyJPQ4z\ntJhe6zZvKOzvIyxHO8llLmdrImsXRL9eqroWGs0VYqe6baQpY6xpSjbYK0J5HYcg\nTO+/u80JI+ROTMHE6unGp5Pgh/xIz6Wd34E0lWL1eOyNfGiPLyRWn1d0",
    "status": "TRUSTED",
    "problems": [],
  }
```

### Add/Delete GPG Keys

*POST /accounts/[{account-id}](#account-id)/gpgkeys*

Add or delete one or more GPG keys for a user.

The changes must be provided in the request body as a
[GpgKeysInput](#gpg-keys-input) entity. Each new GPG key is provided in
ASCII armored format, and must contain a self-signed certification
matching a registered email or other identity of the user.

**Request.**

``` 
  POST /accounts/link:#account-id[\{account-id\}]/gpgkeys
  Content-Type: application/json

  {
    "add": [
      "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: GnuPG v1\n\nmQENBFXUpNcBCACv4paCiyKxZ0EcKy8VaWVNkJlNebRBiyw9WxU85wPOq5Gz/3GT\nRQwKqeY0SxVdQT8VNBw2sBe2m6eqcfZ2iKmesSlbXMe15DA7k8Bg4zEpQ0tXNG1L\nhceZDVQ1Xk06T2sgkunaiPsXi82nwN3UWYtDXxX4is5e6xBNL48Jgz4lbqo6+8D5\nvsVYiYMx4AwRkJyt/oA3IZAtSlY8Yd445nY14VPcnsGRwGWTLyZv9gxKHRUppVhQ\nE3o6ePXKEVgmONnQ4CjqmkGwWZvjMF2EPtAxvQLAuFa8Hqtkq5cgfgVkv/Vrcln4\nnQZVoMm3a3f5ODii2tQzNh6+7LL1bpqAmVEtABEBAAG0H0pvaG4gRG9lIDxqb2hu\nLmRvZUBleGFtcGxlLmNvbT6JATgEEwECACIFAlXUpNcCGwMGCwkIBwMCBhUIAgkK\nCwQWAgMBAh4BAheAAAoJEJNQnkuvyKSbfjoH/2OcSQOu1kJ20ndjhgY2yNChm7gd\ntU7TEBbB0TsLeazkrrLtKvrpW5+CRe07ZAG9HOtp3DikwAyrhSxhlYgVsQDhgB8q\nG0tYiZtQ88YyYrncCQ4hwknrcWXVW9bK3V4ZauxzPv3ADSloyR9tMURw5iHCIeL5\nfIw/pLvA3RjPMx4Sfow/bqRCUELua39prGw5Tv8a2ZRFbj2sgP5j8lUFegyJPQ4z\ntJhe6zZvKOzvIyxHO8llLmdrImsXRL9eqroWGs0VYqe6baQpY6xpSjbYK0J5HYcg\nTO+/u80JI+ROTMHE6unGp5Pgh/xIz6Wd34E0lWL1eOyNfGiPLyRWn1d0yZO5AQ0E\nVdSk1wEIALUycrH2HK9zQYdR/KJo1yJJuaextLWsYYn881yDQo/p06U5vXOZ28lG\nAq/Xs96woVZPbgME6FyQzhf20Z2sbr+5bNo3OcEKaKX3Eo/sWwSJ7bXbGLDxMf4S\netfY1WDC+4rTqE30JuC++nQviPRdCcZf0AEgM6TxVhYEMVYwV787YO1IH62EBICM\nSkIONOfnusNZ4Skgjq9OzakOOpROZ4tki5cH/5oSDgdcaGPy1CFDpL9fG6er2zzk\nsw3qCbraqZrrlgpinWcAduiao67U/dV18O6OjYzrt33fTKZ0+bXhk1h1gloC21MQ\nya0CXlnfR/FOQhvuK0RlbR3cMfhZQscAEQEAAYkBHwQYAQIACQUCVdSk1wIbDAAK\nCRCTUJ5Lr8ikm8+QB/4uE+AlvFQFh9W8koPdfk7CJF7wdgZZ2NDtktvLL71WuMK8\nPOmf9f5JtcLCX4iJxGzcWogAR5ed20NgUoHUg7jn9Xm3fvP+kiqL6WqPhjazd89h\nk06v9hPE65kp4wb0fQqDrtWfP1lFGuh77rQgISt3Y4QutDl49vXS183JAfGPxFxx\n8FgGcfNwL2LVObvqCA0WLqeIrQVbniBPFGocE3yA/0W9BB/xtolpKfgMMsqGRMeu\n9oIsNxB2oE61OsqjUtGsnKQi8k5CZbhJaql4S89vwS+efK0R+mo+0N55b0XxRlCS\nfaURgAcjarQzJnG0hUps2GNO/+nM7UyyJAGfHlh5\n=EdXO\n-----END PGP PUBLIC KEY BLOCK-----\n"
    ],
    "delete": [
      "DEADBEEF",
    ]
  }'
```

As a response, the modified GPG keys are returned as a map of
[GpgKeyInfo](#gpg-key-info) entities, keyed by ID. Deleted keys are
represented by an empty object.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "AFC8A49B": {
      "fingerprint": "0192 723D 42D1 0C5B 32A6  E1E0 9350 9E4B AFC8 A49B",
      "user_ids": [
        "John Doe \u003cjohn.doe@example.com\u003e"
      ],
      "key": "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: BCPG v1.52\n\nmQENBFXUpNcBCACv4paCiyKxZ0EcKy8VaWVNkJlNebRBiyw9WxU85wPOq5Gz/3GT\nRQwKqeY0SxVdQT8VNBw2sBe2m6eqcfZ2iKmesSlbXMe15DA7k8Bg4zEpQ0tXNG1L\nhceZDVQ1Xk06T2sgkunaiPsXi82nwN3UWYtDXxX4is5e6xBNL48Jgz4lbqo6+8D5\nvsVYiYMx4AwRkJyt/oA3IZAtSlY8Yd445nY14VPcnsGRwGWTLyZv9gxKHRUppVhQ\nE3o6ePXKEVgmONnQ4CjqmkGwWZvjMF2EPtAxvQLAuFa8Hqtkq5cgfgVkv/Vrcln4\nnQZVoMm3a3f5ODii2tQzNh6+7LL1bpqAmVEtABEBAAG0H0pvaG4gRG9lIDxqb2hu\nLmRvZUBleGFtcGxlLmNvbT6JATgEEwECACIFAlXUpNcCGwMGCwkIBwMCBhUIAgkK\nCwQWAgMBAh4BAheAAAoJEJNQnkuvyKSbfjoH/2OcSQOu1kJ20ndjhgY2yNChm7gd\ntU7TEBbB0TsLeazkrrLtKvrpW5+CRe07ZAG9HOtp3DikwAyrhSxhlYgVsQDhgB8q\nG0tYiZtQ88YyYrncCQ4hwknrcWXVW9bK3V4ZauxzPv3ADSloyR9tMURw5iHCIeL5\nfIw/pLvA3RjPMx4Sfow/bqRCUELua39prGw5Tv8a2ZRFbj2sgP5j8lUFegyJPQ4z\ntJhe6zZvKOzvIyxHO8llLmdrImsXRL9eqroWGs0VYqe6baQpY6xpSjbYK0J5HYcg\nTO+/u80JI+ROTMHE6unGp5Pgh/xIz6Wd34E0lWL1eOyNfGiPLyRWn1d0"
      "status": "TRUSTED",
      "problems": [],
    }
    "DEADBEEF": {}
  }
```

### Delete GPG Key

*DELETE
/accounts/[{account-id}](#account-id)/gpgkeys/[{gpg-key-id}](#gpg-key-id)*

Deletes a GPG key of a user.

**Request.**

``` 
  DELETE /accounts/self/gpgkeys/AFC8A49B HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### List Account Capabilities

*GET /accounts/[{account-id}](#account-id)/capabilities*

Returns the global capabilities that are enabled for the specified user.

If the global capabilities for the calling user should be listed, `self`
can be used as account-id. This can be used by UI tools to discover if
administrative features are available to the caller, so they can hide
(or show) relevant UI actions.

**Request.**

``` 
  GET /accounts/self/capabilities HTTP/1.0
```

As response the global capabilities of the user are returned as a
[CapabilityInfo](#capability-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "queryLimit": {
      "min": 0,
      "max": 500
    },
    "emailReviewers": true
  }
```

Administrator that has authenticated with basic authentication:

**Request.**

``` 
  GET /a/accounts/self/capabilities HTTP/1.0
  Authorization: Basic ABCDECF..
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "administrateServer": true,
    "queryLimit": {
      "min": 0,
      "max": 500
    },
    "createAccount": true,
    "createGroup": true,
    "createProject": true,
    "emailReviewers": true,
    "killTask": true,
    "viewCaches": true,
    "flushCaches": true,
    "viewConnections": true,
    "viewPlugins": true,
    "viewQueue": true,
    "runGC": true
  }
```

get::/accounts/self/capabilities

To filter the set of global capabilities the `q` parameter can be used.
Filtering may decrease the response time by avoiding looking at every
possible alternative for the
caller.

**Request.**

``` 
  GET /a/accounts/self/capabilities?q=createAccount&q=createGroup HTTP/1.0
  Authorization: Basic ABCDEF...
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "createAccount": true,
    "createGroup": true
  }
```

get::/accounts/self/capabilities?q=createGroup

### Check Account Capability

*GET
/accounts/[{account-id}](#account-id)/capabilities/[{capability-id}](#capability-id)*

Checks if a user has a certain global capability.

**Request.**

``` 
  GET /a/accounts/self/capabilities/createGroup HTTP/1.0
```

If the user has the global capability the string `ok` is returned.

**Response.**

``` 
  HTTP/1.1 200 OK

  ok
```

If the user doesn’t have the global capability the response is "`404 Not
Found`".

get::/accounts/self/capabilities/createGroup

### List Groups

*GET /accounts/[{account-id}](#account-id)/groups/*

Lists all groups that contain the specified user as a member.

**Request.**

``` 
  GET /a/accounts/self/groups/ HTTP/1.0
```

As result a list of [GroupInfo](rest-api-groups.html#group-info) entries
is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "global%3AAnonymous-Users",
      "url": "#/admin/groups/uuid-global%3AAnonymous-Users",
      "options": {
      },
      "description": "Any user, signed-in or not",
      "group_id": 2,
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389"
    },
    {
      "id": "834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7",
      "url": "#/admin/groups/uuid-834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7",
      "options": {
        "visible_to_all": true,
      },
      "group_id": 6,
      "owner_id": "834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7"
    },
    {
      "id": "global%3ARegistered-Users",
      "url": "#/admin/groups/uuid-global%3ARegistered-Users",
      "options": {
      },
      "description": "Any signed-in user",
      "group_id": 3,
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389"
    }
  ]
```

get::/accounts/self/groups/

### Get Avatar

*GET /accounts/[{account-id}](#account-id)/avatar*

Retrieves the avatar image of the user.

With the `size` option (alias `s`) you can specify the preferred size in
pixels (height and width).

**Request.**

``` 
  GET /a/accounts/john.doe@example.com/avatar?s=20 HTTP/1.0
```

The response redirects to the URL of the avatar image.

**Response.**

``` 
  HTTP/1.1 302 Found
  Location: https://profiles/avatar/john_doe.jpeg?s=20x20
```

### Get Avatar Change URL

*GET /accounts/[{account-id}](#account-id)/avatar.change.url*

Retrieves the URL where the user can change the avatar image.

**Request.**

``` 
  GET /a/accounts/self/avatar.change.url HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  https://profiles/pictures/john.doe
```

### Get User Preferences

*GET /accounts/[{account-id}](#account-id)/preferences*

Retrieves the user’s preferences.

**Request.**

``` 
  GET /a/accounts/self/preferences HTTP/1.0
```

As result the account preferences of the user are returned as a
[PreferencesInfo](#preferences-info) entity.

Users may only retrieve the preferences for their own account, unless
they are an [Administrator](access-control.html#administrators) or a
member of a group that is granted the
[ModifyAccount](access-control.html#capability_modifyAccount)
capability, in which case they can retrieve the preferences for any
account.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "changes_per_page": 25,
    "show_site_header": true,
    "use_flash_clipboard": true,
    "download_command": "CHECKOUT",
    "date_format": "STD",
    "time_format": "HHMM_12",
    "diff_view": "SIDE_BY_SIDE",
    "size_bar_in_change_table": true,
    "review_category_strategy": "ABBREV",
    "mute_common_path_prefixes": true,
    "publish_comments_on_push": true,
    "default_base_for_merges": "FIRST_PARENT",
    "my": [
      {
        "url": "#/dashboard/self",
        "name": "Changes"
      },
      {
        "url": "#/q/has:draft",
        "name": "Draft Comments"
      },
      {
        "url": "#/q/is:watched+is:open",
        "name": "Watched Changes"
      },
      {
        "url": "#/q/is:starred",
        "name": "Starred Changes"
      },
      {
        "url": "#/groups/self",
        "name": "Groups"
      },
      change_table: []
    ]
  }
```

### Set User Preferences

*PUT /accounts/[{account-id}](#account-id)/preferences*

Sets the user’s preferences.

The new preferences must be provided in the request body as a
[PreferencesInput](#preferences-input) entity.

**Request.**

``` 
  PUT /a/accounts/self/preferences HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "changes_per_page": 50,
    "show_site_header": true,
    "use_flash_clipboard": true,
    "expand_inline_diffs": true,
    "download_command": "CHECKOUT",
    "date_format": "STD",
    "time_format": "HHMM_12",
    "size_bar_in_change_table": true,
    "review_category_strategy": "NAME",
    "diff_view": "SIDE_BY_SIDE",
    "mute_common_path_prefixes": true,
    "my": [
      {
        "url": "#/dashboard/self",
        "name": "Changes"
      },
      {
        "url": "#/q/has:draft",
        "name": "Draft Comments"
      },
      {
        "url": "#/q/is:watched+is:open",
        "name": "Watched Changes"
      },
      {
        "url": "#/q/is:starred",
        "name": "Starred Changes"
      },
      {
        "url": "#/groups/self",
        "name": "Groups"
      }
    ],
    "change_table": [
      "Subject",
      "Owner"
    ]
  }
```

As result the new preferences of the user are returned as a
[PreferencesInfo](#preferences-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "changes_per_page": 50,
    "show_site_header": true,
    "use_flash_clipboard": true,
    "expand_inline_diffs": true,
    "download_command": "CHECKOUT",
    "date_format": "STD",
    "time_format": "HHMM_12",
    "size_bar_in_change_table": true,
    "review_category_strategy": "NAME",
    "diff_view": "SIDE_BY_SIDE",
    "publish_comments_on_push": true,
    "mute_common_path_prefixes": true,
    "my": [
      {
        "url": "#/dashboard/self",
        "name": "Changes"
      },
      {
        "url": "#/q/has:draft",
        "name": "Draft Comments"
      },
      {
        "url": "#/q/is:watched+is:open",
        "name": "Watched Changes"
      },
      {
        "url": "#/q/is:starred",
        "name": "Starred Changes"
      },
      {
        "url": "#/groups/self",
        "name": "Groups"
      }
    ],
    "change_table": [
      "Subject",
      "Owner"
    ]
  }
```

### Get Diff Preferences

*GET /accounts/[{account-id}](#account-id)/preferences.diff*

Retrieves the diff preferences of a user.

**Request.**

``` 
  GET /a/accounts/self/preferences.diff HTTP/1.0
```

As result the diff preferences of the user are returned as a
[DiffPreferencesInfo](#diff-preferences-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "context": 10,
    "theme": "DEFAULT",
    "ignore_whitespace": "IGNORE_ALL",
    "intraline_difference": true,
    "line_length": 100,
    "cursor_blink_rate": 500,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "tab_size": 8,
    "font_size": 12
  }
```

### Set Diff Preferences

*PUT /accounts/[{account-id}](#account-id)/preferences.diff*

Sets the diff preferences of a user.

The new diff preferences must be provided in the request body as a
[DiffPreferencesInput](#diff-preferences-input) entity.

**Request.**

``` 
  PUT /a/accounts/self/preferences.diff HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "context": 10,
    "theme": "ECLIPSE",
    "ignore_whitespace": "IGNORE_ALL",
    "intraline_difference": true,
    "line_length": 100,
    "cursor_blink_rate": 500,
    "show_line_endings": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "tab_size": 8,
    "font_size": 12
  }
```

As result the new diff preferences of the user are returned as a
[DiffPreferencesInfo](#diff-preferences-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "context": 10,
    "theme": "ECLIPSE",
    "ignore_whitespace": "IGNORE_ALL",
    "intraline_difference": true,
    "line_length": 100,
    "show_line_endings": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "tab_size": 8,
    "font_size": 12
  }
```

### Get Edit Preferences

*GET /accounts/[{account-id}](#account-id)/preferences.edit*

Retrieves the edit preferences of a user.

**Request.**

``` 
  GET /a/accounts/self/preferences.edit HTTP/1.0
```

As result the edit preferences of the user are returned as a
[EditPreferencesInfo](#edit-preferences-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "theme": "ECLIPSE",
    "key_map_type": "VIM",
    "tab_size": 4,
    "line_length": 80,
    "indent_unit": 2,
    "cursor_blink_rate": 530,
    "hide_top_menu": true,
    "show_whitespace_errors": true,
    "hide_line_numbers": true,
    "match_brackets": true,
    "line_wrapping": false,
    "indent_with_tabs": false,
    "auto_close_brackets": true
  }
```

### Set Edit Preferences

*PUT /accounts/[{account-id}](#account-id)/preferences.edit*

Sets the edit preferences of a user.

The new edit preferences must be provided in the request body as a
[EditPreferencesInfo](#edit-preferences-info) entity.

**Request.**

``` 
  PUT /a/accounts/self/preferences.edit HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "theme": "ECLIPSE",
    "key_map_type": "VIM",
    "tab_size": 4,
    "line_length": 80,
    "indent_unit": 2,
    "cursor_blink_rate": 530,
    "hide_top_menu": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "hide_line_numbers": true,
    "match_brackets": true,
    "line_wrapping": false,
    "auto_close_brackets": true
  }
```

As result the new edit preferences of the user are returned as a
[EditPreferencesInfo](#edit-preferences-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "theme": "ECLIPSE",
    "key_map_type": "VIM",
    "tab_size": 4,
    "line_length": 80,
    "cursor_blink_rate": 530,
    "hide_top_menu": true,
    "show_whitespace_errors": true,
    "hide_line_numbers": true,
    "match_brackets": true,
    "auto_close_brackets": true
  }
```

### Get Watched Projects

*GET /accounts/[{account-id}](#account-id)/watched.projects*

Retrieves all projects a user is watching.

**Request.**

``` 
  GET /a/accounts/self/watched.projects HTTP/1.0
```

As result the watched projects of the user are returned as a list of
[ProjectWatchInfo](#project-watch-info) entities. The result is sorted
by project name in ascending order.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "project": "Test Project 1",
      "notify_new_changes": true,
      "notify_new_patch_sets": true,
      "notify_all_comments": true,
    },
    {
      "project": "Test Project 2",
      "filter": "branch:experimental",
      "notify_all_comments": true,
      "notify_submitted_changes": true,
      "notify_abandoned_changes": true
    }
  ]
```

### Add/Update a List of Watched Project Entities

*POST /accounts/[{account-id}](#account-id)/watched.projects*

Add new projects to watch or update existing watched projects. Projects
that are already watched by a user will be updated with the provided
configuration. All other projects in the request will be watched using
the provided configuration. The posted body can contain
[ProjectWatchInfo](#project-watch-info) entities. Omitted boolean values
will be set to false.

**Request.**

``` 
  POST /a/accounts/self/watched.projects HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  [
    {
      "project": "Test Project 1",
      "notify_new_changes": true,
      "notify_new_patch_sets": true,
      "notify_all_comments": true,
    }
  ]
```

As result the watched projects of the user are returned as a list of
[ProjectWatchInfo](#project-watch-info) entities. The result is sorted
by project name in ascending order.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "project": "Test Project 1",
      "notify_new_changes": true,
      "notify_new_patch_sets": true,
      "notify_all_comments": true,
    },
    {
      "project": "Test Project 2",
      "notify_new_changes": true,
      "notify_new_patch_sets": true,
      "notify_all_comments": true,
    }
  ]
```

### Delete Watched Projects

*POST /accounts/[{account-id}](#account-id)/watched.projects:delete*

Projects posted to this endpoint will no longer be watched. The posted
body can contain a list of [ProjectWatchInfo](#project-watch-info)
entities.

**Request.**

``` 
  POST /a/accounts/self/watched.projects:delete HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  [
    {
      "project": "Test Project 1",
      "filter": "branch:master"
    }
  ]
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Account External IDs

*GET /accounts/[{account-id}](#account-id)/external.ids*

Retrieves the external ids of a user account.

**Request.**

``` 
  GET /a/accounts/self/external.ids HTTP/1.0
```

As result the external ids of the user are returned as a list of
[AccountExternalIdInfo](#account-external-id-info) entities.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "identity": "username:john",
      "email": "john.doe@example.com",
      "trusted": true
    }
  ]
```

### Delete Account External IDs

*POST /accounts/[{account-id}](#account-id)/external.ids:delete*

Delete a list of external ids for a user account. The target external
ids must be provided as a list in the request body.

Only external ids belonging to the caller may be deleted.

**Request.**

``` 
  POST /a/accounts/self/external.ids:delete HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  [
    "mailto:john.doe@example.com"
  ]
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Default Star Endpoints

### Get Changes With Default Star

*GET /accounts/[{account-id}](#account-id)/starred.changes*

Gets the changes that were starred with the default star by the
identified user account. This URL endpoint is functionally identical to
the changes query `GET /changes/?q=is:starred`. The result is a list of
[ChangeInfo](rest-api-changes.html#change-info) entities.

**Request.**

``` 
  GET /a/accounts/self/starred.changes
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
      "project": "myProject",
      "branch": "master",
      "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
      "subject": "Implementing Feature X",
      "status": "NEW",
      "created": "2013-02-01 09:59:32.126000000",
      "updated": "2013-02-21 11:16:36.775000000",
      "starred": true,
      "stars": [
        "star"
      ],
      "mergeable": true,
      "submittable": false,
      "insertions": 145,
      "deletions": 12,
      "_number": 3965,
      "owner": {
        "name": "John Doe"
      }
    }
  ]
```

### Put Default Star On Change

*PUT
/accounts/[{account-id}](#account-id)/starred.changes/[{change-id}](rest-api-changes.html#change-id)*

Star a change with the default label. Changes starred with the default
label are returned for the search query `is:starred` or `starredby:USER`
and automatically notify the user whenever updates are made to the
change.

**Request.**

``` 
  PUT /a/accounts/self/starred.changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Remove Default Star From Change

*DELETE
/accounts/[{account-id}](#account-id)/starred.changes/[{change-id}](rest-api-changes.html#change-id)*

Remove the default star label from a change. This stops
notifications.

**Request.**

``` 
  DELETE /a/accounts/self/starred.changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Star Endpoints

### Get Starred Changes

*GET /accounts/[{account-id}](#account-id)/stars.changes*

Gets the changes that were starred with any label by the identified user
account. This URL endpoint is functionally identical to the changes
query `GET /changes/?q=has:stars`. The result is a list of
[ChangeInfo](rest-api-changes.html#change-info) entities.

**Request.**

``` 
  GET /a/accounts/self/stars.changes
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
      "project": "myProject",
      "branch": "master",
      "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
      "subject": "Implementing Feature X",
      "status": "NEW",
      "created": "2013-02-01 09:59:32.126000000",
      "updated": "2013-02-21 11:16:36.775000000",
      "stars": [
        "ignore",
        "risky"
      ],
      "mergeable": true,
      "submittable": false,
      "insertions": 145,
      "deletions": 12,
      "_number": 3965,
      "owner": {
        "name": "John Doe"
      }
    }
  ]
```

### Get Star Labels From Change

*GET
/accounts/[{account-id}](#account-id)/stars.changes/[{change-id}](rest-api-changes.html#change-id)*

Get star labels from a
change.

**Request.**

``` 
  GET /a/accounts/self/stars.changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
```

As response the star labels that the user applied on the change are
returned. The labels are lexicographically sorted.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "blue",
    "green",
    "red"
  ]
```

### Update Star Labels On Change

*POST
/accounts/[{account-id}](#account-id)/stars.changes/[{change-id}](rest-api-changes.html#change-id)*

Update star labels on a change. The star labels to be added/removed must
be specified in the request body as [StarsInput](#stars-input) entity.
Starred changes are returned for the search query
`has:stars`.

**Request.**

``` 
  POST /a/accounts/self/stars.changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "add": [
      "blue",
      "red"
    ],
    "remove": [
      "yellow"
    ]
  }
```

As response the star labels that the user applied on the change are
returned. The labels are lexicographically sorted.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "blue",
    "green",
    "red"
  ]
```

### List Contributor Agreements

*GET /accounts/[{account-id}](#account-id)/agreements*

Gets a list of the user’s signed contributor agreements.

**Request.**

``` 
  GET /a/accounts/self/agreements HTTP/1.0
```

As response the user’s signed agreements are returned as a list of
[ContributorAgreementInfo](#contributor-agreement-info) entities.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "name": "Individual",
      "description": "If you are going to be contributing code on your own, this is the one you want. You can sign this one online.",
      "url": "static/cla_individual.html"
    }
  ]
```

### Sign Contributor Agreement

*PUT /accounts/[{account-id}](#account-id)/agreements*

Signs a contributor agreement.

The contributor agreement must be provided in the request body as a
[ContributorAgreementInput](#contributor-agreement-input).

**Request.**

``` 
  PUT /accounts/self/agreements HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "name": "Individual"
  }
```

As response the contributor agreement name is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Individual"
```

### Index Account

*POST /accounts/[{account-id}](#account-id)/index*

Adds or updates the account in the secondary index.

**Request.**

``` 
  POST /accounts/1000096/index HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## IDs

### {account-id}

Identifier that uniquely identifies one account.

This can be:

  - a string of the format "Full Name \<<email@example.com>\>"

  - just the email address ("email@example")

  - a full name if it is unique ("Full Name")

  - an account ID ("18419")

  - a user name ("username")

  - `self` for the calling user

### {capability-id}

Identifier of a global capability. Valid values are all field names of
the [CapabilityInfo](#capability-info) entity.

### {email-id}

An email address, or `preferred` for the preferred email address of the
user.

### {username}

The user name.

### {ssh-key-id}

The sequence number of the SSH key.

### {gpg-key-id}

A GPG key identifier, either the 8-character hex key reported by `gpg
--list-keys`, or the 40-character hex fingerprint (whitespace is
ignored) reported by `gpg --list-keys --with-fingerprint`.

## JSON Entities

### AccountDetailInfo

The `AccountDetailInfo` entity contains detailed information about an
account.

`AccountDetailInfo` has the same fields as [AccountInfo](#account-info).
In addition `AccountDetailInfo` has the following fields:

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>registered_on</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the account was registered.</p></td>
</tr>
<tr class="even">
<td><p><code>inactive</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the account is inactive.</p></td>
</tr>
</tbody>
</table>

### AccountExternalIdInfo

The `AccountExternalIdInfo` entity contains information for an external
id of an account.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>identity</code></p></td>
<td></td>
<td><p>The account external id.</p></td>
</tr>
<tr class="even">
<td><p><code>email</code></p></td>
<td><p>optional</p></td>
<td><p>The email address for the external id.</p></td>
</tr>
<tr class="odd">
<td><p><code>trusted</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the external id is trusted.</p></td>
</tr>
<tr class="even">
<td><p><code>can_delete</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the external id can be deleted by the calling user.</p></td>
</tr>
</tbody>
</table>

### AccountInfo

The `AccountInfo` entity contains information about an account.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>_account_id</code></p></td>
<td></td>
<td><p>The numeric ID of the account.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>optional</p></td>
<td><p>The full name of the user.<br />
Only set if detailed account information is requested.<br />
See option <a href="rest-api-changes.html#detailed-accounts">DETAILED_ACCOUNTS</a> for change queries<br />
and option <a href="#details">DETAILS</a> for account queries.</p></td>
</tr>
<tr class="odd">
<td><p><code>email</code></p></td>
<td><p>optional</p></td>
<td><p>The email address the user prefers to be contacted through.<br />
Only set if detailed account information is requested.<br />
See option <a href="rest-api-changes.html#detailed-accounts">DETAILED_ACCOUNTS</a> for change queries<br />
and options <a href="#details">DETAILS</a> and <a href="#all-emails">ALL_EMAILS</a> for account queries.</p></td>
</tr>
<tr class="even">
<td><p><code>secondary_emails</code></p></td>
<td><p>optional</p></td>
<td><p>A list of the secondary email addresses of the user.<br />
Only set for account queries when the <a href="#all-emails">ALL_EMAILS</a> option is set.</p></td>
</tr>
<tr class="odd">
<td><p><code>username</code></p></td>
<td><p>optional</p></td>
<td><p>The username of the user.<br />
Only set if detailed account information is requested.<br />
See option <a href="rest-api-changes.html#detailed-accounts">DETAILED_ACCOUNTS</a> for change queries<br />
and option <a href="#details">DETAILS</a> for account queries.</p></td>
</tr>
<tr class="even">
<td><p><code>_more_accounts</code></p></td>
<td><p>optional, not set if <code>false</code></p></td>
<td><p>Whether the query would deliver more results if not limited.<br />
Only set on the last account that is returned.</p></td>
</tr>
</tbody>
</table>

### AccountInput

The `AccountInput` entity contains information for the creation of a new
account.

<table>
<colgroup>
<col width="14%" />
<col width="28%" />
<col width="57%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>username</code></p></td>
<td><p>optional</p></td>
<td><p>The user name. If provided, must match the user name from the URL.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>optional</p></td>
<td><p>The full name of the user.</p></td>
</tr>
<tr class="odd">
<td><p><code>email</code></p></td>
<td><p>optional</p></td>
<td><p>The email address of the user.</p></td>
</tr>
<tr class="even">
<td><p><code>ssh_key</code></p></td>
<td><p>optional</p></td>
<td><p>The public SSH key of the user.</p></td>
</tr>
<tr class="odd">
<td><p><code>http_password</code></p></td>
<td><p>optional</p></td>
<td><p>The HTTP password of the user.</p></td>
</tr>
<tr class="even">
<td><p><code>groups</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="rest-api-groups.html#group-id">group IDs</a> that identify the groups to which the user should be added.</p></td>
</tr>
</tbody>
</table>

### AccountNameInput

The `AccountNameInput` entity contains information for setting a name
for an account.

<table>
<colgroup>
<col width="14%" />
<col width="28%" />
<col width="57%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>name</code></p></td>
<td><p>optional</p></td>
<td><p>The new full name of the account.<br />
If not set or if set to an empty string, the account name is deleted.</p></td>
</tr>
</tbody>
</table>

### AccountStatusInput

The `AccountStatusInput` entity contains information for setting a
status for an account.

<table>
<colgroup>
<col width="14%" />
<col width="28%" />
<col width="57%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>status</code></p></td>
<td><p>optional</p></td>
<td><p>The new status of the account.<br />
If not set or if set to an empty string, the account status is deleted.</p></td>
</tr>
</tbody>
</table>

### CapabilityInfo

The `CapabilityInfo` entity contains information about the global
capabilities of a user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>accessDatabase</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_accessDatabase">Access Database</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>administrateServer</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_administrateServer">Administrate Server</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>createAccount</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_createAccount">Create Account</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>createGroup</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_createGroup">Create Group</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>createProject</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_createProject">Create Project</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>emailReviewers</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_emailReviewers">Email Reviewers</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>flushCaches</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_flushCaches">Flush Caches</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>killTask</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_kill">Kill Task</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>maintainServer</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_maintainServer">Maintain Server</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>priority</code></p></td>
<td><p>not set if <code>INTERACTIVE</code></p></td>
<td><p>The name of the thread pool used by the user, see <a href="access-control.html#capability_priority">Priority</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>queryLimit</code></p></td>
<td></td>
<td><p>The <a href="access-control.html#capability_queryLimit">Query Limit</a> of the user as <a href="#query-limit-info">QueryLimitInfo</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>runAs</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_runAs">Run As</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>runGC</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_runGC">Run Garbage Collection</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>streamEvents</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_streamEvents">Stream Events</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>viewAllAccounts</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_viewAllAccounts">View All Accounts</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>viewCaches</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_viewCaches">View Caches</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>viewConnections</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_viewConnections">View Connections</a> capability.</p></td>
</tr>
<tr class="even">
<td><p><code>viewPlugins</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_viewPlugins">View Plugins</a> capability.</p></td>
</tr>
<tr class="odd">
<td><p><code>viewQueue</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the user has the <a href="access-control.html#capability_viewQueue">View Queue</a> capability.</p></td>
</tr>
</tbody>
</table>

### ContributorAgreementInfo

The `ContributorAgreementInfo` entity contains information about a
contributor agreement.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>name</code></p></td>
<td><p>The name of the agreement.</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td><p>The description of the agreement.</p></td>
</tr>
<tr class="odd">
<td><p><code>url</code></p></td>
<td><p>The URL of the agreement.</p></td>
</tr>
</tbody>
</table>

### ContributorAgreementInput

The `ContributorAgreementInput` entity contains information about a new
contributor agreement.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>name</code></p></td>
<td><p>The name of the agreement.</p></td>
</tr>
</tbody>
</table>

### DiffPreferencesInfo

The `DiffPreferencesInfo` entity contains information about the diff
preferences of a user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>context</code></p></td>
<td></td>
<td><p>The number of lines of context when viewing a patch.</p></td>
</tr>
<tr class="even">
<td><p><code>theme</code></p></td>
<td></td>
<td><p>The CodeMirror theme name in upper case, for example <code>DEFAULT</code>. All the themes from the CodeMirror release that Gerrit is using are available.</p></td>
</tr>
<tr class="odd">
<td><p><code>expand_all_comments</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether all inline comments should be automatically expanded.</p></td>
</tr>
<tr class="even">
<td><p><code>ignore_whitespace</code></p></td>
<td></td>
<td><p>Whether whitespace changes should be ignored and if yes, which whitespace changes should be ignored.<br />
Allowed values are <code>IGNORE_NONE</code>, <code>IGNORE_TRAILING</code>, <code>IGNORE_LEADING_AND_TRAILING</code>, <code>IGNORE_ALL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>intraline_difference</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether intraline differences should be highlighted.</p></td>
</tr>
<tr class="even">
<td><p><code>line_length</code></p></td>
<td></td>
<td><p>Number of characters that should be displayed in one line.</p></td>
</tr>
<tr class="odd">
<td><p><code>cursor_blink_rate</code></p></td>
<td></td>
<td><p>Half-period in milliseconds used for cursor blinking. Setting it to 0 disables cursor blinking.</p></td>
</tr>
<tr class="even">
<td><p><code>manual_review</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the <em>Reviewed</em> flag should not be set automatically on a patch when it is viewed.</p></td>
</tr>
<tr class="odd">
<td><p><code>retain_header</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the header that is displayed above the patch (that either shows the commit message, the diff preferences, the patch sets or the files) should be retained on file switch.</p></td>
</tr>
<tr class="even">
<td><p><code>show_line_endings</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether Windows EOL/Cr-Lf should be displayed as <em>\r</em> in a dotted-line box.</p></td>
</tr>
<tr class="odd">
<td><p><code>show_tabs</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether tabs should be shown.</p></td>
</tr>
<tr class="even">
<td><p><code>show_whitespace_errors</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether whitespace errors should be shown.</p></td>
</tr>
<tr class="odd">
<td><p><code>skip_deleted</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether deleted files should be skipped on file switch.</p></td>
</tr>
<tr class="even">
<td><p><code>skip_uncommented</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether uncommented files should be skipped on file switch.</p></td>
</tr>
<tr class="odd">
<td><p><code>syntax_highlighting</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether syntax highlighting should be enabled.</p></td>
</tr>
<tr class="even">
<td><p><code>hide_top_menu</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>If true the top menu header and site header are hidden.</p></td>
</tr>
<tr class="odd">
<td><p><code>auto_hide_diff_table_header</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>If true the diff table header is automatically hidden when scrolling down more than half of a page.</p></td>
</tr>
<tr class="even">
<td><p><code>hide_line_numbers</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>If true the line numbers are hidden.</p></td>
</tr>
<tr class="odd">
<td><p><code>tab_size</code></p></td>
<td></td>
<td><p>Number of spaces that should be used to display one tab.</p></td>
</tr>
<tr class="even">
<td><p><code>font_size</code></p></td>
<td></td>
<td><p>Default font size in pixels for change to be displayed in the diff view.</p></td>
</tr>
<tr class="odd">
<td><p><em>hide_empty_pane</em></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether empty panes should be hidden. The left pane is empty when a file was added; the right pane is empty when a file was deleted.</p></td>
</tr>
<tr class="even">
<td><p><code>match_brackets</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether matching brackets should be highlighted.</p></td>
</tr>
<tr class="odd">
<td><p><code>line_wrapping</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to enable line wrapping or not.</p></td>
</tr>
</tbody>
</table>

### DiffPreferencesInput

The `DiffPreferencesInput` entity contains information for setting the
diff preferences of a user. Fields which are not set will not be
updated.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>context</code></p></td>
<td><p>optional</p></td>
<td><p>The number of lines of context when viewing a patch.</p></td>
</tr>
<tr class="even">
<td><p><code>expand_all_comments</code></p></td>
<td><p>optional</p></td>
<td><p>Whether all inline comments should be automatically expanded.</p></td>
</tr>
<tr class="odd">
<td><p><code>ignore_whitespace</code></p></td>
<td><p>optional</p></td>
<td><p>Whether whitespace changes should be ignored and if yes, which whitespace changes should be ignored.<br />
Allowed values are <code>IGNORE_NONE</code>, <code>IGNORE_TRAILING</code>, <code>IGNORE_LEADING_AND_TRAILING</code>, <code>IGNORE_ALL</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>intraline_difference</code></p></td>
<td><p>optional</p></td>
<td><p>Whether intraline differences should be highlighted.</p></td>
</tr>
<tr class="odd">
<td><p><code>line_length</code></p></td>
<td><p>optional</p></td>
<td><p>Number of characters that should be displayed in one line.</p></td>
</tr>
<tr class="even">
<td><p><code>manual_review</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the <em>Reviewed</em> flag should not be set automatically on a patch when it is viewed.</p></td>
</tr>
<tr class="odd">
<td><p><code>retain_header</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the header that is displayed above the patch (that either shows the commit message, the diff preferences, the patch sets or the files) should be retained on file switch.</p></td>
</tr>
<tr class="even">
<td><p><code>show_line_endings</code></p></td>
<td><p>optional</p></td>
<td><p>Whether Windows EOL/Cr-Lf should be displayed as <em>\r</em> in a dotted-line box.</p></td>
</tr>
<tr class="odd">
<td><p><code>show_tabs</code></p></td>
<td><p>optional</p></td>
<td><p>Whether tabs should be shown.</p></td>
</tr>
<tr class="even">
<td><p><code>show_whitespace_errors</code></p></td>
<td><p>optional</p></td>
<td><p>Whether whitespace errors should be shown.</p></td>
</tr>
<tr class="odd">
<td><p><code>skip_deleted</code></p></td>
<td><p>optional</p></td>
<td><p>Whether deleted files should be skipped on file switch.</p></td>
</tr>
<tr class="even">
<td><p><code>skip_uncommented</code></p></td>
<td><p>optional</p></td>
<td><p>Whether uncommented files should be skipped on file switch.</p></td>
</tr>
<tr class="odd">
<td><p><code>syntax_highlighting</code></p></td>
<td><p>optional</p></td>
<td><p>Whether syntax highlighting should be enabled.</p></td>
</tr>
<tr class="even">
<td><p><code>hide_top_menu</code></p></td>
<td><p>optional</p></td>
<td><p>True if the top menu header and site header should be hidden.</p></td>
</tr>
<tr class="odd">
<td><p><code>auto_hide_diff_table_header</code></p></td>
<td><p>optional</p></td>
<td><p>True if the diff table header is automatically hidden when scrolling down more than half of a page.</p></td>
</tr>
<tr class="even">
<td><p><code>hide_line_numbers</code></p></td>
<td><p>optional</p></td>
<td><p>True if the line numbers should be hidden.</p></td>
</tr>
<tr class="odd">
<td><p><code>tab_size</code></p></td>
<td><p>optional</p></td>
<td><p>Number of spaces that should be used to display one tab.</p></td>
</tr>
<tr class="even">
<td><p><code>font_size</code></p></td>
<td><p>optional</p></td>
<td><p>Default font size in pixels for change to be displayed in the diff view.</p></td>
</tr>
<tr class="odd">
<td><p><code>line_wrapping</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to enable line wrapping or not.</p></td>
</tr>
<tr class="even">
<td><p><code>indent_with_tabs</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to enable indent with tabs or not.</p></td>
</tr>
</tbody>
</table>

### EditPreferencesInfo

The `EditPreferencesInfo` entity contains information about the edit
preferences of a user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>theme</code></p></td>
<td></td>
<td><p>The CodeMirror theme name in upper case, for example <code>DEFAULT</code>. All the themes from the CodeMirror release that Gerrit is using are available.</p></td>
</tr>
<tr class="even">
<td><p><code>key_map_type</code></p></td>
<td></td>
<td><p>The CodeMirror key map. Currently only a subset of key maps are supported: <code>DEFAULT</code>, <code>EMACS</code>, <code>SUBLIME</code>, <code>VIM</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>tab_size</code></p></td>
<td></td>
<td><p>Number of spaces that should be used to display one tab.</p></td>
</tr>
<tr class="even">
<td><p><code>line_length</code></p></td>
<td></td>
<td><p>Number of characters that should be displayed per line.</p></td>
</tr>
<tr class="odd">
<td><p><code>indent_unit</code></p></td>
<td></td>
<td><p>Number of spaces that should be used for auto-indent.</p></td>
</tr>
<tr class="even">
<td><p><code>cursor_blink_rate</code></p></td>
<td></td>
<td><p>Half-period in milliseconds used for cursor blinking. Setting it to 0 disables cursor blinking.</p></td>
</tr>
<tr class="odd">
<td><p><code>hide_top_menu</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>If true the top menu header and site header is hidden.</p></td>
</tr>
<tr class="even">
<td><p><code>show_tabs</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether tabs should be shown.</p></td>
</tr>
<tr class="odd">
<td><p><code>show_whitespace_errors</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether whitespace errors should be shown.</p></td>
</tr>
<tr class="even">
<td><p><code>syntax_highlighting</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether syntax highlighting should be enabled.</p></td>
</tr>
<tr class="odd">
<td><p><code>hide_line_numbers</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether line numbers should be hidden.</p></td>
</tr>
<tr class="even">
<td><p><code>match_brackets</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether matching brackets should be highlighted.</p></td>
</tr>
<tr class="odd">
<td><p><code>line_wrapping</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to enable line wrapping or not.</p></td>
</tr>
<tr class="even">
<td><p><code>auto_close_brackets</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether brackets and quotes should be auto-closed during typing.</p></td>
</tr>
</tbody>
</table>

### EmailInfo

The `EmailInfo` entity contains information about an email address of a
user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>email</code></p></td>
<td></td>
<td><p>The email address.</p></td>
</tr>
<tr class="even">
<td><p><code>preferred</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether this is the preferred email address of the user.</p></td>
</tr>
<tr class="odd">
<td><p><code>pending_confirmation</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Set true if the user must confirm control of the email address by following a verification link before Gerrit will permit use of this address.</p></td>
</tr>
</tbody>
</table>

### EmailInput

The `EmailInput` entity contains information for registering a new email
address.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>email</code></p></td>
<td></td>
<td><p>The email address. If provided, must match the email address from the URL.</p></td>
</tr>
<tr class="even">
<td><p><code>preferred</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether the new email address should become the preferred email address of the user (only supported if <code>no_confirmation</code> is set or if the authentication type is <code>DEVELOPMENT_BECOME_ANY_ACCOUNT</code>).</p></td>
</tr>
<tr class="odd">
<td><p><code>no_confirmation</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether the email address should be added without confirmation. In this case no verification email is sent to the user.<br />
Only Gerrit administrators are allowed to add email addresses without confirmation.</p></td>
</tr>
</tbody>
</table>

### GpgKeyInfo

The `GpgKeyInfo` entity contains information about a GPG public key.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>id</code></p></td>
<td><p>Not set in map context</p></td>
<td><p>The 8-char hex GPG key ID.</p></td>
</tr>
<tr class="even">
<td><p><code>fingerprint</code></p></td>
<td><p>Not set for deleted keys</p></td>
<td><p>The 40-char (plus spaces) hex GPG key fingerprint.</p></td>
</tr>
<tr class="odd">
<td><p><code>user_ids</code></p></td>
<td><p>Not set for deleted keys</p></td>
<td><p><a href="https://tools.ietf.org/html/rfc4880#section-5.11">OpenPGP User IDs</a> associated with the public key.</p></td>
</tr>
<tr class="even">
<td><p><code>key</code></p></td>
<td><p>Not set for deleted keys</p></td>
<td><p>ASCII armored public key material.</p></td>
</tr>
<tr class="odd">
<td><p><code>status</code></p></td>
<td><p>Not set for deleted keys</p></td>
<td><p>The result of server-side checks on the key; one of <code>BAD</code>, <code>OK</code>, or <code>TRUSTED</code>. <code>BAD</code> keys have serious problems and should not be used. If a key is <code>OK,
inspecting only that key found no problems, but the system does not fully trust
the key's origin. A `TRUSTED</code> key is valid, and the system knows enough about the key and its origin to trust it.</p></td>
</tr>
<tr class="even">
<td><p><code>problems</code></p></td>
<td><p>Not set for deleted keys</p></td>
<td><p>A list of human-readable problem strings found in the course of checking whether the key is valid and trusted.</p></td>
</tr>
</tbody>
</table>

### GpgKeysInput

The `GpgKeysInput` entity contains information for adding/deleting GPG
keys.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>add</code></p></td>
<td><p>List of ASCII armored public key strings to add.</p></td>
</tr>
<tr class="even">
<td><p><code>delete</code></p></td>
<td><p>List of <a href="#gpg-key-id"><code>\{gpg-key-id\}</code></a>s to delete.</p></td>
</tr>
</tbody>
</table>

### HttpPasswordInput

The `HttpPasswordInput` entity contains information for
setting/generating an HTTP password.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>generate</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether a new HTTP password should be generated</p></td>
</tr>
<tr class="even">
<td><p><code>http_password</code></p></td>
<td><p>optional</p></td>
<td><p>The new HTTP password. Only Gerrit administrators may set the HTTP password directly.<br />
If empty or not set and <code>generate</code> is false or not set, the HTTP password is deleted.</p></td>
</tr>
</tbody>
</table>

### OAuthTokenInfo

The `OAuthTokenInfo` entity contains information about an OAuth access
token.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>username</code></p></td>
<td></td>
<td><p>The owner of the OAuth access token.</p></td>
</tr>
<tr class="even">
<td><p><code>resource_host</code></p></td>
<td></td>
<td><p>The host of the Gerrit instance.</p></td>
</tr>
<tr class="odd">
<td><p><code>access_token</code></p></td>
<td></td>
<td><p>The actual token value.</p></td>
</tr>
<tr class="even">
<td><p><code>provider_id</code></p></td>
<td><p>optional</p></td>
<td><p>The identifier of the OAuth provider in the form <code>plugin-name:provider-name</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>expires_at</code></p></td>
<td><p>optional</p></td>
<td><p>Time of expiration of this token in milliseconds.</p></td>
</tr>
<tr class="even">
<td><p><code>type</code></p></td>
<td></td>
<td><p>The type of the OAuth access token, always <code>bearer</code>.</p></td>
</tr>
</tbody>
</table>

### PreferencesInfo

The `PreferencesInfo` entity contains information about a user’s
preferences.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>changes_per_page</code></p></td>
<td></td>
<td><p>The number of changes to show on each page. Allowed values are <code>10</code>, <code>25</code>, <code>50</code>, <code>100</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>show_site_header</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the site header should be shown.</p></td>
</tr>
<tr class="odd">
<td><p><code>use_flash_clipboard</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to use the flash clipboard widget.</p></td>
</tr>
<tr class="even">
<td><p><code>expand_inline_diffs</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to expand diffs inline instead of opening as separate page (PolyGerrit only).</p></td>
</tr>
<tr class="odd">
<td><p><code>download_scheme</code></p></td>
<td><p>optional</p></td>
<td><p>The type of download URL the user prefers to use. May be any key from the <code>schemes</code> map in <a href="rest-api-config.html#download-info">DownloadInfo</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>download_command</code></p></td>
<td></td>
<td><p>The type of download command the user prefers to use.</p></td>
</tr>
<tr class="odd">
<td><p><code>date_format</code></p></td>
<td></td>
<td><p>The format to display the date in. Allowed values are <code>STD</code>, <code>US</code>, <code>ISO</code>, <code>EURO</code>, <code>UK</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>time_format</code></p></td>
<td></td>
<td><p>The format to display the time in. Allowed values are <code>HHMM_12</code>, <code>HHMM_24</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>relative_date_in_change_table</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to show relative dates in the changes table.</p></td>
</tr>
<tr class="even">
<td><p><code>diff_view</code></p></td>
<td></td>
<td><p>The type of diff view to show. Allowed values are <code>SIDE_BY_SIDE</code>, <code>UNIFIED_DIFF</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>size_bar_in_change_table</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to show the change sizes as colored bars in the change table.</p></td>
</tr>
<tr class="even">
<td><p><code>legacycid_in_change_table</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to show change number in the change table.</p></td>
</tr>
<tr class="odd">
<td><p><code>review_category_strategy</code></p></td>
<td></td>
<td><p>The strategy used to displayed info in the review category column. Allowed values are <code>NONE</code>, <code>NAME</code>, <code>EMAIL</code>, <code>USERNAME</code>, <code>ABBREV</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>mute_common_path_prefixes</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to mute common path prefixes in file names in the file table.</p></td>
</tr>
<tr class="odd">
<td><p><code>signed_off_by</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to insert Signed-off-by footer in changes created with the inline edit feature.</p></td>
</tr>
<tr class="even">
<td><p><code>my</code></p></td>
<td></td>
<td><p>The menu items of the <code>MY</code> top menu as a list of <a href="rest-api-config.html#top-menu-item-info">TopMenuItemInfo</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>change_table</code></p></td>
<td></td>
<td><p>The columns to display in the change table (PolyGerrit only). The default is empty, which will default columns as determined by the frontend.</p></td>
</tr>
<tr class="even">
<td><p><code>url_aliases</code></p></td>
<td><p>optional</p></td>
<td><p>A map of URL path pairs, where the first URL path is an alias for the second URL path.</p></td>
</tr>
<tr class="odd">
<td><p><code>email_strategy</code></p></td>
<td></td>
<td><p>The type of email strategy to use. On <code>ENABLED</code>, the user will receive emails from Gerrit. On <code>CC_ON_OWN_COMMENTS</code> the user will also receive emails for their own comments. On <code>DISABLED</code> the user will not receive any email notifications from Gerrit. Allowed values are <code>ENABLED</code>, <code>CC_ON_OWN_COMMENTS</code>, <code>DISABLED</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>default_base_for_merges</code></p></td>
<td></td>
<td><p>The base which should be pre-selected in the <em>Diff Against</em> drop-down list when the change screen is opened for a merge commit. Allowed values are <code>AUTO_MERGE</code> and <code>FIRST_PARENT</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>publish_comments_on_push</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to <a href="user-upload.html#publish-comments">publish draft comments</a> on push by default.</p></td>
</tr>
</tbody>
</table>

### PreferencesInput

The `PreferencesInput` entity contains information for setting the user
preferences. Fields which are not set will not be updated.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>changes_per_page</code></p></td>
<td><p>optional</p></td>
<td><p>The number of changes to show on each page. Allowed values are <code>10</code>, <code>25</code>, <code>50</code>, <code>100</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>show_site_header</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the site header should be shown.</p></td>
</tr>
<tr class="odd">
<td><p><code>use_flash_clipboard</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to use the flash clipboard widget.</p></td>
</tr>
<tr class="even">
<td><p><code>expand_inline_diffs</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to expand diffs inline instead of opening as separate page (PolyGerrit only).</p></td>
</tr>
<tr class="odd">
<td><p><code>download_scheme</code></p></td>
<td><p>optional</p></td>
<td><p>The type of download URL the user prefers to use.</p></td>
</tr>
<tr class="even">
<td><p><code>download_command</code></p></td>
<td><p>optional</p></td>
<td><p>The type of download command the user prefers to use.</p></td>
</tr>
<tr class="odd">
<td><p><code>date_format</code></p></td>
<td><p>optional</p></td>
<td><p>The format to display the date in. Allowed values are <code>STD</code>, <code>US</code>, <code>ISO</code>, <code>EURO</code>, <code>UK</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>time_format</code></p></td>
<td><p>optional</p></td>
<td><p>The format to display the time in. Allowed values are <code>HHMM_12</code>, <code>HHMM_24</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>relative_date_in_change_table</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to show relative dates in the changes table.</p></td>
</tr>
<tr class="even">
<td><p><code>diff_view</code></p></td>
<td><p>optional</p></td>
<td><p>The type of diff view to show. Allowed values are <code>SIDE_BY_SIDE</code>, <code>UNIFIED_DIFF</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>size_bar_in_change_table</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to show the change sizes as colored bars in the change table.</p></td>
</tr>
<tr class="even">
<td><p><code>legacycid_in_change_table</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to show change number in the change table.</p></td>
</tr>
<tr class="odd">
<td><p><code>review_category_strategy</code></p></td>
<td><p>optional</p></td>
<td><p>The strategy used to displayed info in the review category column. Allowed values are <code>NONE</code>, <code>NAME</code>, <code>EMAIL</code>, <code>USERNAME</code>, <code>ABBREV</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>mute_common_path_prefixes</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to mute common path prefixes in file names in the file table.</p></td>
</tr>
<tr class="odd">
<td><p><code>signed_off_by</code></p></td>
<td><p>optional</p></td>
<td><p>Whether to insert Signed-off-by footer in changes created with the inline edit feature.</p></td>
</tr>
<tr class="even">
<td><p><code>my</code></p></td>
<td><p>optional</p></td>
<td><p>The menu items of the <code>MY</code> top menu as a list of <a href="rest-api-config.html#top-menu-item-info">TopMenuItemInfo</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>change_table</code></p></td>
<td></td>
<td><p>The columns to display in the change table (PolyGerrit only). The default is empty, which will default columns as determined by the frontend.</p></td>
</tr>
<tr class="even">
<td><p><code>url_aliases</code></p></td>
<td><p>optional</p></td>
<td><p>A map of URL path pairs, where the first URL path is an alias for the second URL path.</p></td>
</tr>
<tr class="odd">
<td><p><code>email_strategy</code></p></td>
<td><p>optional</p></td>
<td><p>The type of email strategy to use. On <code>ENABLED</code>, the user will receive emails from Gerrit. On <code>CC_ON_OWN_COMMENTS</code> the user will also receive emails for their own comments. On <code>DISABLED</code> the user will not receive any email notifications from Gerrit. Allowed values are <code>ENABLED</code>, <code>CC_ON_OWN_COMMENTS</code>, <code>DISABLED</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>default_base_for_merges</code></p></td>
<td><p>optional</p></td>
<td><p>The base which should be pre-selected in the <em>Diff Against</em> drop-down list when the change screen is opened for a merge commit. Allowed values are <code>AUTO_MERGE</code> and <code>FIRST_PARENT</code>.</p></td>
</tr>
</tbody>
</table>

### QueryLimitInfo

The `QueryLimitInfo` entity contains information about the [Query
Limit](access-control.html#capability_queryLimit) of a user.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>min</code></p></td>
<td><p>Lower limit.</p></td>
</tr>
<tr class="even">
<td><p><code>max</code></p></td>
<td><p>Upper limit.</p></td>
</tr>
</tbody>
</table>

### SshKeyInfo

The `SshKeyInfo` entity contains information about an SSH key of a user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>seq</code></p></td>
<td></td>
<td><p>The sequence number of the SSH key.</p></td>
</tr>
<tr class="even">
<td><p><code>ssh_public_key</code></p></td>
<td></td>
<td><p>The complete public SSH key.</p></td>
</tr>
<tr class="odd">
<td><p><code>encoded_key</code></p></td>
<td></td>
<td><p>The encoded key.</p></td>
</tr>
<tr class="even">
<td><p><code>algorithm</code></p></td>
<td></td>
<td><p>The algorithm of the SSH key.</p></td>
</tr>
<tr class="odd">
<td><p><code>comment</code></p></td>
<td><p>optional</p></td>
<td><p>The comment of the SSH key.</p></td>
</tr>
<tr class="even">
<td><p><code>valid</code></p></td>
<td></td>
<td><p>Whether the SSH key is valid.</p></td>
</tr>
</tbody>
</table>

### StarsInput

The `StarsInput` entity contains star labels that should be added to or
removed from a change.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>add</code></p></td>
<td><p>optional</p></td>
<td><p>List of labels to add to the change.</p></td>
</tr>
<tr class="even">
<td><p><code>remove</code></p></td>
<td><p>optional</p></td>
<td><p>List of labels to remove from the change.</p></td>
</tr>
</tbody>
</table>

### UsernameInput

The `UsernameInput` entity contains information for setting the username
for an account.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>username</code></p></td>
<td><p>The new username of the account.</p></td>
</tr>
</tbody>
</table>

### ProjectWatchInfo

The `WatchedProjectsInfo` entity contains information about a project
watch for a user.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>project</code></p></td>
<td></td>
<td><p>The name of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>filter</code></p></td>
<td><p>optional</p></td>
<td><p>A filter string to be applied to the project.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_new_changes</code></p></td>
<td><p>optional</p></td>
<td><p>Notify on new changes.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_new_patch_sets</code></p></td>
<td><p>optional</p></td>
<td><p>Notify on new patch sets.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_all_comments</code></p></td>
<td><p>optional</p></td>
<td><p>Notify on comments.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_submitted_changes</code></p></td>
<td><p>optional</p></td>
<td><p>Notify on submitted changes.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_abandoned_changes</code></p></td>
<td><p>optional</p></td>
<td><p>Notify on abandoned changes.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

