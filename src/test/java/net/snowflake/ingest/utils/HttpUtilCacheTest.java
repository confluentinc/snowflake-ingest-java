package net.snowflake.ingest.utils;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.Properties;
import net.snowflake.client.core.SFSessionProperty;
import net.snowflake.client.jdbc.internal.apache.http.impl.client.CloseableHttpClient;
import org.junit.After;
import org.junit.Test;

/**
 * Test class for HttpUtil caching functionality. Tests verify that HTTP clients are properly cached
 * based on account name and proxy settings. Note: Both JDBC and streaming clients now use unified
 * SFSessionProperty constants for proxy configuration.
 */
public class HttpUtilCacheTest {

  @After
  public void tearDown() {
    // Clean up after each test
    HttpUtil.closeAllHttpClients();
  }

  @Test
  public void testHttpClientCachingWithSameAccount() {
    String accountName = "testaccount";

    // Get HTTP client twice with same account
    CloseableHttpClient client1 = HttpUtil.getHttpClient(accountName);
    CloseableHttpClient client2 = HttpUtil.getHttpClient(accountName);

    // Should return the same instance
    assertSame("HTTP clients should be the same for same account", client1, client2);
  }

  @Test
  public void testHttpClientCachingWithDifferentAccounts() {
    String accountName1 = "testaccount1";
    String accountName2 = "testaccount2";

    // Get HTTP clients for different accounts
    CloseableHttpClient client1 = HttpUtil.getHttpClient(accountName1);
    CloseableHttpClient client2 = HttpUtil.getHttpClient(accountName2);

    // Should return different instances
    assertNotSame("HTTP clients should be different for different accounts", client1, client2);
  }

  @Test
  public void testHttpClientCachingWithSameProxySettings() {
    String accountName = "testaccount";
    Properties proxyProps = new Properties();
    proxyProps.setProperty(SFSessionProperty.USE_PROXY.getPropertyKey(), "true");
    proxyProps.setProperty(SFSessionProperty.PROXY_HOST.getPropertyKey(), "proxy.example.com");
    proxyProps.setProperty(SFSessionProperty.PROXY_PORT.getPropertyKey(), "8080");

    // Get HTTP client twice with same proxy settings
    CloseableHttpClient client1 = HttpUtil.getHttpClient(accountName, proxyProps);
    CloseableHttpClient client2 = HttpUtil.getHttpClient(accountName, proxyProps);

    // Should return the same instance
    assertSame("HTTP clients should be the same for same proxy settings", client1, client2);
  }

  @Test
  public void testHttpClientCachingWithDifferentProxySettings() {
    String accountName = "testaccount";

    Properties proxyProps1 = new Properties();
    proxyProps1.setProperty(SFSessionProperty.USE_PROXY.getPropertyKey(), "true");
    proxyProps1.setProperty(SFSessionProperty.PROXY_HOST.getPropertyKey(), "proxy1.example.com");
    proxyProps1.setProperty(SFSessionProperty.PROXY_PORT.getPropertyKey(), "8080");

    Properties proxyProps2 = new Properties();
    proxyProps2.setProperty(SFSessionProperty.USE_PROXY.getPropertyKey(), "true");
    proxyProps2.setProperty(SFSessionProperty.PROXY_HOST.getPropertyKey(), "proxy2.example.com");
    proxyProps2.setProperty(SFSessionProperty.PROXY_PORT.getPropertyKey(), "8080");

    // Get HTTP clients with different proxy settings
    CloseableHttpClient client1 = HttpUtil.getHttpClient(accountName, proxyProps1);
    CloseableHttpClient client2 = HttpUtil.getHttpClient(accountName, proxyProps2);

    // Should return different instances
    assertNotSame(
        "HTTP clients should be different for different proxy settings", client1, client2);
  }

  @Test
  public void testHttpClientCachingWithProxyAndNoProxy() {
    String accountName = "testaccount";

    Properties proxyProps = new Properties();
    proxyProps.setProperty(SFSessionProperty.USE_PROXY.getPropertyKey(), "true");
    proxyProps.setProperty(SFSessionProperty.PROXY_HOST.getPropertyKey(), "proxy.example.com");
    proxyProps.setProperty(SFSessionProperty.PROXY_PORT.getPropertyKey(), "8080");

    // Get HTTP client with proxy and without proxy
    CloseableHttpClient clientWithProxy = HttpUtil.getHttpClient(accountName, proxyProps);
    CloseableHttpClient clientWithoutProxy = HttpUtil.getHttpClient(accountName);

    // Should return different instances
    assertNotSame(
        "HTTP clients should be different for proxy vs no proxy",
        clientWithProxy,
        clientWithoutProxy);
  }
}
