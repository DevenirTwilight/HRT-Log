package net.plainnotes.app.data;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/** Emulator/test APK/isolated provider process only. Holds a pipe without consuming/producing.
 * No patient/filesystem input. Driver release is a failure-cleanup seam, never a success condition.
 */
public final class SyntheticSecurityDocumentsProvider extends DocumentsProvider {
    private final ArrayList<ParcelFileDescriptor> held = new ArrayList<>();
    @Override public boolean onCreate() { Gate2EmulatorGuard.requireEmulator(); return true; }
    @Override public Cursor queryRoots(String[] projection) {
        return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});
    }
    @Override public Cursor queryDocument(String id, String[] projection) {
        MatrixCursor c = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_FLAGS});
        c.addRow(new Object[]{id,"application/octet-stream","SYNTHETIC stalled pipe",DocumentsContract.Document.FLAG_SUPPORTS_WRITE});
        return c;
    }
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort) {return queryDocument("stall",projection);}
    @Override public synchronized ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if (!"stall".equals(id)) throw new FileNotFoundException("Synthetic id only");
        try {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            boolean writing=mode.contains("w");
            held.add(pipe[writing?0:1]);
            return pipe[writing?1:0];
        } catch(IOException e) {throw new FileNotFoundException(e.toString());}
    }
    @Override public synchronized Bundle call(String method,String arg,Bundle extras) {
        if (!"release".equals(method)) throw new IllegalArgumentException("Synthetic release only");
        for(ParcelFileDescriptor fd:held)try {fd.close();}catch(IOException ignored) {}
        held.clear();return new Bundle();
    }
}
