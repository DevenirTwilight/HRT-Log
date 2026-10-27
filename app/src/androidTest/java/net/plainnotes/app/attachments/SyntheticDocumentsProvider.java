package net.plainnotes.app.attachments;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Standalone test-APK process: framework/Java only, no target-APK Kotlin dependency.
 * MANAGE_DOCUMENTS protected; generates synthetic bytes without filesystem/patient access. */
public final class SyntheticDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOTS={DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_MIME_TYPES,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_AVAILABLE_BYTES};
    private static final String[] DOCS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
    @Override public boolean onCreate() {SyntheticEmulatorGuard.requireEmulator();return true;}
    private static boolean known(String id) {return Arrays.asList("samples","pdf","png","binary").contains(id);}
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor c=new MatrixCursor(projection==null?ROOTS:projection);MatrixCursor.RowBuilder row=c.newRow();
        for(String name:c.getColumnNames()) {
            Object value=null;
            if(name.equals(DocumentsContract.Root.COLUMN_ROOT_ID)||name.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID))value="samples";
            else if(name.equals(DocumentsContract.Root.COLUMN_TITLE))value="SYNTHETIC MED-02";
            else if(name.equals(DocumentsContract.Root.COLUMN_MIME_TYPES))value="*/*";
            else if(name.equals(DocumentsContract.Root.COLUMN_FLAGS))value=DocumentsContract.Root.FLAG_LOCAL_ONLY|DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD;
            else if(name.equals(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES))value=32L*1024*1024;
            row.add(name,value);
        }
        return c;
    }
    @Override public Cursor queryDocument(String id,String[] projection) {MatrixCursor c=new MatrixCursor(projection==null?DOCS:projection);row(c,id);return c;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort) {
        if(!"samples".equals(parent))throw new IllegalArgumentException("Synthetic parent only");
        MatrixCursor c=new MatrixCursor(projection==null?DOCS:projection);for(String id:Arrays.asList("pdf","png","binary"))row(c,id);return c;
    }
    @Override public boolean isChildDocument(String parent,String id) {return "samples".equals(parent)&&known(id)&&!"samples".equals(id);}
    @Override public DocumentsContract.Path findDocumentPath(String parent,String child) {
        if((parent!=null&&!"samples".equals(parent))||!known(child))throw new IllegalArgumentException("Synthetic path only");
        return new DocumentsContract.Path(parent==null?"samples":null,"samples".equals(child)?Collections.singletonList("samples"):Arrays.asList("samples",child));
    }
    private static void row(MatrixCursor c,String id) {
        if(!known(id))throw new IllegalArgumentException("Synthetic document only");MatrixCursor.RowBuilder row=c.newRow();
        for(String name:c.getColumnNames()) {
            Object value=null;
            if(name.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID))value=id;
            else if(name.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME))value="samples".equals(id)?"SYNTHETIC samples":"SYNTHETIC sample."+("binary".equals(id)?"bin":id);
            // Deliberately false type and length: consumers must inspect actual bounded bytes.
            else if(name.equals(DocumentsContract.Document.COLUMN_MIME_TYPE))value="samples".equals(id)?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream";
            else if(name.equals(DocumentsContract.Document.COLUMN_SIZE))value="samples".equals(id)?null:1L;
            else if(name.equals(DocumentsContract.Document.COLUMN_FLAGS))value=0;
            row.add(name,value);
        }
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Synthetic read-only source");if(signal!=null)signal.throwIfCanceled();
        final byte[] bytes=sample(id);final ParcelFileDescriptor[] pipe;
        try {pipe=ParcelFileDescriptor.createReliablePipe();}catch(IOException e){Arrays.fill(bytes,(byte)0);throw new FileNotFoundException("Synthetic pipe unavailable");}
        Thread writer=new Thread(()->{
            try(ParcelFileDescriptor.AutoCloseOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {out.write(bytes);}
            catch(IOException e){try {pipe[1].closeWithError("Synthetic pipe interrupted");}catch(IOException ignored){}}
            finally {Arrays.fill(bytes,(byte)0);}
        });writer.setDaemon(true);writer.start();return pipe[0];
    }
    public static byte[] sample(String id) {
        if("binary".equals(id)) {byte[] b=new byte[8192];for(int i=0;i<b.length;i++)b[i]=(byte)((i*31+17)%251);return b;}
        if("png".equals(id)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();Bitmap b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
            try {b.eraseColor(Color.BLUE);if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("Synthetic PNG");}
            finally {b.recycle();}return out.toByteArray();
        }
        if(!"pdf".equals(id))throw new IllegalArgumentException("Synthetic sample only");
        String content="BT /F1 12 Tf 5 100 Td (SYNTHETIC ONLY) Tj ET\n";
        String page="<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 160] /Resources << /Font << /F1 7 0 R >> >> /Contents ";
        String stream="<< /Length "+content.length()+" >>\nstream\n"+content+"endstream";
        String[] objects={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",page+"5 0 R >>",page+"6 0 R >>",stream,stream,"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};
        StringBuilder out=new StringBuilder("%PDF-1.4\n");ArrayList<Integer> offsets=new ArrayList<>();
        for(int i=0;i<objects.length;i++){offsets.add(out.length());out.append(i+1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");}
        int start=out.length();out.append("xref\n0 ").append(objects.length+1).append("\n0000000000 65535 f \n");
        for(int offset:offsets)out.append(String.format(Locale.ROOT,"%010d",offset)).append(" 00000 n \n");
        out.append("trailer\n<< /Size ").append(objects.length+1).append(" /Root 1 0 R >>\nstartxref\n").append(start).append("\n%%EOF\n");
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
