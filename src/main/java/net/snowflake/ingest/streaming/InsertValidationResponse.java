/*
 * Copyright (c) 2021 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.ingest.streaming;

import java.util.ArrayList;
import java.util.List;
import net.snowflake.ingest.utils.SFException;

/**
 * Response for an insert operation into a channel, which may contain a list of {@link InsertError}
 * objects if there is any failure during insertion.
 */
public class InsertValidationResponse {
  // List of insertion errors, empty means no error
  private final List<InsertError> insertErrors = new ArrayList<>();

  /**
   * Check whether there is any error during insertion
   *
   * @return false if no insertion error, otherwise true
   */
  public boolean hasErrors() {
    return !insertErrors.isEmpty();
  }

  /** Get the list of insertion errors, the list is empty if no error */
  public List<InsertError> getInsertErrors() {
    return insertErrors;
  }

  /** Get the number of erroneous row count */
  public int getErrorRowCount() {
    return insertErrors.size();
  }

  /**
   * Add an insertion error to the error list
   *
   * @param error {@link InsertError} object which contains the row content and exception
   */
  public void addError(InsertError error) {
    insertErrors.add(error);
  }

  /** Wraps the row content and exception when there is a failure */
  public static class InsertError {
    private final Object rowContent;
    private final SFException exception;

    // Used to map this error row with original row in the insertRows Iterable.
    // i.e the rowIndex can be index 9 in the list of 10 rows.
    // index is 0 based so as to match with incoming Iterable
    private final long rowIndex;

    public InsertError(Object row, SFException exception, long rowIndex) {
      this.rowContent = row;
      this.exception = exception;
      this.rowIndex = rowIndex;
    }

    /** Get the row content */
    public Object getRowContent() {
      return this.rowContent;
    }

    /** Get the exception message */
    public String getMessage() {
      return this.exception.getMessage();
    }

    /** Get the exception */
    public SFException getException() {
      return this.exception;
    }

    /**
     * Get the rowIndex. Please note, this index is 0 based so it can be used in fetching nth row
     * from the input. ({@link SnowflakeStreamingIngestChannel#insertRows(Iterable, String)})
     */
    public long getRowIndex() {
      return rowIndex;
    }
  }
}
