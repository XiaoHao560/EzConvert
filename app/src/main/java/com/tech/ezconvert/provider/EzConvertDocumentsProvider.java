package com.tech.ezconvert.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tech.ezconvert.BuildConfig;
import com.tech.ezconvert.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Exposes EzConvert's private internal files directory through the system
 * Storage Access Framework (SAF).
 *
 * The root is:
 *     Context#getFilesDir()
 *
 * Document IDs are opaque Base64-URL encoded paths relative to that root.
 * The root itself always uses DOCUMENT_ID_ROOT so the root ID remains stable.
 */
public class EzConvertDocumentsProvider extends DocumentsProvider {

    private static final String DOCUMENT_ID_ROOT = "root";
    private static final String AUTHORITY_SUFFIX = ".documents";

    private static final String MIME_TYPE_GENERIC = "application/octet-stream";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private File rootDirectory;

    private static String getAuthority() {
        return BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX;
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        rootDirectory = context.getFilesDir();
        return rootDirectory != null;
    }

    @Override
    public Cursor queryRoots(@Nullable String[] projection) {
        final String[] columns = projection != null ? projection : DEFAULT_ROOT_PROJECTION;
        MatrixCursor result = new MatrixCursor(columns);

        final File root;
        try {
            root = getRootDirectory();
        } catch (FileNotFoundException e) {
            return result;
        }
        if (!root.isDirectory()) {
            return result;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        for (String column : columns) {
            switch (column) {
                case DocumentsContract.Root.COLUMN_ROOT_ID:
                    row.add(DOCUMENT_ID_ROOT);
                    break;

                case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
                    row.add(DOCUMENT_ID_ROOT);
                    break;

                case DocumentsContract.Root.COLUMN_TITLE:
                    row.add(getContext().getString(R.string.app_name));
                    break;

                case DocumentsContract.Root.COLUMN_SUMMARY:
                    row.add(getContext().getPackageName());
                    break;

                case DocumentsContract.Root.COLUMN_FLAGS:
                    row.add(
                            DocumentsContract.Root.FLAG_LOCAL_ONLY
                                    | DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                                    | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                    );
                    break;

                case DocumentsContract.Root.COLUMN_ICON:
                    row.add(R.mipmap.ic_launcher);
                    break;

                case DocumentsContract.Root.COLUMN_MIME_TYPES:
                    row.add("*/*");
                    break;

                case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
                    row.add(root.getFreeSpace());
                    break;

                default:
                    row.add(null);
                    break;
            }
        }

        return result;
    }

    @Override
    public Cursor queryDocument(@NonNull String documentId, @Nullable String[] projection)
            throws FileNotFoundException {
        final String[] columns = projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
        MatrixCursor result = new MatrixCursor(columns);

        File file = getFileForDocumentId(documentId);
        addDocumentRow(result, columns, documentId, file);

        return result;
    }

    @Override
    public Cursor queryChildDocuments(
            @NonNull String parentDocumentId,
            @Nullable String[] projection,
            @Nullable String sortOrder
    ) throws FileNotFoundException {
        final String[] columns = projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
        MatrixCursor result = new MatrixCursor(columns);

        File parent = getFileForDocumentId(parentDocumentId);
        if (!parent.isDirectory()) {
            throw new FileNotFoundException("Not a directory: " + parentDocumentId);
        }

        File[] children = parent.listFiles();
        if (children == null) {
            return result;
        }

        List<File> files = new ArrayList<>(Arrays.asList(children));
        files.sort(
                Comparator.comparing(File::isFile)
                        .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER)
        );

        for (File child : files) {
            addDocumentRow(
                    result,
                    columns,
                    getDocumentIdForFile(child),
                    child
            );
        }

        return result;
    }

