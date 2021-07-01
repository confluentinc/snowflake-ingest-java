/*
 * Copyright (c) 2021 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.ingest.utils;

/** Contains all the constants needed for Streaming Ingest */
public class Constants {

  // Client level constants
  public static final String USER_NAME = "snowflake.user.name";
  public static final String ACCOUNT_URL = "snowflake.url.name";
  public static final String PRIVATE_KEY = "snowflake.private.key";
  public static final String ROLE_NAME = "snowflake.role.name";
  public static final String PRIVATE_KEY_PASSPHRASE = "snowflake.private.key.passphrase";
  public static final String JDBC_USER = "user";
  public static final String JDBC_PRIVATE_KEY = "privateKey";
  public static final String JDBC_SSL = "ssl";
  public static final long MAX_CHUNK_SIZE_IN_BYTES = 16000000L;
  public static final long RESPONSE_SUCCESS = 0L;
  public static final long BLOB_UPLOAD_TIMEOUT_IN_SEC = 10L;
  public static final String STAGE_LOCATION = "file:///tmp/streaming_ingest_tmp/";
  public static final String STAGE_NAME = "STREAMING_INGEST_STAGE";
  public static final String INTERNAL_STAGE_DB_NAME = "DB_STREAMINGINGEST";
  public static final String INTERNAL_STAGE_SCHEMA_NAME = "PUBLIC";
  public static final long CREDENTIAL_EXPIRE_IN_SEC = 30 * 60 * 1000L;
  public static final long BUFFER_FLUSH_INTERVAL_IN_MS = 500;
  public static final long BUFFER_FLUSH_CHECK_INTERVAL_IN_MS = 100;
  public static final long MAX_BLOB_SIZE_IN_BYTES = 256000000L;
  public static final byte BLOB_FORMAT_VERSION = 0;
  public static final int BLOB_TAG_SIZE_IN_BYTES = 4;
  public static final int BLOB_VERSION_SIZE_IN_BYTES = 1;
  public static final int BLOB_FILE_SIZE_SIZE_IN_BYTES = 8;
  public static final int BLOB_CHECKSUM_SIZE_IN_BYTES = 8;
  public static final int BLOB_CHUNK_METADATA_LENGTH_SIZE_IN_BYTES = 4;
  public static final long THREAD_SHUTDOWN_TIMEOUT_IN_SEC = 5L;
  public static final String BLOB_EXTENSION_TYPE = "bdec";
  public static final int MAX_THREAD_COUNT = Integer.MAX_VALUE;
  public static final String CLIENT_CONFIGURE_ENDPOINT = "/v1/streaming/client/configure/";
  public static final int COMMIT_MAX_RETRY_COUNT = 10;
  public static final int COMMIT_RETRY_INTERVAL_IN_MS = 500;
  public static final int ROW_SEQUENCER_IS_COMMITTED = 26;

  // Channel level constants
  public static final String CHANNEL_STATUS_ENDPOINT = "/v1/streaming/channels/status/";
  public static final String OPEN_CHANNEL_ENDPOINT = "/v1/streaming/channels/open/";
  public static final String REGISTER_BLOB_ENDPOINT = "/v1/streaming/channels/write/blobs/";

  public static enum WriteMode {
    CLOUD_STORAGE,
    REST_API,
  }

  // Parameters
  public static final boolean DISABLE_BACKGROUND_FLUSH = false;
  public static final boolean COMPRESS_BLOB_TWICE = false;
  public static final boolean ENABLE_PERF_MEASUREMENT = false;
  public static final boolean BLOB_NO_HEADER = true;
}
