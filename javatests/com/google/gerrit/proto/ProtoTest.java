package com.google.gerrit.proto;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AvatarInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.protobuf.Message;
import java.util.ArrayList;
import org.junit.Test;

public class ProtoTest {

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
  public void protoConverter() throws Exception {
    ChangeInfo inf = new ChangeInfo();
    inf.id = "ID";
    inf.owner = new AccountInfo();
    inf.owner._accountId = 123;
    inf.owner.email = "hanwen@google.com";
    inf.owner.name = "Han-Wen";
    inf.status = ChangeStatus.NEW;

    Rest.ChangeInfo protoCI = Rest.ChangeInfo.newBuilder().build();
    System.out.println("NAME '" + protoCI.getClass().getName() + "'");
    Message msg = RestProtoConverter.toProtoMessage(inf);

    assertThat(msg.getClass()).isEqualTo(protoCI.getClass());
    protoCI = (Rest.ChangeInfo) msg;
    assertThat(protoCI.getId()).isEqualTo(inf.id);
    assertThat(protoCI.hasOwner()).isTrue();
    assertThat(protoCI.getOwner().getName()).isEqualTo(inf.owner.name);

    System.out.println("\nPROTO:\n\n" + msg.toString());
  }

  @Test
  public void enumConvert() throws Exception {
    ChangeInfo inf = new ChangeInfo();
    inf.status = ChangeStatus.NEW;
    Rest.ChangeInfo msg = (Rest.ChangeInfo) RestProtoConverter.toProtoMessage(inf);
    assertThat(msg.getStatus()).isEqualTo(Rest.ChangeStatus.NEW);
  }
}
