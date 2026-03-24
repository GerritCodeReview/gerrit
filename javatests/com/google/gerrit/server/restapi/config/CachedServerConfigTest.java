package com.google.gerrit.server.restapi.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Ticker;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public class CachedServerConfigTest {

  private Provider<GetServerInfo> getServerInfoProvider;
  private GetServerInfo getServerInfo;

  private Provider<GetVersion> getVersionProvider;
  private GetVersion getVersion;

  private Provider<ListTopMenus> listTopMenusProvider;
  private ListTopMenus listTopMenus;

  private FakeTicker ticker;
  private CachedServerConfig cachedServerConfig;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    getServerInfoProvider = mock(Provider.class);
    getServerInfo = mock(GetServerInfo.class);
    when(getServerInfoProvider.get()).thenReturn(getServerInfo);

    getVersionProvider = mock(Provider.class);
    getVersion = mock(GetVersion.class);
    when(getVersionProvider.get()).thenReturn(getVersion);

    listTopMenusProvider = mock(Provider.class);
    listTopMenus = mock(ListTopMenus.class);
    when(listTopMenusProvider.get()).thenReturn(listTopMenus);

    ticker = new FakeTicker();
    cachedServerConfig =
        new CachedServerConfig(
            getServerInfoProvider, getVersionProvider, listTopMenusProvider, ticker);
  }

  @Test
  public void getInfo_cachesAndRefreshes() throws Exception {
    ServerInfo info = new ServerInfo();
    when(getServerInfo.apply(any(ConfigResource.class))).thenReturn(Response.ok(info));

    ServerInfo firstLoad = cachedServerConfig.getInfo();
    assertThat(firstLoad).isSameInstanceAs(info);

    ServerInfo secondLoad = cachedServerConfig.getInfo(); // Before expiration
    assertThat(secondLoad).isSameInstanceAs(info);

    verify(getServerInfoProvider, times(1)).get();

    ticker.advance(6, TimeUnit.MINUTES);

    ServerInfo info2 = new ServerInfo();
    when(getServerInfo.apply(any(ConfigResource.class))).thenReturn(Response.ok(info2));

    ServerInfo thirdLoad = cachedServerConfig.getInfo(); // After refresh threshold
    assertThat(thirdLoad).isSameInstanceAs(info2);

    verify(getServerInfoProvider, times(2)).get();
  }

  @Test
  public void getInfo_masksBackendBlipOnRefresh() throws Exception {
    ServerInfo info = new ServerInfo();
    when(getServerInfo.apply(any(ConfigResource.class))).thenReturn(Response.ok(info));

    ServerInfo firstLoad = cachedServerConfig.getInfo();
    assertThat(firstLoad).isSameInstanceAs(info);

    ticker.advance(6, TimeUnit.MINUTES);

    // Backend blip occurs
    when(getServerInfo.apply(any(ConfigResource.class)))
        .thenThrow(new RuntimeException("backend down"));

    // Expected: serves the stale config due to refreshAfterWrite exception swallowing
    ServerInfo secondLoad = cachedServerConfig.getInfo();
    assertThat(secondLoad).isSameInstanceAs(info);

    verify(getServerInfoProvider, times(2)).get();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getVersion_cachesAndRefreshes() throws Exception {
    doReturn(Response.ok("1.2.3")).when(getVersion).apply(any(ConfigResource.class));

    assertThat(cachedServerConfig.getVersion()).isEqualTo("1.2.3");
    assertThat(cachedServerConfig.getVersion()).isEqualTo("1.2.3");

    verify(getVersionProvider, times(1)).get();

    ticker.advance(6, TimeUnit.MINUTES);
    doReturn(Response.ok("1.2.4")).when(getVersion).apply(any(ConfigResource.class));

    assertThat(cachedServerConfig.getVersion()).isEqualTo("1.2.4");
    verify(getVersionProvider, times(2)).get();
  }

  @Test
  public void getTopMenus_cachesAndRefreshes() throws Exception {
    List<TopMenu.MenuEntry> menus = Collections.emptyList();
    when(listTopMenus.apply(any(ConfigResource.class))).thenReturn(Response.ok(menus));

    assertThat(cachedServerConfig.getTopMenus()).isSameInstanceAs(menus);
    assertThat(cachedServerConfig.getTopMenus()).isSameInstanceAs(menus);

    verify(listTopMenusProvider, times(1)).get();

    ticker.advance(6, TimeUnit.MINUTES);
    assertThat(cachedServerConfig.getTopMenus()).isSameInstanceAs(menus);
    verify(listTopMenusProvider, times(2)).get();
  }

  @Test
  public void getInfo_exceptionHandling() throws Exception {
    PermissionBackendException cause = new PermissionBackendException("backend down");
    when(getServerInfo.apply(any(ConfigResource.class))).thenThrow(cause);

    RestApiException thrown =
        assertThrows(RestApiException.class, () -> cachedServerConfig.getInfo());
    assertThat(thrown).hasMessageThat().contains("Failed to fetch server info");
    assertThat(thrown).hasCauseThat().isSameInstanceAs(cause);

    // Verify cache does not hold onto exceptions, retries immediately
    when(getServerInfo.apply(any(ConfigResource.class))).thenReturn(Response.ok(new ServerInfo()));
    assertThat(cachedServerConfig.getInfo()).isNotNull();
    verify(getServerInfoProvider, times(2)).get();
  }

  private static class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    public void advance(long time, TimeUnit timeUnit) {
      nanos.addAndGet(timeUnit.toNanos(time));
    }
  }
}
