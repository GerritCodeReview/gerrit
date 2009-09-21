// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.reviewdb;

import gwtorm.client.{ResultSet, OrmException}
import gimd._
import file.FileType
import inject.{Inject, Singleton}
import java.sql.Date
import jgit.{JGitBranch, JGitDatabase}
import modification.DatabaseModification
import org.spearce.jgit.lib.{RepositoryCache, Config}
import server.config.GerritServerConfig

/** A DAO for Account and AccountExternalId that delegates to Gimd database. */
@Singleton
final class UserDb @Inject()(@GerritServerConfig private val cfg: Config) {

  private val gimdDb: Database = {
    val repoLocation = cfg.getString("gerrit", null, "userDbPath")
    val repo = RepositoryCache.FileKey.lenient(new java.io.File(repoLocation)).open(true)
    new JGitDatabase(JGitBranch(repo, "refs/heads/master"))
  }

  private object AccountType extends UserType[Account] {

    override val children = Seq(new NestedMember("generalPreferences", GeneralPreferencesType))

    def fields = List(
      FieldSpec("accountId", StringField, _.sha1Id),
      FieldSpec("fullName", StringField, _.fullName),
      FieldSpec("oldAccountId", IntField, _.getRawId),
      FieldSpec("preferredEmail", StringField, _.preferredEmail),
      FieldSpec("registeredOn", (s: String, d: java.util.Date) => TimestampField(s, Timestamp(d)),
        _.registeredOn)
      )

    def toUserObject(m: Message) = {
      val oldId = m.one("oldAccountId").intField.value
      val account = Account.getInstance(oldId)
      account.fullName = m.oneOption("fullName") match {
        case Some(x) => x.stringField.value
        case None => null
      }
      account.preferredEmail = m.oneOption("preferredEmail") match {
        case Some(x) => x.stringField.value
        case None => null
      }
      account.registeredOn =
              new java.sql.Timestamp(m.one("registeredOn").timestampField.value.getTime)
      account.sha1Id = m.one("accountId").stringField.value
      val preferences = GeneralPreferencesType.
              toUserObject(m.one("generalPreferences").messageField.value)
      account.setGeneralPreferences(preferences)
      account
    }
  }

  private object GeneralPreferencesType extends UserType[AccountGeneralPreferences] {
    def fields = List(
      FieldSpec("defaultContext", IntField, _.defaultContext.toInt),
      FieldSpec("maximumPageSize", IntField, _.maximumPageSize.toInt),
      FieldSpec("showSiteHeader", StringField, _.showSiteHeader.toString),
      FieldSpec("useFlashClipboard", StringField, _.useFlashClipboard.toString)
      )

    def toUserObject(m: Message) = {
      val p = new AccountGeneralPreferences
      p.setDefaultContext(m.one("defaultContext").intField.value.toShort)
      p.setMaximumPageSize(m.one("maximumPageSize").intField.value.toShort)
      p.setShowSiteHeader(m.one("showSiteHeader").stringField.value == "true")
      p.setUseFlashClipboard(m.one("useFlashClipboard").stringField.value == "true")
      p
    }
  }


  private object AccountFileType extends FileType[Account] {
    val pathPrefix = Some("accounts/")
    val pathSuffix = None
    val userType = AccountType
    def name(m: Message) = m.one("accountId").stringField.value
  }

  private final case class SshUserName(accountId: String, sshUserName: String)

  private object SshUserNameType extends UserType[SshUserName] {

    def fields =  List(
      FieldSpec("accountId", StringField, _.accountId),
      FieldSpec("sshUserName", StringField, _.sshUserName)
    )

    def toUserObject(m: Message) =
      SshUserName(m.one("accountId").stringField.value, m.one("sshUserName").stringField.value)
  }

  private object SshUserNameFileType extends FileType[SshUserName] {
    val pathPrefix = Some("sshUserNames/")
    val pathSuffix = None
    val userType = SshUserNameType
    def name(m: Message) = m.one("accountId").stringField.value
  }

  private object ExternalIdType extends UserType[AccountExternalId] {

