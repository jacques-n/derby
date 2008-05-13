/*

Derby - Class org.apache.derbyTesting.perf.clients.SingleRecordFiller

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

package org.apache.derbyTesting.perf.clients;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Random;

/**
 * Class which generates and populates tables that can be used by
 * {@code SingleRecordSelectClient} and {@code SingleRecordUpdateClient}.
 * This tables contain rows with an int column (id) and a varchar(100) column
 * (text). The id column is declared as primary key.
 */
public class SingleRecordFiller implements DBFiller {

    private final int numberOfTables;
    private final int tableSize;
    private final int dataType;
    private final String dataTypeString;

    static final int TEXT_SIZE = 100;

    /**
     * Generate a filler that creates the specified number of tables, each of
     * which contains the specified number of records.
     *
     * @param records the number of records in each table
     * @param tables the number of tables to create
     * @param type which SQL type to store the text as (one of
     * {@code java.sql.Types.VARCHAR}, {@code java.sql.Types.BLOB} and
     * {@code java.sql.Types.CLOB}.
     */
    public SingleRecordFiller(int records, int tables, int type) {
        tableSize = records;
        numberOfTables = tables;
        dataType = type;
        switch (type) {
            case Types.VARCHAR:
                dataTypeString = "VARCHAR";
                break;
            case Types.BLOB:
                dataTypeString = "BLOB";
                break;
            case Types.CLOB:
                dataTypeString = "CLOB";
                break;
            default:
                throw new IllegalArgumentException("type = " + type);
        }
    }

    public void fill(Connection c) throws SQLException {
        c.setAutoCommit(false);
        Statement s = c.createStatement();
        for (int table = 0; table < numberOfTables; table++) {
            String tableName = getTableName(tableSize, table, dataType);
            WisconsinFiller.dropTable(c, tableName);
            s.executeUpdate(
                    "CREATE TABLE " + tableName + "(ID INT PRIMARY KEY, " +
                    "TEXT " + dataTypeString + "(" + TEXT_SIZE + "))");

            PreparedStatement ps =
                c.prepareStatement("INSERT INTO " + tableName +
                                   "(ID, TEXT) VALUES (?, ?)");

            for (int i = 0; i < tableSize; i++) {
                ps.setInt(1, i);
                if (dataType == Types.VARCHAR) {
                    ps.setString(2, randomString(i));
                } else if (dataType == Types.CLOB) {
                    StringReader reader = new StringReader(randomString(i));
                    ps.setCharacterStream(2, reader, TEXT_SIZE);
                } else if (dataType == Types.BLOB) {
                    ByteArrayInputStream stream =
                            new ByteArrayInputStream(randomBytes(i));
                    ps.setBinaryStream(2, stream, TEXT_SIZE);
                }
                ps.executeUpdate();
                if ((i % 1000) == 0) {
                    c.commit();
                }
            }

            ps.close();
            c.commit();
        }

        s.close();
    }

    private static final String[] RANDOM_STRINGS = new String[16];
    private static final byte[][] RANDOM_BYTES = new byte[16][TEXT_SIZE];
    static {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz" +
                                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                "01234567890_-+*/(){}[]&%$#!";
        final Random r = new Random();
        for (int i = 0; i < RANDOM_STRINGS.length; i++) {
            StringBuffer buf = new StringBuffer(TEXT_SIZE);
            for (int x = 0; x < TEXT_SIZE; x++) {
                buf.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            RANDOM_STRINGS[i] = buf.toString();
            for (int j = 0; j < TEXT_SIZE; j++) {
                RANDOM_BYTES[i][j] = (byte) RANDOM_STRINGS[i].charAt(j);
            }
        }
    }

    /**
     * Pick a random string.
     *
     * @param seed a seed used to decide which random string to pick
     * @return a (somewhat) random string
     */
    static String randomString(int seed) {
        return RANDOM_STRINGS[(seed & 0x7fffffff) % RANDOM_STRINGS.length];
    }

    /**
     * Pick a random byte string.
     *
     * @param seed a seed used to decide which random string to pick
     * @return a (somewhat) random sequence of bytes
     */
    static byte[] randomBytes(int seed) {
        return RANDOM_BYTES[(seed & 0x7fffffff) % RANDOM_BYTES.length];
    }

    /**
     * Get the name of a table generated by this class.
     *
     * @param records the number of records in the table
     * @param table the number of the table, between 0 (inclusive) and the
     * total number of tables (exclusive)
     * @param dataType the {@code java.sql.Types} constant specifying the
     * data type of the text column
     * @return the name of the table specified by the arguments
     */
    static String getTableName(int records, int table, int dataType) {
        String suffix;
        if (dataType == Types.VARCHAR) {
            suffix = "";
        } else if (dataType == Types.BLOB) {
            suffix = "_BLOB";
        } else if (dataType == Types.CLOB) {
            suffix = "_CLOB";
        } else {
            throw new IllegalArgumentException("dataType = " + dataType);
        }
        return "SINGLE_RECORD_" + records + "_" + table + suffix;
    }
}