    @Override
    public boolean isChildDocument(
            @NonNull String parentDocumentId,
            @NonNull String documentId
    ) {
        try {
            File parent = getFileForDocumentId(parentDocumentId);
            File child = getFileForDocumentId(documentId);

            if (!parent.isDirectory()) {
                return false;
            }

            String parentPath = getCanonicalPath(parent);
            String childPath = getCanonicalPath(child);

            if (parentPath.equals(childPath)) {
                return false;
            }

            return childPath.startsWith(parentPath + File.separator);
        } catch (FileNotFoundException e) {
            // DocumentsProvider.isChildDocument() does not declare
            // FileNotFoundException. Invalid/non-existent document IDs
            // therefore simply aren't considered children.
            return false;
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(
            @NonNull String documentId,
            @NonNull String mode,
            @Nullable CancellationSignal signal
    ) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);

        if (file.isDirectory()) {
            throw new FileNotFoundException("Directories cannot be opened as documents: " + documentId);
        }

        int accessMode = getParcelFileDescriptorMode(mode);
        try {
            return ParcelFileDescriptor.open(file, accessMode);
        } catch (SecurityException e) {
            throw new FileNotFoundException("Permission denied: " + file.getAbsolutePath());
        }
    }

    /**
     * We advertise directory creation from the root so the system picker can
     * treat this as a normal writable document tree. File writes themselves
     * are supported by openDocument().
     */
    @Override
    public String createDocument(
            @NonNull String parentDocumentId,
            @NonNull String mimeType,
            @NonNull String displayName
    ) throws FileNotFoundException {
        File parent = getFileForDocumentId(parentDocumentId);
        if (!parent.isDirectory()) {
            throw new FileNotFoundException("Parent is not a directory");
        }

        String safeName = sanitizeDisplayName(displayName);
        if (safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new FileNotFoundException("Invalid display name");
        }

        File target = createNonConflictingFile(parent, safeName);
        boolean created;

        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
            created = target.mkdirs();
        } else {
            try {
                created = target.createNewFile();
            } catch (IOException e) {
                throw new FileNotFoundException("Unable to create document: " + e.getMessage());
            }
        }

        if (!created) {
            throw new FileNotFoundException("Unable to create document: " + target.getAbsolutePath());
        }