    def fields = List(
      FieldSpec("idSha1", StringField, _.sha1Id),
      FieldSpec("emailAddress", StringField, _.emailAddress),
      FieldSpec("oldAccountId", IntField, _.getRawAccountId),
      FieldSpec("externalId", StringField, _.getRawExternalId),
      FieldSpec("lastUsedOn", (s: String, d: java.util.Date) => TimestampField(s, Timestamp(d)),
        _.lastUsedOn)
      )

    def toUserObject(m: Message) = {
      val rawExternalId = m.one("externalId").stringField.value
      val rawOldAccountId = m.one("oldAccountId").intField.value
      val externalId = AccountExternalId.getInstance(rawOldAccountId, rawExternalId)
      externalId.emailAddress = m.oneOption("emailAddress") match {
        case Some(x) => x.stringField.value
        case None => null
      }
      externalId.sha1Id = m.one("idSha1").stringField.value
      externalId.lastUsedOn = m.oneOption("lastUsedOn") match {
        case Some(x) => new java.sql.Timestamp(x.timestampField.value.getTime)
        case None => null
      }
      externalId
    }
  }

  private object ExternalIdFileType extends FileType[AccountExternalId] {
    val pathPrefix = Some("identities/")
    val pathSuffix = None
    val userType = ExternalIdType
    def name(m: Message) = m.one("idSha1").stringField.value
  }

  @throws(classOf[OrmException])
  def byOldId(rawId: Int)(implicit s: Snapshot): Account = {
    val account = query(_.getRawId == rawId).take(2).toList match {
      case Nil => throw new OrmException("Account with key '%1s' not found.".format(rawId))
      case x :: Nil => x
      case _ =>
        throw new OrmException("Query returned wrong number of Accounts with key '%1s'.".
                                 format(rawId))
    }
    fillSshUsername(account)
  }

  @throws(classOf[OrmException])
  def byId(accountId: String)(implicit s: Snapshot) = {
    val account = query(_.sha1Id == accountId).take(2).toList match {
      case Nil => throw new OrmException("Account with key '%1s' not found.".format(accountId))
      case x :: Nil => x
      case _ =>
        throw new OrmException("Query returned wront nubmer of Accounts with key '%1s'.".
                                 format(accountId))
    }
    fillSshUsername(account)
  }

  def byPreferredEmail(email: String)(implicit s: Snapshot): ResultSet[Account] =
    toResultSet(query(_.preferredEmail == email).take(2).map(fillSshUsername(_)))

  def byFullName(name: String)(implicit s: Snapshot): ResultSet[Account] =
    toResultSet(query(_.fullName == name).take(2).map(fillSshUsername(_)))

  @throws(classOf[OrmException])
  def bySshUserName(name: String)(implicit s: Snapshot): Account =
    findSshUserName(_.sshUserName == name) match {
      case Some(x) => byId(x.accountId)
      case None => throw new OrmException("Account with sshUserName '%1s' not found.")
    }

  def suggestByFullName(nameA: String, nameB: String, limit: Int)(implicit s: Snapshot):
    ResultSet[Account] = {
    val accounts = query(x => x.fullName >= nameA && x.fullName <= nameB).take(limit).toList
    toResultSet(accounts.sort((x, y) => x.fullName < y.fullName).map(fillSshUsername(_)).elements)
  }

  def suggestByPreferredEmail(emailA: String, emailB: String, limit: Int)(implicit s: Snapshot):
    ResultSet[Account] = {
    val p = (x: Account) => x.preferredEmail >= emailA && x.preferredEmail <= emailB
    val accounts = query(p).take(limit).toList
    toResultSet(accounts.sort((x, y) => x.preferredEmail < y.preferredEmail).
            map(fillSshUsername(_)).elements)
  }

  def suggestBySshUserName(nameA: String, nameB: String, limit: Int)(implicit s: Snapshot):
    ResultSet[Account] = {
    val accounts = query(x => x.sshUserName >= nameA && x.sshUserName <= nameB).take(limit).toList
    toResultSet(accounts.sort((x: Account, y: Account) => x.sshUserName < y.sshUserName)
            .map(fillSshUsername(_)).elements)
  }

  //-------------------------------------- ExternalId from here

