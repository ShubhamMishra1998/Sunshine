
package com.codeking123.android.sunshine.data;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.codeking123.android.sunshine.data.TestUtilities.BULK_INSERT_RECORDS_TO_INSERT;
import static com.codeking123.android.sunshine.data.TestUtilities.createBulkInsertTestWeatherValues;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class TestWeatherProvider {

    /* Context used to access various parts of the system */
    private final Context mContext = InstrumentationRegistry.getTargetContext();


    @Before
    public void setUp() {
        deleteAllRecordsFromWeatherTable();
    }


    @Test
    public void testProviderRegistry() {

        String packageName = mContext.getPackageName();
        String weatherProviderClassName = WeatherProvider.class.getName();
        ComponentName componentName = new ComponentName(packageName, weatherProviderClassName);

        try {


            PackageManager pm = mContext.getPackageManager();

            /* The ProviderInfo will contain the authority, which is what we want to test */
            ProviderInfo providerInfo = pm.getProviderInfo(componentName, 0);
            String actualAuthority = providerInfo.authority;
            String expectedAuthority = WeatherContract.CONTENT_AUTHORITY;

            /* Make sure that the registered authority matches the authority from the Contract */
            String incorrectAuthority =
                    "Error: WeatherProvider registered with authority: " + actualAuthority +
                            " instead of expected authority: " + expectedAuthority;
            assertEquals(incorrectAuthority,
                    actualAuthority,
                    expectedAuthority);

        } catch (PackageManager.NameNotFoundException e) {
            String providerNotRegisteredAtAll =
                    "Error: WeatherProvider not registered at " + mContext.getPackageName();
            /*
             * This exception is thrown if the ContentProvider hasn't been registered with the
             * manifest at all. If this is the case, you need to double check your
             * AndroidManifest file
             */
            fail(providerNotRegisteredAtAll);
        }
    }


    @Test
    public void testBasicWeatherQuery() {

        /* Use WeatherDbHelper to get access to a writable database */
        WeatherDbHelper dbHelper = new WeatherDbHelper(mContext);
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        /* Obtain weather values from TestUtilities */
        ContentValues testWeatherValues = TestUtilities.createTestWeatherContentValues();

        /* Insert ContentValues into database and get a row ID back */
        long weatherRowId = database.insert(
                /* Table to insert values into */
                WeatherContract.WeatherEntry.TABLE_NAME,
                null,
                /* Values to insert into table */
                testWeatherValues);

        String insertFailed = "Unable to insert into the database";
        assertTrue(insertFailed, weatherRowId != -1);

        /* We are done with the database, close it now. */
        database.close();

        /*
         * Perform our ContentProvider query. We expect the cursor that is returned will contain
         * the exact same data that is in testWeatherValues and we will validate that in the next
         * step.
         */
        Cursor weatherCursor = mContext.getContentResolver().query(
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Columns; leaving this null returns every column in the table */
                null,
                /* Optional specification for columns in the "where" clause above */
                null,
                /* Values for "where" clause */
                null,
                /* Sort order to return in Cursor */
                null);

        /* This method will ensure that we  */
        TestUtilities.validateThenCloseCursor("testBasicWeatherQuery",
                weatherCursor,
                testWeatherValues);
    }


    @Test
    public void testBulkInsert() {

        /* Create a new array of ContentValues for weather */
        ContentValues[] bulkInsertTestContentValues = createBulkInsertTestWeatherValues();

        /*
         * TestContentObserver allows us to test weather or not notifyChange was called
         * appropriately. We will use that here to make sure that notifyChange is called when a
         * deletion occurs.
         */
        TestUtilities.TestContentObserver weatherObserver = TestUtilities.getTestContentObserver();

        /*
         * A ContentResolver provides us access to the content model. We can use it to perform
         * deletions and queries at our CONTENT_URI
         */
        ContentResolver contentResolver = mContext.getContentResolver();

        /* Register a content observer to be notified of changes to data at a given URI (weather) */
        contentResolver.registerContentObserver(
                /* URI that we would like to observe changes to */
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Whether or not to notify us if descendants of this URI change */
                true,
                /* The observer to register (that will receive notifyChange callbacks) */
                weatherObserver);

        /* bulkInsert will return the number of records that were inserted. */
        int insertCount = contentResolver.bulkInsert(
                /* URI at which to insert data */
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Array of values to insert into given URI */
                bulkInsertTestContentValues);

        /*
         * If this fails, it's likely you didn't call notifyChange in your insert method from
         * your ContentProvider.
         */
        weatherObserver.waitForNotificationOrFail();

        /*
         * waitForNotificationOrFail is synchronous, so after that call, we are done observing
         * changes to content and should therefore unregister this observer.
         */
        contentResolver.unregisterContentObserver(weatherObserver);

        /*
         * We expect that the number of test content values that we specify in our TestUtility
         * class were inserted here. We compare that value to the value that the ContentProvider
         * reported that it inserted. These numbers should match.
         */
        String expectedAndActualInsertedRecordCountDoNotMatch =
                "Number of expected records inserted does not match actual inserted record count";
        assertEquals(expectedAndActualInsertedRecordCountDoNotMatch,
                insertCount,
                BULK_INSERT_RECORDS_TO_INSERT);

        /*
         * Perform our ContentProvider query. We expect the cursor that is returned will contain
         * the exact same data that is in testWeatherValues and we will validate that in the next
         * step.
         */
        Cursor cursor = mContext.getContentResolver().query(
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Columns; leaving this null returns every column in the table */
                null,
                /* Optional specification for columns in the "where" clause above */
                null,
                /* Values for "where" clause */
                null,
                /* Sort by date from smaller to larger (past to future) */
                WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");

        /*
         * Although we already tested the number of records that the ContentProvider reported
         * inserting, we are now testing the number of records that the ContentProvider actually
         * returned from the query above.
         */
        assertEquals(cursor.getCount(), BULK_INSERT_RECORDS_TO_INSERT);

        /*
         * We now loop through and validate each record in the Cursor with the expected values from
         * bulkInsertTestContentValues.
         */
        cursor.moveToFirst();
        for (int i = 0; i < BULK_INSERT_RECORDS_TO_INSERT; i++, cursor.moveToNext()) {
            TestUtilities.validateCurrentRecord(
                    "testBulkInsert. Error validating WeatherEntry " + i,
                    cursor,
                    bulkInsertTestContentValues[i]);
        }

        /* Always close the Cursor! */
        cursor.close();
    }


    @Test
    public void testDeleteAllRecordsFromProvider() {

        /*
         * Ensure there are records to delete from the database. Due to our setUp method, the
         * database will not have any records in it prior to this method being run.
         */
        testBulkInsert();

        /*
         * TestContentObserver allows us to test weather or not notifyChange was called
         * appropriately. We will use that here to make sure that notifyChange is called when a
         * deletion occurs.
         */
        TestUtilities.TestContentObserver weatherObserver = TestUtilities.getTestContentObserver();

        /*
         * A ContentResolver provides us access to the content model. We can use it to perform
         * deletions and queries at our CONTENT_URI
         */
        ContentResolver contentResolver = mContext.getContentResolver();

        /* Register a content observer to be notified of changes to data at a given URI (weather) */
        contentResolver.registerContentObserver(
                /* URI that we would like to observe changes to */
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Whether or not to notify us if descendants of this URI change */
                true,
                /* The observer to register (that will receive notifyChange callbacks) */
                weatherObserver);

        /* Delete all of the rows of data from the weather table */
        contentResolver.delete(
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Columns; leaving this null returns every column in the table */
                null,
                /* Optional specification for columns in the "where" clause above */
                null);

        /* Perform a query of the data that we've just deleted. This should be empty. */
        Cursor shouldBeEmptyCursor = contentResolver.query(
                WeatherContract.WeatherEntry.CONTENT_URI,
                /* Columns; leaving this null returns every column in the table */
                null,
                /* Optional specification for columns in the "where" clause above */
                null,
                /* Values for "where" clause */
                null,
                /* Sort order to return in Cursor */
                null);

        /*
         * If this fails, it's likely you didn't call notifyChange in your delete method from
         * your ContentProvider.
         */
        weatherObserver.waitForNotificationOrFail();

        /*
         * waitForNotificationOrFail is synchronous, so after that call, we are done observing
         * changes to content and should therefore unregister this observer.
         */
        contentResolver.unregisterContentObserver(weatherObserver);

        /* In some cases, the cursor can be null. That's actually a failure case here. */
        String cursorWasNull = "Cursor was null.";
        assertNotNull(cursorWasNull, shouldBeEmptyCursor);

        /* If the count of the cursor is not zero, all records weren't deleted */
        String allRecordsWereNotDeleted =
                "Error: All records were not deleted from weather table during delete";
        assertEquals(allRecordsWereNotDeleted,
                0,
                shouldBeEmptyCursor.getCount());

        /* Always close your cursor */
        shouldBeEmptyCursor.close();
    }


    private void deleteAllRecordsFromWeatherTable() {
        /* Access writable database through WeatherDbHelper */
        WeatherDbHelper helper = new WeatherDbHelper(InstrumentationRegistry.getTargetContext());
        SQLiteDatabase database = helper.getWritableDatabase();

        /* The delete method deletes all of the desired rows from the table, not the table itself */
        database.delete(WeatherContract.WeatherEntry.TABLE_NAME, null, null);

        /* Always close the database when you're through with it */
        database.close();
    }
}