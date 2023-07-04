package com.google.gerrit.proto;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AvatarInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import org.junit.Test;

public class RestProtoConverterTest {

  @Test
  public void nameConvert() throws Exception {
    assertThat(RestProtoConverter.toProtoName("_accountId")).isEqualTo("account_id");
  }

  @Test
  public void protoConverterAccount() throws Exception {
    AccountInfo inf = new AccountInfo();
    inf._accountId = 123;
    inf.email = "hanwen@google.com";
    inf.name = "Han-Wen";
    inf.displayName = "Display Han-Wen";
    inf.secondaryEmails = new ArrayList<>();
    inf.secondaryEmails.add("email1");
    inf.secondaryEmails.add("email2");
    inf.avatars = new ArrayList<>();
    inf._moreAccounts = false;

    AvatarInfo aInf = new AvatarInfo();
    aInf.url = "https://a/b";
    aInf.height = 3;
    aInf.width = 3;
    inf.avatars.add(aInf);

    Message msg = RestProtoConverter.toProtoMessage(inf);
    Rest.AccountInfo protoInf = (Rest.AccountInfo) msg;
    assertThat(protoInf.getName()).isEqualTo(inf.name);
    assertThat(protoInf.getEmail()).isEqualTo(inf.email);
    assertThat(protoInf.getSecondaryEmailsCount()).isEqualTo(2);
    assertThat(protoInf.getSecondaryEmails(0)).isEqualTo("email1");
    assertThat(protoInf.getSecondaryEmails(1)).isEqualTo("email2");

    Rest.AvatarInfo avatarInfo = (Rest.AvatarInfo) RestProtoConverter.toProtoMessage(aInf);
    assertThat(protoInf.getAvatarsCount()).isEqualTo(1);
    assertThat(protoInf.getAvatars(0)).isEqualTo(avatarInfo);

    assertThat(protoInf.hasMoreAccounts()).isTrue();
    assertThat(protoInf.getMoreAccounts()).isFalse();
  }

  @Test
  public void protoLists() throws Exception {
    AccountInfo inf = new AccountInfo();
    inf.secondaryEmails = new ArrayList<>();
    inf.secondaryEmails.add("email1");
    inf.secondaryEmails.add("email2");

    Message msg = RestProtoConverter.toProtoMessage(inf);
    Rest.AccountInfo protoInf = (Rest.AccountInfo) msg;

    assertThat(protoInf.getSecondaryEmailsCount()).isEqualTo(2);
    assertThat(protoInf.getSecondaryEmails(0)).isEqualTo("email1");
    assertThat(protoInf.getSecondaryEmails(1)).isEqualTo("email2");
  }

  @Test
  public void protoConverter() throws Exception {
    Rest.ChangeInfo protoInf =
        Rest.ChangeInfo.newBuilder()
            .setId("ID")
            .setOwner(
                Rest.AccountInfo.newBuilder()
                    .setAccountId(123)
                    .setEmail("hanwen@google.com")
                    .setName("Han-Wen"))
            .setStatus(Rest.ChangeStatus.NEW)
            .build();

    ChangeInfo inf = (ChangeInfo) RestProtoConverter.fromProtoMessage(protoInf);
    assertThat(inf.id).isEqualTo("ID");
    assertThat(inf.owner._accountId).isEqualTo(123);
    assertThat(inf.status).isEqualTo(ChangeStatus.NEW);

    Rest.ChangeInfo roundtrip = (Rest.ChangeInfo) RestProtoConverter.toProtoMessage(inf);
    assertThat(roundtrip).isEqualTo(protoInf);
  }

  @Test
  public void timestampConvert() throws Exception {
    Rest.ChangeInfo protoInf =
        Rest.ChangeInfo.newBuilder()
            .setSubmitted(Timestamp.newBuilder().setSeconds(123456).setNanos(123456789).build())
            .build();
    checkRoundtrip(protoInf);
  }

  @Test
  public void booleanConvert() throws Exception {
    Rest.ChangeInfo protoInf = Rest.ChangeInfo.newBuilder().setStarred(true).build();
    checkRoundtrip(protoInf);
  }

  @Test
  public void intConvert() throws Exception {
    Rest.ChangeInfo protoInf = Rest.ChangeInfo.newBuilder().setInsertions(42).build();
    checkRoundtrip(protoInf);
  }

  void checkRoundtrip(Message msg) {
    Object jObj = RestProtoConverter.fromProtoMessage(msg);
    Message roundtrip = RestProtoConverter.toProtoMessage(jObj);
    assertThat(roundtrip).isEqualTo(msg);
  }

  @Test
  public void collectionConvert() {
    Rest.ChangeInfo protoInf =
        Rest.ChangeInfo.newBuilder().addStars("green").addStars("blue").build();
    checkRoundtrip(protoInf);
  }

  @Test
  public void mapConvert() {
    Rest.FetchInfo protoInf = Rest.FetchInfo.newBuilder().putCommands("key", "value").build();
    FetchInfo fi = (FetchInfo) RestProtoConverter.fromProtoMessage(protoInf);
    assertThat(fi.commands).isEqualTo(ImmutableMap.of("key", "value"));
    Rest.FetchInfo roundtrip = (Rest.FetchInfo) RestProtoConverter.toProtoMessage(fi);
    assertThat(roundtrip).isEqualTo(protoInf);
  }

  @Test
  public void enumConvert() throws Exception {
    Object status = ChangeStatus.NEW;
    EnumValueDescriptor vd = RestProtoConverter.toProtoEnum(status);

    assertThat(vd.getNumber()).isEqualTo(Rest.ChangeStatus.NEW.getNumber());
  }

  @Test
  public void enumConvertMessage() throws Exception {
    ChangeInfo inf = new ChangeInfo();
    inf.status = ChangeStatus.NEW;
    Rest.ChangeInfo msg = (Rest.ChangeInfo) RestProtoConverter.toProtoMessage(inf);
    assertThat(msg.getStatus()).isEqualTo(Rest.ChangeStatus.NEW);
    ChangeInfo roundtrip = (ChangeInfo) RestProtoConverter.fromProtoMessage(msg);
    assertThat(roundtrip.status).isEqualTo(inf.status);
  }
}
