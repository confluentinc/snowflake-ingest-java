package net.snowflake.ingest.streaming.internal;

import java.util.HashMap;
import java.util.Map;

/** Utility class to provide configurable constants with values set by Snowflake servers */
public class ParameterProvider {
  final String MAX_CHUNK_SIZE_IN_BYTES_MAP_KEY = "MAX_CHUNK_SIZE_IN_BYTES";
  final String MAX_BLOB_SIZE_IN_BYTES_MAP_KEY = "MAX_BLOB_SIZE_IN_BYTES";
  final String BUFFER_FLUSH_INTERVAL_IN_MILLIS_MAP_KEY = "BUFFER_FLUSH_INTERVAL_IN_MILLIS";
  final String BUFFER_FLUSH_CHECK_INTERVAL_IN_MILLIS_MAP_KEY =
      "BUFFER_FLUSH_CHECK_INTERVAL_IN_MILLIS";
  final String INSERT_THROTTLE_INTERVAL_IN_MILLIS_MAP_KEY = "INSERT_THROTTLE_INTERVAL_IN_MILLIS";
  final String INSERT_THROTTLE_THRESHOLD_IN_PERCENTAGE_MAP_KEY =
      "INSERT_THROTTLE_THRESHOLD_IN_PERCENTAGE";

  // Default values in the event no value is set by the server
  static final long MAX_CHUNK_SIZE_IN_BYTES_DEFAULT = 16000000L;
  static final long MAX_BLOB_SIZE_IN_BYTES_DEFAULT = 256000000L;
  static final long BUFFER_FLUSH_INTERVAL_IN_MILLIS_DEFAULT = 1000;
  static final long BUFFER_FLUSH_CHECK_INTERVAL_IN_MILLIS_DEFAULT = 100;
  static final long INSERT_THROTTLE_INTERVAL_IN_MILLIS_DEFAULT = 500;
  static final long INSERT_THROTTLE_THRESHOLD_IN_PERCENTAGE_DEFAULT = 5;

  /** Map of parameter name to parameter value. This will be set by client/configure API Call. */
  private Map<String, Object> parameterMap = new HashMap<>();

  /**
   * Construct empty ParameterProvider that will supply default values until updated by
   * client/configure API call
   */
  public ParameterProvider() {}

  /**
   * Sets the latest values from the server with data from the client configure API
   *
   * @param parameterMap Map<String, Long> of parameter name -> valuen
   */
  public void setParameterMap(Map<String, Object> parameterMap) {
    this.parameterMap = parameterMap;
  }

  /** @return Maximum chunk size in bytes */
  public long getMaxChunkSizeInBytes() {
    return (long)
        this.parameterMap.getOrDefault(
            MAX_CHUNK_SIZE_IN_BYTES_MAP_KEY, MAX_CHUNK_SIZE_IN_BYTES_DEFAULT);
  }

  /** @return Maximum blob size in bytes */
  public long getMaxBlobSizeInBytes() {
    return (long)
        this.parameterMap.getOrDefault(
            MAX_BLOB_SIZE_IN_BYTES_MAP_KEY, MAX_BLOB_SIZE_IN_BYTES_DEFAULT);
  }

  /** @return Longest interval in milliseconds between buffer flushes */
  public long getBufferFlushIntervalInMs() {
    return (long)
        this.parameterMap.getOrDefault(
            BUFFER_FLUSH_INTERVAL_IN_MILLIS_MAP_KEY, BUFFER_FLUSH_INTERVAL_IN_MILLIS_DEFAULT);
  }

  /** @return Time in milliseconds between checks to see if the buffer should be flushed */
  public long getBufferFlushCheckIntervalInMs() {
    return (long)
        this.parameterMap.getOrDefault(
            BUFFER_FLUSH_CHECK_INTERVAL_IN_MILLIS_MAP_KEY,
            BUFFER_FLUSH_CHECK_INTERVAL_IN_MILLIS_DEFAULT);
  }

  /** @return Duration in milliseconds to delay data insertion to the buffer when throttled */
  public long getInsertThrottleIntervalInMs() {
    return (long)
        this.parameterMap.getOrDefault(
            INSERT_THROTTLE_INTERVAL_IN_MILLIS_MAP_KEY, INSERT_THROTTLE_INTERVAL_IN_MILLIS_DEFAULT);
  }

  /** @return Percent of free total memory at which we throttle row inserts */
  public int getInsertThrottleThresholdInPercentage() {
    return ((Long)
            this.parameterMap.getOrDefault(
                INSERT_THROTTLE_THRESHOLD_IN_PERCENTAGE_MAP_KEY,
                INSERT_THROTTLE_THRESHOLD_IN_PERCENTAGE_DEFAULT))
        .intValue();
  }
}