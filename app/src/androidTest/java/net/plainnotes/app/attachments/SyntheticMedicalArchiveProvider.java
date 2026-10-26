package net.plainnotes.app.attachments;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicInteger;

/** TEST APK ONLY. One synthetic encrypted archive, no arbitrary paths or user files. */
public final class SyntheticMedicalArchiveProvider extends ContentProvider {
    private final AtomicInteger opens=new AtomicInteger();
    private File file(){return new File(getContext().getNoBackupFilesDir(),"synthetic-medarchive.pnmedbk2");}
    @Override public boolean onCreate(){net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard.requireEmulator();return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"/archive".equals(uri.getPath()))throw new FileNotFoundException("Synthetic path only");
        opens.incrementAndGet();
        if("w".equals(mode))return ParcelFileDescriptor.open(file(),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
        if("r".equals(mode))return ParcelFileDescriptor.open(file(),ParcelFileDescriptor.MODE_READ_ONLY);
        throw new FileNotFoundException("Synthetic mode only");
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        if("reset".equals(method)){file().delete();opens.set(0);}
        Bundle b=new Bundle();b.putInt("opens",opens.get());return b;
    }
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Cursor query(Uri uri,String[]p,String s,String[]a,String o){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String s,String[]a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[]a){throw new UnsupportedOperationException();}
}
