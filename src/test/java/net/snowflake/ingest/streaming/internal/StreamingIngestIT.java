package net.snowflake.ingest.streaming.internal;

import static net.snowflake.ingest.utils.Constants.BLOB_NO_HEADER;
import static net.snowflake.ingest.utils.Constants.COMPRESS_BLOB_TWICE;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import net.snowflake.ingest.TestUtils;
import net.snowflake.ingest.streaming.InsertValidationResponse;
import net.snowflake.ingest.streaming.OpenChannelRequest;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestChannel;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClientFactory;
import net.snowflake.ingest.utils.ErrorCode;
import net.snowflake.ingest.utils.SFException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Example streaming ingest sdk integration test */
public class StreamingIngestIT {
  private static final String TEST_TABLE = "STREAMING_INGEST_TEST_TABLE";
  private static final String TEST_DB = "STREAMING_INGEST_TEST_DB";
  private static final String TEST_SCHEMA = "STREAMING_INGEST_TEST_SCHEMA";

  private static final String INTERLEAVED_TABLE_PREFIX = "t_interleaved_test_";
  private static final int INTERLEAVED_CHANNEL_NUMBER = 3;

  private Properties prop;

  private SnowflakeStreamingIngestClientInternal client;
  private Connection jdbcConnection;

  @Before
  public void beforeAll() throws Exception {
    // Create a streaming ingest client
    jdbcConnection = TestUtils.getConnection();
    jdbcConnection
        .createStatement()
        .execute(String.format("create or replace database %s;", TEST_DB));
    jdbcConnection
        .createStatement()
        .execute(String.format("create or replace schema %s;", TEST_SCHEMA));
    jdbcConnection
        .createStatement()
        .execute(String.format("create or replace table %s (c1 char(10));", TEST_TABLE));
    jdbcConnection
        .createStatement()
        .execute("alter session set ENABLE_PR_37692_MULTI_FORMAT_SCANSET=true;");
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "alter database %s set ENABLE_PR_37692_MULTI_FORMAT_SCANSET=true;", TEST_DB));
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "alter table %s set ENABLE_PR_37692_MULTI_FORMAT_SCANSET=true;", TEST_TABLE));
    jdbcConnection.createStatement().execute("alter session set ENABLE_UNIFIED_TABLE_SCAN=true;");
    jdbcConnection
        .createStatement()
        .execute(String.format("use warehouse %s", TestUtils.getWarehouse()));

    prop = TestUtils.getProperties();
    client =
        (SnowflakeStreamingIngestClientInternal)
            SnowflakeStreamingIngestClientFactory.builder("client1").setProperties(prop).build();
  }

  @After
  public void afterAll() throws Exception {
    client.close();
    jdbcConnection.createStatement().execute(String.format("drop database %s", TEST_DB));
  }

  @Test
  public void testSimpleIngest() throws Exception {
    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(TEST_TABLE)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);
    for (int val = 0; val < 1000; val++) {
      Map<String, Object> row = new HashMap<>();
      row.put("c1", Integer.toString(val));
      verifyInsertValidationResponse(channel1.insertRow(row, Integer.toString(val)));
    }

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("999")) {
        ResultSet result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select count(*) from %s.%s.%s", TEST_DB, TEST_SCHEMA, TEST_TABLE));
        result.next();
        Assert.assertEquals(1000, result.getLong(1));

        ResultSet result2 =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select * from %s.%s.%s order by c1 limit 2",
                        TEST_DB, TEST_SCHEMA, TEST_TABLE));
        result2.next();
        Assert.assertEquals("0", result2.getString(1));
        result2.next();
        Assert.assertEquals("1", result2.getString(1));

        // Verify perf metrics
        if (client.getParameterProvider().hasEnabledSnowpipeStreamingMetrics()) {
          Assert.assertEquals(1, client.blobSizeHistogram.getCount());
          if (BLOB_NO_HEADER && COMPRESS_BLOB_TWICE) {
            Assert.assertEquals(3445, client.blobSizeHistogram.getSnapshot().getMax());
          } else if (BLOB_NO_HEADER) {
            Assert.assertEquals(3579, client.blobSizeHistogram.getSnapshot().getMax());
          } else if (COMPRESS_BLOB_TWICE) {
            Assert.assertEquals(3981, client.blobSizeHistogram.getSnapshot().getMax());
          } else {
            Assert.assertEquals(4115, client.blobSizeHistogram.getSnapshot().getMax());
          }
        }
        return;
      }
      Thread.sleep(500);
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testInterleavedIngest() {
    Consumer<IntConsumer> iter =
        f -> IntStream.rangeClosed(1, INTERLEAVED_CHANNEL_NUMBER).forEach(f);

    iter.accept(i -> createTableForInterleavedTest(INTERLEAVED_TABLE_PREFIX + i));

    SnowflakeStreamingIngestChannel[] channels =
        new SnowflakeStreamingIngestChannel[INTERLEAVED_CHANNEL_NUMBER];
    iter.accept(i -> channels[i - 1] = openChannel(INTERLEAVED_TABLE_PREFIX + i));

    iter.accept(
        i ->
            produceRowsForInterleavedTest(
                channels[i - 1], INTERLEAVED_TABLE_PREFIX + i, 1 << (i + 1)));
    iter.accept(i -> waitChannelFlushed(channels[i - 1], 1 << (i + 1)));

    iter.accept(i -> verifyInterleavedResult(1 << (i + 1), INTERLEAVED_TABLE_PREFIX + i));
  }

  @Test
  public void testCollation() throws Exception {
    String collationTable = "collation_table";
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "create or replace table %s (noncol char(10), col char(10) collate 'en-ci');",
                collationTable));

    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(collationTable)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);
    Map<String, Object> row = new HashMap<>();
    row.put("col", "AA");
    row.put("noncol", "AA");
    verifyInsertValidationResponse(channel1.insertRow(row, "1"));
    row.put("col", "a");
    row.put("noncol", "a");
    verifyInsertValidationResponse(channel1.insertRow(row, "2"));

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("2")) {
        ResultSet result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select min(col), min(noncol) from %s.%s.%s",
                        TEST_DB, TEST_SCHEMA, collationTable));
        result.next();
        Assert.assertEquals("a", result.getString(1));
        Assert.assertEquals("AA", result.getString(2));
        return;
      }
      Thread.sleep(1000);
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testDecimalColumnIngest() throws Exception {
    String decimalTableName = "decimal_table";
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "create or replace table %s (tinyfloat NUMBER(38,2));", decimalTableName));
    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL_DECI")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(decimalTableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);

    Map<String, Object> row = new HashMap<>();
    row.put("tinyfloat", -1.1);
    verifyInsertValidationResponse(channel1.insertRow(row, null));
    row.put("tinyfloat", 2.2);
    verifyInsertValidationResponse(channel1.insertRow(row, null));
    row.put("tinyfloat", BigInteger.valueOf(10).pow(35));
    verifyInsertValidationResponse(channel1.insertRow(row, "1"));

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("1")) {
        ResultSet result = null;
        result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select * from %s.%s.%s", TEST_DB, TEST_SCHEMA, decimalTableName));

        result.next();
        Assert.assertEquals(-1.1, result.getFloat("TINYFLOAT"), 0.001);
        result.next();
        Assert.assertEquals(2.2, result.getFloat("TINYFLOAT"), 0.001);
        result.next();
        Assert.assertEquals(
            BigInteger.valueOf(10).pow(35).floatValue(), result.getFloat("TINYFLOAT"), 10);
        return;
      } else {
        Thread.sleep(2000);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testTimeColumnIngest() throws Exception {
    String timeTableName = "time_table";
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "create or replace table %s (tsmall TIME(3), tntzsmall TIMESTAMP_NTZ(3), ttzsmall"
                    + " TIMESTAMP_TZ(3), tbig TIME(9), tntzbig TIMESTAMP_NTZ(9), ttzbig"
                    + " TIMESTAMP_TZ(9) );",
                timeTableName));
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "alter table %s set ENABLE_PR_37692_MULTI_FORMAT_SCANSET=true;", timeTableName));
    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL_TIME")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(timeTableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);

    Map<String, Object> row = new HashMap<>();
    row.put("ttzsmall", "2021-01-01 01:00:00.123 -0300");
    row.put("ttzbig", "2021-01-01 09:00:00.12345678 -0300");
    row.put("tsmall", "01:00:00.123");
    row.put("tbig", "09:00:00.12345678");
    row.put("tntzsmall", "1609462800.123");
    row.put("tntzbig", "1609462800.12345");
    verifyInsertValidationResponse(channel1.insertRow(row, null));
    row.put("ttzsmall", "2021-01-01 10:00:00.123 +0700");
    row.put("ttzbig", "2021-01-01 19:00:00.12345678 -0300");
    row.put("tsmall", "02:00:00.123");
    row.put("tbig", "10:00:00.12345678");
    row.put("tntzsmall", "1709462800.123");
    row.put("tntzbig", "1709462800.12345");
    verifyInsertValidationResponse(channel1.insertRow(row, null));
    row.put("ttzsmall", "2021-01-01 05:00:00 +0100");
    row.put("ttzbig", "2021-01-01 23:00:00.12345678 -0300");
    row.put("tsmall", "03:00:00.123");
    row.put("tbig", "11:00:00.12345678");
    row.put("tntzsmall", "1809462800.123");
    row.put("tntzbig", "2031-01-01 09:00:00.12345678");
    verifyInsertValidationResponse(channel1.insertRow(row, "1"));

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("1")) {
        ResultSet result = null;
        result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format("select * from %s.%s.%s", TEST_DB, TEST_SCHEMA, timeTableName));

        result.next();
        Assert.assertEquals(1609473600123l, result.getTimestamp("TTZSMALL").getTime());
        Assert.assertEquals(1609502400123l, result.getTimestamp("TTZBIG").getTime());
        Assert.assertEquals(123456780, result.getTimestamp("TTZBIG").getNanos());
        Assert.assertEquals(3600123, result.getTimestamp("TSMALL").getTime());
        Assert.assertEquals(32400123, result.getTimestamp("TBIG").getTime());
        Assert.assertEquals(123456780, result.getTimestamp("TBIG").getNanos());
        Assert.assertEquals(1609462800123L, result.getTimestamp("TNTZSMALL").getTime());
        Assert.assertEquals(1609462800123L, result.getTimestamp("TNTZBIG").getTime());
        Assert.assertEquals(123450000, result.getTimestamp("TNTZBIG").getNanos());

        result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select "
                            + "max(ttzsmall) as mttzsmall,"
                            + "max(ttzbig) as mttzbig,"
                            + "max(tsmall) as mtsmall,"
                            + "max(tbig) as mtbig,"
                            + "max(tntzsmall) as mtntzsmall,"
                            + "max(tntzbig) as mtntzbig"
                            + " from %s.%s.%s",
                        TEST_DB, TEST_SCHEMA, timeTableName));

        result.next();
        Assert.assertEquals(1609473600123L, result.getTimestamp("MTTZSMALL").getTime());
        Assert.assertEquals(1609552800123L, result.getTimestamp("MTTZBIG").getTime());
        Assert.assertEquals(123456780, result.getTimestamp("MTTZBIG").getNanos());
        Assert.assertEquals(10800123, result.getTimestamp("MTSMALL").getTime());
        Assert.assertEquals(39600123, result.getTimestamp("MTBIG").getTime());
        Assert.assertEquals(123456780, result.getTimestamp("MTBIG").getNanos());
        Assert.assertEquals(1809462800123L, result.getTimestamp("MTNTZSMALL").getTime());
        Assert.assertEquals(1925024400123L, result.getTimestamp("MTNTZBIG").getTime());
        Assert.assertEquals(123456780, result.getTimestamp("MTNTZBIG").getNanos());

        return;
      } else {
        Thread.sleep(2000);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testMultiColumnIngest() throws Exception {
    String multiTableName = "multi_column";
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "create or replace table %s (s text, i integer, f float, var variant, t"
                    + " timestamp_ntz, tinyfloat NUMBER(3,1), d DATE);",
                multiTableName));
    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL_MULTI")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(multiTableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);
    long timestamp = System.currentTimeMillis();
    timestamp = timestamp / 1000;
    Map<String, Object> row = new HashMap<>();
    row.put("s", "honk");
    row.put("i", 1);
    row.put("f", 3.14);
    row.put("tinyfloat", 1.1);
    row.put("var", "{\"e\":2.7}");
    row.put("t", timestamp);
    row.put("d", "1967-06-23 01:01:01");
    verifyInsertValidationResponse(channel1.insertRow(row, "1"));

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("1")) {
        ResultSet result = null;
        result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format("select * from %s.%s.%s", TEST_DB, TEST_SCHEMA, multiTableName));

        result.next();
        Assert.assertEquals("honk", result.getString("S"));
        Assert.assertEquals(1, result.getLong("I"));
        Assert.assertEquals(3.14, result.getFloat("F"), 0.0001);
        Assert.assertEquals(1.1, result.getFloat("TINYFLOAT"), 0.001);
        Assert.assertEquals("{\n" + "  \"e\": 2.7\n" + "}", result.getString("VAR"));
        Assert.assertEquals(timestamp * 1000, result.getTimestamp("T").getTime());
        Assert.assertEquals(-923, TimeUnit.MILLISECONDS.toDays(result.getDate("D").getTime()));
        return;
      } else {
        Thread.sleep(2000);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testNullableColumns() throws Exception {
    String multiTableName = "multi_column";
    jdbcConnection
        .createStatement()
        .execute(
            String.format(
                "create or replace table %s (s text, notnull text NOT NULL);", multiTableName));
    OpenChannelRequest request1 =
        OpenChannelRequest.builder("CHANNEL_MULTI1")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(multiTableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel1 = client.openChannel(request1);

    OpenChannelRequest request2 =
        OpenChannelRequest.builder("CHANNEL_MULTI2")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(multiTableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel2 = client.openChannel(request2);

    Map<String, Object> row1 = new HashMap<>();
    row1.put("s", "honk");
    row1.put("notnull", "1");
    verifyInsertValidationResponse(channel1.insertRow(row1, "1"));

    Map<String, Object> row2 = new HashMap<>();
    row2.put("s", null);
    row2.put("notnull", "2");
    verifyInsertValidationResponse(channel1.insertRow(row2, "2"));

    Map<String, Object> row3 = new HashMap<>();
    row3.put("notnull", "3");
    verifyInsertValidationResponse(channel1.insertRow(row3, "3"));

    verifyInsertValidationResponse(channel2.insertRow(row3, "1"));

    for (int i = 1; i < 15; i++) {
      if (channel1.getLatestCommittedOffsetToken() != null
          && channel1.getLatestCommittedOffsetToken().equals("3")
          && channel2.getLatestCommittedOffsetToken() != null
          && channel2.getLatestCommittedOffsetToken().equals("1")) {
        ResultSet result = null;
        result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select * from %s.%s.%s order by notnull",
                        TEST_DB, TEST_SCHEMA, multiTableName));

        result.next();
        Assert.assertEquals("honk", result.getString("S"));
        Assert.assertEquals("1", result.getString("NOTNULL"));

        result.next();
        Assert.assertNull(result.getString("S"));
        Assert.assertEquals("2", result.getString("NOTNULL"));

        result.next();
        Assert.assertNull(result.getString("S"));
        Assert.assertEquals("3", result.getString("NOTNULL"));

        result.next();
        Assert.assertNull(result.getString("S"));
        Assert.assertEquals("3", result.getString("NOTNULL"));
        return;
      } else {
        Thread.sleep(2000);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  @Test
  public void testMultiThread() throws Exception {
    String multiThreadTable = "multi_thread";
    jdbcConnection
        .createStatement()
        .execute(
            String.format("create or replace table %s (numcol NUMBER(10,2));", multiThreadTable));
    int numThreads = 20;
    int numRows = 1000000;
    ExecutorService testThreadPool = Executors.newFixedThreadPool(numThreads);
    CompletableFuture[] futures = new CompletableFuture[numThreads];
    List<SnowflakeStreamingIngestChannel> channelList = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      final String channelName = "CHANNEL" + i;
      futures[i] =
          CompletableFuture.runAsync(
              () -> {
                OpenChannelRequest request =
                    OpenChannelRequest.builder(channelName)
                        .setDBName(TEST_DB)
                        .setSchemaName(TEST_SCHEMA)
                        .setTableName(multiThreadTable)
                        .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
                        .build();
                SnowflakeStreamingIngestChannel channel = client.openChannel(request);
                channelList.add(channel);
                for (int val = 1; val <= numRows; val++) {
                  Map<String, Object> row = new HashMap<>();
                  row.put("numcol", val);
                  verifyInsertValidationResponse(channel.insertRow(row, Integer.toString(val)));
                }
              },
              testThreadPool);
    }
    CompletableFuture joined = CompletableFuture.allOf(futures);
    joined.get();

    for (int i = 1; i < 15; i++) {
      if (channelList.stream()
          .allMatch(
              c ->
                  c.getLatestCommittedOffsetToken() != null
                      && c.getLatestCommittedOffsetToken().equals(Integer.toString(numRows)))) {
        ResultSet result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select count(*), max(numcol), min(numcol) from %s.%s.%s",
                        TEST_DB, TEST_SCHEMA, multiThreadTable));

        result.next();
        Assert.assertEquals(numRows * numThreads, result.getInt(1));
        Assert.assertEquals(Double.valueOf(numRows), result.getDouble(2), 10);
        Assert.assertEquals(1D, result.getDouble(3), 10);
        return;
      } else {
        Thread.sleep(3000);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  /**
   * Tests client's handling of invalidated channels
   *
   * @throws Exception
   */
  @Test
  public void testTwoClientsOneChannel() throws Exception {
    SnowflakeStreamingIngestClientInternal clientA =
        (SnowflakeStreamingIngestClientInternal)
            SnowflakeStreamingIngestClientFactory.builder("clientA").setProperties(prop).build();
    SnowflakeStreamingIngestClientInternal clientB =
        (SnowflakeStreamingIngestClientInternal)
            SnowflakeStreamingIngestClientFactory.builder("clientB").setProperties(prop).build();

    OpenChannelRequest requestA =
        OpenChannelRequest.builder("CHANNEL")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(TEST_TABLE)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channelA = clientA.openChannel(requestA);
    Map<String, Object> row = new HashMap<>();
    row.put("c1", "1");
    verifyInsertValidationResponse(channelA.insertRow(row, "1"));
    clientA.flush(false).get();

    // ClientB opens channel and invalidates ClientA
    SnowflakeStreamingIngestChannel channelB = clientB.openChannel(requestA);
    row.put("c1", "2");
    verifyInsertValidationResponse(channelB.insertRow(row, "2"));
    clientB.flush(false).get();

    // ClientA tries to write, but will fail to register because it's invalid
    row.put("c1", "3");
    verifyInsertValidationResponse(channelA.insertRow(row, "3"));
    clientA.flush(false).get();

    // ClientA will error trying to write to invalid channel
    try {
      row.put("c1", "4");
      verifyInsertValidationResponse(channelA.insertRow(row, "4"));
      Assert.fail();
    } catch (SFException e) {
      Assert.assertEquals(ErrorCode.INVALID_CHANNEL.getMessageCode(), e.getVendorCode());
    }

    // ClientA will not be kept down, reopens the channel, invalidating ClientB
    SnowflakeStreamingIngestChannel channelA2 = clientA.openChannel(requestA);

    // ClientB tries to write but the register will be rejected, invalidating the channel
    row.put("c1", "5");
    verifyInsertValidationResponse(channelB.insertRow(row, "5"));
    clientB.flush(false).get();

    // ClientB fails to write to invalid channel
    try {
      row.put("c1", "6");
      verifyInsertValidationResponse(channelB.insertRow(row, "6"));
      Assert.fail();
    } catch (SFException e) {
      Assert.assertEquals(ErrorCode.INVALID_CHANNEL.getMessageCode(), e.getVendorCode());
    }

    // ClientA is victorious, writes to table
    row.put("c1", "7");
    verifyInsertValidationResponse(channelA2.insertRow(row, "7"));
    clientA.flush(false).get();
    row.put("c1", "8");
    verifyInsertValidationResponse(channelA2.insertRow(row, "8"));

    for (int i = 1; i < 15; i++) {
      if (channelA2.getLatestCommittedOffsetToken() != null
          && channelA2.getLatestCommittedOffsetToken().equals("8")) {
        ResultSet result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select count(*) from %s.%s.%s", TEST_DB, TEST_SCHEMA, TEST_TABLE));
        result.next();
        Assert.assertEquals(4, result.getLong(1));

        ResultSet result2 =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select * from %s.%s.%s order by c1", TEST_DB, TEST_SCHEMA, TEST_TABLE));
        result2.next();
        Assert.assertEquals("1", result2.getString(1));
        result2.next();
        Assert.assertEquals("2", result2.getString(1));
        result2.next();
        Assert.assertEquals("7", result2.getString(1));
        result2.next();
        Assert.assertEquals("8", result2.getString(1));
        return;
      }
      Thread.sleep(500);
    }
  }

  @Test
  public void testAbortOnErrorOption() throws Exception {
    String onErrorOptionTable = "abort_on_error_option";
    jdbcConnection
        .createStatement()
        .execute(String.format("create or replace table %s (c1 int);", onErrorOptionTable));

    OpenChannelRequest request =
        OpenChannelRequest.builder("CHANNEL")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(onErrorOptionTable)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.ABORT)
            .build();

    // Open a streaming ingest channel from the given client
    SnowflakeStreamingIngestChannel channel = client.openChannel(request);
    Map<String, Object> row1 = new HashMap<>();
    row1.put("c1", 1);
    channel.insertRow(row1, "1");
    Map<String, Object> row2 = new HashMap<>();
    row2.put("c1", 2);
    channel.insertRow(row2, "2");
    Map<String, Object> row3 = new HashMap<>();
    row3.put("c1", "a");
    try {
      channel.insertRow(row3, "3");
      Assert.fail("insert should fail");
    } catch (SFException e) {
      Assert.assertEquals(ErrorCode.INVALID_ROW.getMessageCode(), e.getVendorCode());
    }
    try {
      channel.insertRows(Arrays.asList(row1, row2, row3), "6");
      Assert.fail("insert should fail");
    } catch (SFException e) {
      Assert.assertEquals(ErrorCode.INVALID_ROW.getMessageCode(), e.getVendorCode());
    }
    Map<String, Object> row7 = new HashMap<>();
    row7.put("c1", 7);
    channel.insertRow(row7, "7");

    for (int i = 1; i < 15; i++) {
      if (channel.getLatestCommittedOffsetToken() != null
          && channel.getLatestCommittedOffsetToken().equals("7")) {
        ResultSet result =
            jdbcConnection
                .createStatement()
                .executeQuery(
                    String.format(
                        "select count(c1), min(c1), max(c1) from %s.%s.%s",
                        TEST_DB, TEST_SCHEMA, onErrorOptionTable));
        result.next();
        Assert.assertEquals("3", result.getString(1));
        Assert.assertEquals("1", result.getString(2));
        Assert.assertEquals("7", result.getString(3));
        return;
      }
      Thread.sleep(1000);
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  /** Verify the insert validation response and throw the exception if needed */
  private void verifyInsertValidationResponse(InsertValidationResponse response) {
    if (response.hasErrors()) {
      throw response.getInsertErrors().get(0).getException();
    }
  }

  private void createTableForInterleavedTest(String tableName) {
    try {
      jdbcConnection
          .createStatement()
          .execute(
              String.format(
                  "create or replace table %s (num NUMBER(38,0 ), str VARCHAR);", tableName));
    } catch (SQLException e) {
      throw new RuntimeException("Cannot create table " + tableName, e);
    }
  }

  private SnowflakeStreamingIngestChannel openChannel(String tableName) {
    OpenChannelRequest request =
        OpenChannelRequest.builder("CHANNEL")
            .setDBName(TEST_DB)
            .setSchemaName(TEST_SCHEMA)
            .setTableName(tableName)
            .setOnErrorOption(OpenChannelRequest.OnErrorOption.CONTINUE)
            .build();

    // Open a streaming ingest channel from the given client
    return client.openChannel(request);
  }

  private void produceRowsForInterleavedTest(
      SnowflakeStreamingIngestChannel channel, String tableName, int rowNumber) {
    // Insert a few rows into the channel
    for (int val = 0; val < rowNumber; val++) {
      Map<String, Object> row = new HashMap<>();
      row.put("num", val);
      row.put("str", tableName + "_value_" + val);
      verifyInsertValidationResponse(channel.insertRow(row, Integer.toString(val)));
    }
  }

  private void waitChannelFlushed(SnowflakeStreamingIngestChannel channel, int numberOfRows) {
    for (int i = 1; i < 15; i++) {
      if (channel.getLatestCommittedOffsetToken() != null
          && channel.getLatestCommittedOffsetToken().equals(Integer.toString(numberOfRows - 1))) {
        return;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(
            "Interrupted waitChannelFlushed for " + numberOfRows + " rows", e);
      }
    }
    Assert.fail("Row sequencer not updated before timeout");
  }

  private void verifyInterleavedResult(int rowNumber, String tableName) {
    ResultSet result;
    try {
      result =
          jdbcConnection
              .createStatement()
              .executeQuery(
                  String.format(
                      "select * from %s.%s.%s order by num", TEST_DB, TEST_SCHEMA, tableName));
      for (int val = 0; val < rowNumber; val++) {
        result.next();
        Assert.assertEquals(val, result.getLong("NUM"));
        Assert.assertEquals(tableName + "_value_" + val, result.getString("STR"));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Cannot verifyInterleavedResult for " + tableName, e);
    }
  }
}
