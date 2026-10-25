package net.plainnotes.app.imports;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test APK only. Actual DocumentsUI, deliberately misleading names/MIME/size, synthetic data. */
public final class SyntheticImportDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOTS={"root_id","document_id","title","mime_types","flags"};
    private static final String[] DOCS={"document_id","_display_name","mime_type","_size","flags"};
    @Override public boolean onCreate() {return true;}
    private void row(MatrixCursor c,String id) {
        if(!Arrays.asList("exports","tracker","memo").contains(id))throw new IllegalArgumentException();
        MatrixCursor.RowBuilder r=c.newRow();for(String k:c.getColumnNames()) {
            Object v=null;
            if(k.equals("document_id"))v=id;
            if(k.equals("_display_name"))v=id.equals("exports")?"SYNTHETIC exports":id.equals("tracker")?"SYNTHETIC tracker.db":"SYNTHETIC memo.json";
            if(k.equals("mime_type"))v=id.equals("exports")?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream";
            if(k.equals("_size"))v=1L;if(k.equals("flags"))v=0;r.add(k,v);
        }
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor c=new MatrixCursor(projection==null?ROOTS:projection);MatrixCursor.RowBuilder r=c.newRow();
        for(String k:c.getColumnNames()) {Object v=null;if(k.equals("root_id")||k.equals("document_id"))v="exports";
            if(k.equals("title"))v="SYNTHETIC IMPORT";if(k.equals("mime_types"))v="*/*";
            if(k.equals("flags"))v=DocumentsContract.Root.FLAG_LOCAL_ONLY|DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD;r.add(k,v);}
        return c;
    }
    @Override public Cursor queryDocument(String id,String[] p) {MatrixCursor c=new MatrixCursor(p==null?DOCS:p);row(c,id);return c;}
    @Override public Cursor queryChildDocuments(String id,String[] p,String sort) {if(!id.equals("exports"))throw new IllegalArgumentException();MatrixCursor c=new MatrixCursor(p==null?DOCS:p);row(c,"tracker");row(c,"memo");return c;}
    @Override public boolean isChildDocument(String parent,String child) {return parent.equals("exports")&&Arrays.asList("tracker","memo").contains(child);}
    @Override public DocumentsContract.Path findDocumentPath(String parent,String child) {return new DocumentsContract.Path(parent==null?"exports":null,child.equals("exports")?Collections.singletonList("exports"):Arrays.asList("exports",child));}
    private byte[] asset(String name) throws IOException {try(InputStream in=getContext().getAssets().open("imports/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!mode.equals("r"))throw new FileNotFoundException();if(signal!=null)signal.throwIfCanceled();
        try {
            final byte[] bytes;
            if(id.equals("tracker"))bytes=asset("tracker.json");
            else if(id.equals("memo")) {
                File f=File.createTempFile("synthetic-import-", ".db",getContext().getCacheDir());
                try {SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(f,null);
                    try {for(String statement:new String(asset("memo.sql"),StandardCharsets.UTF_8).split(";\\n")) {
                        StringBuilder sql=new StringBuilder();for(String line:statement.split("\\n"))if(!line.trim().startsWith("--"))sql.append(line).append('\n');
                        if(!sql.toString().trim().isEmpty()&&!sql.toString().contains("android_metadata"))db.execSQL(sql.toString());
                    }}finally{db.close();}
                    try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
                }finally{f.delete();new File(f+"-journal").delete();}
            }else throw new FileNotFoundException();
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {out.write(bytes);}catch(IOException ignored){}finally{Arrays.fill(bytes,(byte)0);}}).start();return pipe[0];
        }catch(IOException e){throw new FileNotFoundException("Synthetic input unavailable");}
    }
}
