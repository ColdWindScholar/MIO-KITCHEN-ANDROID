package com.mio.kitchen;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class MTDataFilesProvider extends DocumentsProvider {
    public static final String[] f = {"root_id", "mime_types", "flags", "icon", "title", "summary", "document_id"};
    public static final String[] g = {"document_id", "mime_type", "_display_name", "last_modified", "flags", "_size", "mt_extras"};
    public String b;
    public File c;
    public File d;
    public File e;


    public static boolean a(File file) {
        File[] listFiles;
        if (file.isDirectory()) {
            try {
                if ((Os.lstat(file.getPath()).st_mode & 61440) == 40960) {
                    if ((listFiles = file.listFiles()) != null) {
                        for (File file2 : listFiles) {
                            if (a(file2)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (ErrnoException ex) {
                throw new RuntimeException(ex);
            }

        }
        return !file.delete();
    }

    public static String c(File file) {
        if (file.isDirectory()) {
            return "vnd.android.document/directory";
        }
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(46);
        if (lastIndexOf >= 0) {
            String mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(lastIndexOf + 1).toLowerCase());
            return mimeTypeFromExtension != null ? mimeTypeFromExtension : "application/octet-stream";
        }
        return "application/octet-stream";
    }

    @Override
    public final void attachInfo(Context context, ProviderInfo providerInfo) {
        super.attachInfo(context, providerInfo);
        this.b = context.getPackageName();
        this.c = context.getFilesDir().getParentFile();
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        this.d = new File(externalStorageDirectory, "Android/data/" + this.b);
        File externalStorageDirectory2 = Environment.getExternalStorageDirectory();
        this.e = new File(externalStorageDirectory2, "Android/obb/" + this.b);
    }

    public final File b(String str, boolean z) {
        String substring;
        if (str.startsWith(this.b)) {
            String substring2 = str.substring(this.b.length());
            if (substring2.startsWith("/")) {
                substring2 = substring2.substring(1);
            }
            File file = null;
            if (substring2.isEmpty()) {
                return null;
            }
            int indexOf = substring2.indexOf(47);
            if (indexOf == -1) {
                substring = "";
            } else {
                String substring3 = substring2.substring(0, indexOf);
                substring = substring2.substring(indexOf + 1);
                substring2 = substring3;
            }
            if (substring2.equalsIgnoreCase("data")) {
                file = new File(this.c, substring);
            } else if (substring2.equalsIgnoreCase("android_data")) {
                file = new File(this.d, substring);
            } else if (substring2.equalsIgnoreCase("android_obb")) {
                file = new File(this.e, substring);
            }
            if (file == null || (z && !file.exists())) {
                try {
                    throw new FileNotFoundException(str.concat(" not found"));
                } catch (FileNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return file;
        }
        try {
            throw new FileNotFoundException(str.concat(" not found"));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public final Bundle call(String str, String str2, Bundle bundle) {
        Bundle call = super.call(str, str2, bundle);
        if (call != null) {
            return call;
        }
        try {
            if (!str.equals("mt:setPermissions") && !str.equals("mt:setLastModified")) {
                return null;
            }
            List<String> pathSegments = ((Uri) bundle.getParcelable("uri")).getPathSegments();
            File b = b(pathSegments.size() >= 4 ? pathSegments.get(3) : pathSegments.get(1), true);
            Bundle bundle2 = new Bundle();
            if (b == null) {
                bundle2.putBoolean("result", false);
            } else if (str.equals("mt:setLastModified")) {
                bundle2.putBoolean("result", b.setLastModified(bundle.getLong("time")));
            } else {
                try {
                    Os.chmod(b.getPath(), bundle.getInt("permissions"));
                    bundle2.putBoolean("result", true);
                } catch (ErrnoException e) {
                    bundle2.putBoolean("result", false);
                    bundle2.putString("message", e.getMessage());
                }
            }
            return bundle2;
        } catch (Exception e2) {
            Bundle bundle3 = new Bundle();
            bundle3.putBoolean("result", false);
            bundle3.putString("message", e2.toString());
            return bundle3;
        }
    }

    @Override
    public final String createDocument(String str, String str2, String str3) {
        StringBuilder sb;
        File b = b(str, true);
        if (b != null) {
            File file = new File(b, str3);
            int i = 2;
            while (file.exists()) {
                file = new File(b, str3 + " (" + i + ")");
                i++;
            }
            try {
                if ("vnd.android.document/directory".equals(str2) ? file.mkdir() : file.createNewFile()) {
                    if (str.endsWith("/")) {
                        sb = new StringBuilder();
                        sb.append(str);
                        sb.append(file.getName());
                    } else {
                        sb = new StringBuilder();
                        sb.append(str);
                        sb.append("/");
                        sb.append(file.getName());
                    }
                    str = sb.toString();
                    return str;
                }
            } catch (IOException e) {
                Log.e("mt_!", e.toString());
            }
        }
        try {
            throw new FileNotFoundException("Failed to create document in " + str + " with name " + str3);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    public final void d(MatrixCursor matrixCursor, String str, File file) {
        int i;
        String name;
        if (file == null) {
            file = b(str, true);
        }
        boolean z = false;
        if (file == null) {
            MatrixCursor.RowBuilder newRow = matrixCursor.newRow();
            newRow.add("document_id", this.b);
            newRow.add("_display_name", this.b);
            newRow.add("_size", 0L);
            newRow.add("mime_type", "vnd.android.document/directory");
            newRow.add("last_modified", 0);
            newRow.add("flags", 0);
            return;
        }
        if (file.isDirectory()) {
            if (file.canWrite()) {
                i = 8;
            }
        } else {
            if (file.canWrite()) {
                i = 2;
            }
        }
        i = 0;
        if (file.getParentFile().canWrite()) {
            i = i | 4 | 64;
        }
        String path = file.getPath();
        if (path.equals(this.c.getPath())) {
            name = "data";
        } else if (path.equals(this.d.getPath())) {
            name = "android_data";
        } else if (path.equals(this.e.getPath())) {
            name = "android_obb";
        } else {
            name = file.getName();
            z = true;
        }
        MatrixCursor.RowBuilder newRow2 = matrixCursor.newRow();
        newRow2.add("document_id", str);
        newRow2.add("_display_name", name);
        newRow2.add("_size", file.length());
        newRow2.add("mime_type", c(file));
        newRow2.add("last_modified", file.lastModified());
        newRow2.add("flags", i);
        if (z) {
            try {
                StringBuilder sb = new StringBuilder();
                StructStat lstat = Os.lstat(path);
                sb.append(lstat.st_mode);
                sb.append("|");
                sb.append(lstat.st_uid);
                sb.append("|");
                sb.append(lstat.st_gid);
                if ((lstat.st_mode & 61440) == 40960) {
                    sb.append("|");
                    sb.append(Os.readlink(path));
                }
                newRow2.add("mt_extras", sb.toString());
            } catch (Exception e) {
                Log.e("mt_!", e.toString());
            }
        }
    }

    @Override
    public final void deleteDocument(String str) {
        File b = b(str, true);
        if (b == null || a(b)) {
            try {
                throw new FileNotFoundException("Failed to delete document ".concat(str));
            } catch (FileNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public final String getDocumentType(String str) {
        File b = b(str, true);
        return b == null ? "vnd.android.document/directory" : c(b);
    }

    @Override
    public final boolean isChildDocument(String str, String str2) {
        return str2.startsWith(str);
    }

    @Override
    public final String moveDocument(String str, String str2, String str3) {
        File b = b(str, true);
        File b2 = b(str3, true);
        if (b != null && b2 != null) {
            File file = new File(b2, b.getName());
            if (!file.exists() && b.renameTo(file)) {
                if (str3.endsWith("/")) {
                    return str3 + file.getName();
                }
                return str3 + "/" + file.getName();
            }
        }
        try {
            throw new FileNotFoundException("Filed to move document " + str + " to " + str3);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public final boolean onCreate() {
        return true;
    }

    @Override
    public final ParcelFileDescriptor openDocument(String str, String str2, CancellationSignal cancellationSignal) {
        File b = b(str, false);
        if (b != null) {
            try {
                return ParcelFileDescriptor.open(b, ParcelFileDescriptor.parseMode(str2));
            } catch (FileNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        try {
            throw new FileNotFoundException(str.concat(" not found"));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public final Cursor queryChildDocuments(String str, String[] strArr, String str2) {
        if (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        if (strArr == null) {
            strArr = g;
        }
        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        File b = b(str, true);
        if (b == null) {
            d(matrixCursor, str.concat("/data"), this.c);
            if (this.d.exists()) {
                d(matrixCursor, str.concat("/android_data"), this.d);
            }
            if (this.e.exists()) {
                d(matrixCursor, str.concat("/android_obb"), this.e);
            }
        } else {
            File[] listFiles = b.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) {
                    d(matrixCursor, str + "/" + file.getName(), file);
                }
            }
        }
        return matrixCursor;
    }

    @Override
    public final Cursor queryDocument(String str, String[] strArr) {
        if (strArr == null) {
            strArr = g;
        }
        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        d(matrixCursor, str, null);
        return matrixCursor;
    }

    @Override
    public final Cursor queryRoots(String[] strArr) {
        ApplicationInfo applicationInfo = getContext().getApplicationInfo();
        String charSequence = applicationInfo.loadLabel(getContext().getPackageManager()).toString();
        if (strArr == null) {
            strArr = f;
        }
        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        MatrixCursor.RowBuilder newRow = matrixCursor.newRow();
        newRow.add("root_id", this.b);
        newRow.add("document_id", this.b);
        newRow.add("summary", this.b);
        newRow.add("flags", 17);
        newRow.add("title", charSequence);
        newRow.add("mime_types", "*/*");
        newRow.add("icon", applicationInfo.icon);
        return matrixCursor;
    }

    @Override
    public final void removeDocument(String str, String str2) {
        deleteDocument(str);
    }

    @Override
    public final String renameDocument(String str, String str2) {
        File b = b(str, true);
        if (b == null || !b.renameTo(new File(b.getParentFile(), str2))) {
            try {
                throw new FileNotFoundException("Failed to rename document " + str + " to " + str2);
            } catch (FileNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        int lastIndexOf = str.lastIndexOf(47, str.length() - 2);
        return str.substring(0, lastIndexOf) + "/" + str2;
    }
}