        notifyChildrenChanged(parentDocumentId);
        return getDocumentIdForFile(target);
    }

    @Override
    public void deleteDocument(@NonNull String documentId) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        if (file.equals(getRootDirectory())) {
            throw new FileNotFoundException("The provider root cannot be deleted");
        }

        if (!deleteRecursively(file)) {
            throw new FileNotFoundException("Unable to delete: " + file.getAbsolutePath());
        }

        File parent = file.getParentFile();
        if (parent != null) {
            notifyChildrenChanged(getDocumentIdForFile(parent));
        }
    }

    @Nullable
    @Override
    public String renameDocument(
            @NonNull String documentId,
            @NonNull String displayName
    ) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        if (file.equals(getRootDirectory())) {
            throw new FileNotFoundException("The provider root cannot be renamed");
        }

        File parent = file.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new FileNotFoundException("Parent directory does not exist");
        }

        String safeName = sanitizeDisplayName(displayName);
        if (safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new FileNotFoundException("Invalid display name");
        }

        File target = new File(parent, safeName);
        if (target.exists()) {
            throw new FileNotFoundException("A document with the same name already exists");
        }

        if (!file.renameTo(target)) {
            throw new FileNotFoundException("Unable to rename: " + file.getAbsolutePath());
        }

        notifyChildrenChanged(getDocumentIdForFile(parent));
        return getDocumentIdForFile(target);
    }

    private void addDocumentRow(
            MatrixCursor result,
            String[] columns,
            String documentId,
            File file
    ) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException("Document does not exist: " + documentId);
        }

        MatrixCursor.RowBuilder row = result.newRow();
        boolean isDirectory = file.isDirectory();

        for (String column : columns) {
            switch (column) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
                    row.add(documentId);
                    break;

                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    row.add(DOCUMENT_ID_ROOT.equals(documentId)
                            ? getContext().getString(R.string.app_name)
                            : file.getName());
                    break;

                case DocumentsContract.Document.COLUMN_SIZE:
                    row.add(isDirectory ? 0L : file.length());
                    break;

                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row.add(isDirectory
                            ? DocumentsContract.Document.MIME_TYPE_DIR
                            : getMimeType(file));
                    break;

                case DocumentsContract.Document.COLUMN_FLAGS:
                    int flags = 0;
                    if (isDirectory) {
                        flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
                    } else {
                        flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
                    }
                    if (!DOCUMENT_ID_ROOT.equals(documentId)) {
                        flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                        flags |= DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
                    }
                    row.add(flags);
                    break;

                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row.add(file.lastModified());
                    break;

                default:
                    row.add(null);
                    break;
            }
        }
    }

    private int getParcelFileDescriptorMode(String mode) throws FileNotFoundException {
        if ("r".equals(mode)) {
            return ParcelFileDescriptor.MODE_READ_ONLY;
        }
        if ("w".equals(mode)) {
            return ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        if ("wa".equals(mode)) {
            return ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_APPEND;
        }
        if ("rw".equals(mode)) {
            return ParcelFileDescriptor.MODE_READ_WRITE;
        }
        if ("rwt".equals(mode)) {
            return ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }

        throw new FileNotFoundException("Unsupported access mode: " + mode);
    }

    @NonNull
    private String getDocumentIdForFile(@NonNull File file) throws FileNotFoundException {
        File root = getRootDirectory();
        String rootPath = getCanonicalPath(root);
        String filePath = getCanonicalPath(file);

        if (rootPath.equals(filePath)) {
            return DOCUMENT_ID_ROOT;
        }

        String prefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;

        if (!filePath.startsWith(prefix)) {
            throw new FileNotFoundException("File is outside the provider root");
        }

        String relativePath = filePath.substring(prefix.length());
        return Base64.encodeToString(
                relativePath.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    @NonNull
    private File getFileForDocumentId(@NonNull String documentId) throws FileNotFoundException {
        File root = getRootDirectory();

        if (DOCUMENT_ID_ROOT.equals(documentId)) {
            return root;
        }

        final String relativePath;
        try {
            byte[] decoded = Base64.decode(documentId, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            relativePath = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new FileNotFoundException("Invalid document id");
        }

        if (relativePath.isEmpty()) {
            throw new FileNotFoundException("Invalid document id");
        }

        File file = new File(root, relativePath);
        String rootPath = getCanonicalPath(root);
        String filePath = getCanonicalPath(file);
        String prefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;

        if (!filePath.startsWith(prefix)) {
            throw new FileNotFoundException("Document is outside the provider root");
        }

        return file;
    }

    @NonNull
    private File getRootDirectory() throws FileNotFoundException {
        if (rootDirectory == null) {
            throw new FileNotFoundException("Provider is not initialized");
        }
        if (!rootDirectory.isDirectory()) {
            throw new FileNotFoundException("Provider root does not exist");
        }
        return rootDirectory;
    }

    @NonNull
    private String getCanonicalPath(@NonNull File file) throws FileNotFoundException {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new FileNotFoundException("Unable to resolve path: " + e.getMessage());
        }
    }

    @NonNull
    private String getMimeType(@NonNull File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeType != null) {
                return mimeType;
            }
        }
        return MIME_TYPE_GENERIC;
    }

    @NonNull
    private String sanitizeDisplayName(@Nullable String displayName) {
        if (displayName == null) {
            return "";
        }

        return displayName
                .replace("/", "_")
                .replace("\\", "_")
                .replace("\u0000", "")
                .trim();
    }

    @NonNull
    private File createNonConflictingFile(@NonNull File parent, @NonNull String displayName) {
        File candidate = new File(parent, displayName);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = displayName.lastIndexOf('.');
        String base = dot > 0 ? displayName.substring(0, dot) : displayName;
        String extension = dot > 0 ? displayName.substring(dot) : "";

        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            candidate = new File(parent, base + " (" + index + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }

        return candidate;
    }

    private boolean deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private void notifyChildrenChanged(@NonNull String parentDocumentId) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        Uri uri = DocumentsContract.buildChildDocumentsUri(
                getAuthority(),
                parentDocumentId
        );
        ContentResolver resolver = context.getContentResolver();
        resolver.notifyChange(uri, null);
    }

}
