/*
 * Copyright (c) 2012-2017 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.ingest.utils;

import static net.snowflake.ingest.utils.Utils.isNullOrEmpty;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines all parameters needed to create an HttpClient object for the ingest service.
 * It is used as the key for the static hashmap of reusable http clients.
 */
public class HttpClientSettingsKey implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientSettingsKey.class);

  private boolean useProxy;
  private String proxyHost = "";
  private int proxyPort = 0;
  private String nonProxyHosts = "";
  private String proxyUser = "";
  private String proxyPassword = "";
  private String accountName = "";
  private boolean disallowLocalIps;
  private boolean disallowPrivateIps;
  private boolean disallowClassEIps;
  private List<String> disallowCidrRanges;
  private List<String> allowCidrRanges;

  /** Constructor for proxy configuration */
  public HttpClientSettingsKey(
      String accountName,
      String proxyHost,
      int proxyPort,
      String nonProxyHosts,
      String proxyUser,
      String proxyPassword,
      boolean disallowLocalIps,
      boolean disallowPrivateIps,
      boolean disallowClassEIps,
      List<String> disallowCidrRanges,
      List<String> allowCidrRanges) {
    this.useProxy = true;
    this.accountName = !isNullOrEmpty(accountName) ? accountName.trim() : "";
    this.proxyHost = !isNullOrEmpty(proxyHost) ? proxyHost.trim() : "";
    this.proxyPort = proxyPort;
    this.nonProxyHosts = !isNullOrEmpty(nonProxyHosts) ? nonProxyHosts.trim() : "";
    this.proxyUser = !isNullOrEmpty(proxyUser) ? proxyUser.trim() : "";
    this.proxyPassword = !isNullOrEmpty(proxyPassword) ? proxyPassword.trim() : "";
    this.disallowLocalIps = disallowLocalIps;
    this.disallowPrivateIps = disallowPrivateIps;
    this.disallowClassEIps = disallowClassEIps;
    this.disallowCidrRanges =
        disallowCidrRanges != null ? disallowCidrRanges : Collections.emptyList();
    this.allowCidrRanges = allowCidrRanges != null ? allowCidrRanges : Collections.emptyList();

    LOGGER.trace(
        "Created HttpClientSettingsKey with proxy configuration for account: {}. Host: {}, Port:"
            + " {}, User: {}, NonProxyHosts: {}, Disallow Local IPs: {}, Disallow Private IPs: {}",
        this.accountName,
        this.proxyHost,
        this.proxyPort,
        !isNullOrEmpty(this.proxyUser) ? "set" : "not set",
        !isNullOrEmpty(this.nonProxyHosts) ? this.nonProxyHosts : "not set",
        this.disallowLocalIps,
        this.disallowPrivateIps);
  }

  /** Constructor for non-proxy configuration */
  public HttpClientSettingsKey(String accountName) {
    this.useProxy = false;
    this.accountName = !isNullOrEmpty(accountName) ? accountName.trim() : "";
    this.proxyHost = "";
    this.proxyPort = 0;
    this.nonProxyHosts = "";
    this.proxyUser = "";
    this.proxyPassword = "";
    this.disallowLocalIps = true;
    this.disallowPrivateIps = true;
    this.disallowClassEIps = true;
    this.disallowCidrRanges = Collections.emptyList();
    this.allowCidrRanges = Collections.emptyList();

    LOGGER.debug(
        "Created HttpClientSettingsKey without proxy configuration for account: {}, Disallow"
            + " Local IPs: {}, Disallow Private IPs: {}, Disallow Class E IPs: {}",
        this.accountName,
        this.disallowLocalIps,
        this.disallowPrivateIps,
        this.disallowClassEIps);
  }

  /** Convenience constructor for non-proxy configuration with IP filtering */
  public HttpClientSettingsKey(
      String accountName,
      boolean disallowLocalIps,
      boolean disallowPrivateIps,
      boolean disallowClassEIps,
      List<String> disallowCidrRanges,
      List<String> allowCidrRanges) {
    this.useProxy = false;
    this.accountName = isNullOrEmpty(accountName) ? "" : accountName.trim();
    this.proxyHost = "";
    this.proxyPort = 0;
    this.nonProxyHosts = "";
    this.proxyUser = "";
    this.proxyPassword = "";
    this.disallowLocalIps = disallowLocalIps;
    this.disallowPrivateIps = disallowPrivateIps;
    this.disallowClassEIps = disallowClassEIps;
    this.disallowCidrRanges =
        disallowCidrRanges != null ? disallowCidrRanges : Collections.emptyList();
    this.allowCidrRanges = allowCidrRanges != null ? allowCidrRanges : Collections.emptyList();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    HttpClientSettingsKey that = (HttpClientSettingsKey) obj;

    return useProxy == that.useProxy
        && proxyPort == that.proxyPort
        && disallowLocalIps == that.disallowLocalIps
        && disallowPrivateIps == that.disallowPrivateIps
        && disallowClassEIps == that.disallowClassEIps
        && Objects.equals(accountName, that.accountName)
        && Objects.equals(proxyHost, that.proxyHost)
        && Objects.equals(nonProxyHosts, that.nonProxyHosts)
        && Objects.equals(proxyUser, that.proxyUser)
        && Objects.equals(proxyPassword, that.proxyPassword)
        && Objects.equals(disallowCidrRanges, that.disallowCidrRanges)
        && Objects.equals(allowCidrRanges, that.allowCidrRanges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        useProxy,
        accountName,
        proxyHost,
        proxyPort,
        nonProxyHosts,
        proxyUser,
        proxyPassword,
        disallowLocalIps,
        disallowPrivateIps,
        disallowClassEIps,
        disallowCidrRanges,
        allowCidrRanges);
  }

  public boolean isDisallowLocalIps() {
    return disallowLocalIps;
  }

  public boolean isDisallowPrivateIps() {
    return disallowPrivateIps;
  }

  public boolean usesProxy() {
    return this.useProxy;
  }

  public String getAccountName() {
    return this.accountName;
  }

  public String getProxyHost() {
    return this.proxyHost;
  }

  public int getProxyPort() {
    return this.proxyPort;
  }

  public String getProxyUser() {
    return this.proxyUser;
  }

  public String getProxyPassword() {
    return this.proxyPassword;
  }

  public String getNonProxyHosts() {
    return this.nonProxyHosts;
  }

  public boolean isDisallowClassEIps() {
    return disallowClassEIps;
  }

  public List<String> getDisallowCidrRanges() {
    return disallowCidrRanges;
  }

  public List<String> getAllowCidrRanges() {
    return allowCidrRanges;
  }

  @Override
  public String toString() {
    return "HttpClientSettingsKey["
        + "accountName='"
        + accountName
        + '\''
        + ", useProxy="
        + useProxy
        + ", proxyHost='"
        + proxyHost
        + '\''
        + ", proxyPort="
        + proxyPort
        + ", nonProxyHosts='"
        + nonProxyHosts
        + '\''
        + ", proxyUser='"
        + proxyUser
        + '\''
        + ", proxyPassword="
        + (proxyPassword.isEmpty() ? "not set" : "set")
        + ", disallowLocalIps="
        + disallowLocalIps
        + ", disallowPrivateIps="
        + disallowPrivateIps
        + ", disallowClassEIps="
        + disallowClassEIps
        + ", disallowCidrRanges="
        + disallowCidrRanges
        + ", allowCidrRanges="
        + allowCidrRanges
        + ']';
  }
}