  @throws(classOf[OrmException])
  def byExternalId(externalId: String)(implicit s: Snapshot): AccountExternalId =
    queryExtId(_.getRawExternalId == externalId).take(2).toList match {
      case Nil => throw new OrmException("ExternalId with key '%1s' not found.".format(externalId))
      case x :: Nil => x
      case _ =>
        throw new OrmException("Query returned more than one ExeternalId for key '%1s'.".
                format(externalId))
    }

  def byAccount(rawId: Int)(implicit s: Snapshot): ResultSet[AccountExternalId] =
    toResultSet(queryExtId(_.getRawAccountId == rawId))

  def byEmail(email: String)(implicit s: Snapshot): ResultSet[AccountExternalId] =
    toResultSet(queryExtId(_.emailAddress == email))

  def suggestByEmail(emailA: String, emailB: String, limit: Int)(implicit s: Snapshot):
    ResultSet[AccountExternalId] = {
    val accounts = queryExtId(x => x.emailAddress >= emailA && x.emailAddress <= emailB).
          take(limit).toList
    toResultSet(accounts.sort((x, y) => x.emailAddress < y.emailAddress).elements)
  }

  def emptyModification = DatabaseModification.empty

  def insertAccount(m: DatabaseModification, a: Account) = m.insertFile(AccountFileType, a)

  def updateAccount(a: Account, m: DatabaseModification, s: Snapshot) = {
    s.query(AccountFileType, (x: Account) => x.getRawId == a.getRawId).toList match {
      case (handle, _) :: Nil => {
        m.modify(handle, a)
      }
      case xs => throw new OrmException(("Query returned more than one Account with id '%1s'. " +
              "Returned result is %2s").format(a.getRawId, xs))
    }
  }

  def updateExternalId(extId: AccountExternalId, m: DatabaseModification, s: Snapshot) = {
    val p = (x: AccountExternalId) => x.getRawExternalId == extId.getRawExternalId
    s.query(ExternalIdFileType, p).toList match {
      case (handle, _) :: Nil => {
        m.modify(handle, extId)
      }
      case xs => throw new OrmException(("Query returned more than one AccountExternalId with " +
              "id '%1s'. Returned result is %2s").format(extId.getRawExternalId, xs))
    }
  }

  def insertExternalId(m: DatabaseModification, extId: AccountExternalId) =
    m.insertFile(ExternalIdFileType, extId)

  @throws(classOf[OrmException])
  def modify(f: Snapshot => DatabaseModification) = wrapException(gimdDb.modify(f))

  @throws(classOf[OrmException])
  def modifyAndReturn[T](f: Snapshot => (DatabaseModification, T)) =
    wrapException(gimdDb.modifyAndReturn(f))

  def latestSnapshot = gimdDb.latestSnapshot

  private def toResultSet[T](it: Iterator[T]): ResultSet[T] = new ResultSet[T] {
    def iterator = toJavaIterator(it)
    def toList = java.util.Arrays.asList(it.toList.toArray: _*)
    def close = ()
  }

  private def toJavaIterator[T](it: Iterator[T]): java.util.Iterator[T] = new java.util.Iterator[T] {
    def hasNext = it.hasNext
    def next = it.next
    def remove = throw new UnsupportedOperationException("This is object wrapping immutable Scala" +
            "iterator that does not support remove operation.")
  }

  private def fillSshUsername(a: Account)(implicit snapshot: Snapshot): Account = {
    findSshUserName(_.accountId == a.sha1Id).
            foreach((x: SshUserName) => a.sshUserName = x.sshUserName)
    a
  }

  private def findSshUserName(p: SshUserName => Boolean)(implicit s: Snapshot) =
    s.query(SshUserNameFileType, p).map(_._2).take(1).toList match {
      case Nil => None
      case x :: xs => Some(x)
    }

  private def query(p: Account => Boolean)(implicit s: Snapshot): Iterator[Account] =
    s.query(AccountFileType, p).map(_._2)

  private def queryExtId(p: AccountExternalId => Boolean)(implicit s: Snapshot):
    Iterator[AccountExternalId] = s.query(ExternalIdFileType, p).map(_._2)

  private def wrapException[T](what: => T): T =
    try {
      what
    } catch {
      case e: GimdException => throw new OrmException(e)
    }

}
