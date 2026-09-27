package cloud.einknote.hwpviewer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HwpDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "hwp_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HWP = "hwp_files";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_PATH = "path";
    public static final String COL_TIMESTAMP = "timestamp";

    public static class HwpItem {
        public long id;
        public String name;
        public String path;
        public long timestamp;

        public HwpItem(long id, String name, String path, long timestamp) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.timestamp = timestamp;
        }

        public File toFile() {
            return new File(path);
        }
    }

    public HwpDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableSql = "CREATE TABLE " + TABLE_HWP + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_PATH + " TEXT UNIQUE NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL" +
                ");";
        db.execSQL(createTableSql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HWP);
        onCreate(db);
    }

    // 파일 열람 기록 추가 또는 타임스탬프 갱신
    public synchronized void upsertHwpFile(String name, String path) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_PATH, path);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());

        // path가 UNIQUE 제약조건이므로 중복 시 충돌 해결(REPLACE)
        db.insertWithOnConflict(TABLE_HWP, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // 파일 삭제
    public synchronized void deleteByPath(String path) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HWP, COL_PATH + " = ?", new String[]{path});
    }

    // 최신 열람 순서대로 전체 목록 가져오기
    public synchronized List<HwpItem> getAllHistory() {
        List<HwpItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_HWP, null, null, null, null, null, COL_TIMESTAMP + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
                String path = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(new HwpItem(id, name, path, timestamp));
            }
            cursor.close();
        }
        return list;
    }
}