package com.tech.ezconvert.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tech.ezconvert.BuildConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 通过系统文档框架共享 EzConvert 自身的应用私有目录
 *
 * 根目录包含内部数据目录、应用专属外部数据目录、OBB 目录和可用的设备加密目录
 */
public class EzConvertDocumentsProvider extends DocumentsProvider {

    private static final String DOCUMENT_ID_ROOT = BuildConfig.APPLICATION_ID;
    private static final String TYPE_DATA = "data";
    private static final String TYPE_ANDROID_DATA = "android_data";
    private static final String TYPE_ANDROID_OBB = "android_obb";
    private static final String TYPE_USER_DE_DATA = "user_de_data";
    private static final String MIME_TYPE_GENERIC = "application/octet-stream";

    private static final String[] DEFAULT_ROOT_PROJECTION = {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private File dataDirectory;
    private File androidDataDirectory;
    private File androidObbDirectory;
    private File userDeDataDirectory;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        dataDirectory = context.getFilesDir().getParentFile();
        if (dataDirectory == null) {
            return false;
        }

        String dataPath = dataDirectory.getAbsolutePath();
        String userPathPrefix = "/data/user/";
        if (dataPath.startsWith(userPathPrefix)) {
            userDeDataDirectory = new File(
                    "/data/user_de/" + dataPath.substring(userPathPrefix.length())
            );
        }

        File externalFilesDirectory = context.getExternalFilesDir(null);
        if (externalFilesDirectory != null) {
            androidDataDirectory = externalFilesDirectory.getParentFile();
        }
        androidObbDirectory = context.getObbDir();
        return true;
    }

    @Override
    public Cursor queryRoots(@Nullable String[] projection) {
        String[] columns = projection != null ? projection : DEFAULT_ROOT_PROJECTION;
        MatrixCursor result = new MatrixCursor(columns);
        Context context = getContext();
        if (context == null || !isDirectoryAvailable(dataDirectory)) {
            return result;
        }

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String title = applicationInfo.loadLabel(context.getPackageManager()).toString();
        MatrixCursor.RowBuilder row = result.newRow();
        for (String column : columns) {
            switch (column) {
                case DocumentsContract.Root.COLUMN_ROOT_ID:
                case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
                    row.add(DOCUMENT_ID_ROOT);
                    break;
                case DocumentsContract.Root.COLUMN_TITLE:
                    row.add(title);
                    break;
                case DocumentsContract.Root.COLUMN_SUMMARY:
                    row.add(context.getPackageName());
                    break;
                case DocumentsContract.Root.COLUMN_FLAGS:
                    row.add(DocumentsContract.Root.FLAG_LOCAL_ONLY
                            | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
                    break;
                case DocumentsContract.Root.COLUMN_ICON:
                    row.add(applicationInfo.icon);
                    break;
                case DocumentsContract.Root.COLUMN_MIME_TYPES:
                    row.add("*/*");
                    break;
                case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
                    row.add(dataDirectory.getFreeSpace());
                    break;
                default:
                    row.add(null);
                    break;
            }
        }
        return result;
    }

    @Override
    public Cursor queryDocument(
            @NonNull String documentId,
            @Nullable String[] projection
    ) throws FileNotFoundException {
        String[] columns = projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
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
        String[] columns = projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
        MatrixCursor result = new MatrixCursor(columns);

        File parent = getFileForDocumentId(parentDocumentId);
        if (parent == null) {
            addRootDirectory(result, columns, parentDocumentId, TYPE_DATA, dataDirectory);
            addRootDirectory(result, columns, parentDocumentId, TYPE_ANDROID_DATA, androidDataDirectory);
            addRootDirectory(result, columns, parentDocumentId, TYPE_ANDROID_OBB, androidObbDirectory);
            addRootDirectory(result, columns, parentDocumentId, TYPE_USER_DE_DATA, userDeDataDirectory);
            return result;
        }

        if (!isDirectoryAvailable(parent)) {
            throw new FileNotFoundException("Not a directory: " + parentDocumentId);
        }

        File[] children = parent.listFiles();
        if (children == null) {
            return result;
        }

        List<File> sortedFiles = new ArrayList<>(Arrays.asList(children));
        sortedFiles.sort(Comparator
                .comparing((File file) -> !isDirectory(file))
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File child : sortedFiles) {
            String childDocumentId = getDocumentIdForFile(child);
            addDocumentRow(result, columns, childDocumentId, child);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(
            @NonNull String documentId,
            @NonNull String mode,
            @Nullable CancellationSignal signal
    ) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        if (file == null || isDirectory(file)) {
            throw new FileNotFoundException("Document cannot be opened: " + documentId);
        }
        if (signal != null) {
            signal.throwIfCanceled();
        }
        return ParcelFileDescriptor.open(file, parseFileMode(mode));
    }

    @Override
    public String createDocument(
            @NonNull String parentDocumentId,
            @NonNull String mimeType,
            @NonNull String displayName
    ) throws FileNotFoundException {
        File parent = getFileForDocumentId(parentDocumentId);
        if (!isDirectoryAvailable(parent)) {
            throw new FileNotFoundException("Parent directory is unavailable");
        }

        String safeName = sanitizeName(displayName);
        if (safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new FileNotFoundException("Invalid document name");
        }

        File target = createUniqueFile(parent, safeName);
        boolean created;
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
            created = target.mkdir();
        } else {
            try {
                created = target.createNewFile();
            } catch (IOException e) {
                throw fileNotFound("Unable to create document", e);
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
        if (file == null || isRootDirectory(file)) {
            throw new FileNotFoundException("The provider root cannot be deleted");
        }

        if (!deleteRecursively(file)) {
            throw new FileNotFoundException("Unable to delete: " + file.getAbsolutePath());
        }
        notifyParentChanged(file);
    }

    @Nullable
    @Override
    public String renameDocument(
            @NonNull String documentId,
            @NonNull String displayName
    ) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        if (file == null || isRootDirectory(file)) {
            throw new FileNotFoundException("The provider root cannot be renamed");
        }

        File parent = file.getParentFile();
        String safeName = sanitizeName(displayName);
        if (parent == null || safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new FileNotFoundException("Invalid document name");
        }

        File target = new File(parent, safeName);
        if (pathExists(target) || !file.renameTo(target)) {
            throw new FileNotFoundException("Unable to rename document: " + file.getAbsolutePath());
        }
        notifyChildrenChanged(getDocumentIdForFile(parent));
        return getDocumentIdForFile(target);
    }

    @Override
    public String moveDocument(
            @NonNull String sourceDocumentId,
            @NonNull String sourceParentDocumentId,
            @NonNull String targetParentDocumentId
    ) throws FileNotFoundException {
        File source = getFileForDocumentId(sourceDocumentId);
        File targetParent = getFileForDocumentId(targetParentDocumentId);
        if (source == null || isRootDirectory(source) || !isDirectoryAvailable(targetParent)) {
            throw new FileNotFoundException("Invalid move operation");
        }

        File target = new File(targetParent, source.getName());
        if (pathExists(target) || !source.renameTo(target)) {
            throw new FileNotFoundException("Unable to move document: " + sourceDocumentId);
        }

        notifyChildrenChanged(sourceParentDocumentId);
        if (!sourceParentDocumentId.equals(targetParentDocumentId)) {
            notifyChildrenChanged(targetParentDocumentId);
        }
        return getDocumentIdForFile(target);
    }

    @Override
    public String getDocumentType(@NonNull String documentId) throws FileNotFoundException {
        File file = getFileForDocumentId(documentId);
        return file == null || isDirectory(file)
                ? DocumentsContract.Document.MIME_TYPE_DIR
                : getMimeType(file);
    }

    @Override
    public boolean isChildDocument(
            @NonNull String parentDocumentId,
            @NonNull String documentId
    ) {
        try {
            File parent = getFileForDocumentId(parentDocumentId);
            File child = getFileForDocumentId(documentId);
            if (parent == null) {
                return child != null;
            }
            if (child == null || !isDirectory(parent)) {
                return false;
            }

            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return !parentPath.equals(childPath)
                    && childPath.startsWith(parentPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private void addRootDirectory(
            MatrixCursor cursor,
            String[] columns,
            String parentDocumentId,
            String type,
            File directory
    ) throws FileNotFoundException {
        if (!isDirectoryAvailable(directory)) {
            return;
        }
        String documentId = DOCUMENT_ID_ROOT + "/" + type;
        addDocumentRow(cursor, columns, documentId, directory);
    }

    private void addDocumentRow(
            MatrixCursor cursor,
            String[] columns,
            String documentId,
            File file
    ) throws FileNotFoundException {
        if (file != null && !pathExists(file)) {
            throw new FileNotFoundException("Document does not exist: " + documentId);
        }
        MatrixCursor.RowBuilder row = cursor.newRow();
        boolean root = file == null;
        boolean directory = root || isDirectory(file);
        for (String column : columns) {
            switch (column) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
                    row.add(documentId);
                    break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    row.add(root ? getContext().getPackageName()
                            : getRootDisplayName(documentId, file));
                    break;
                case DocumentsContract.Document.COLUMN_SIZE:
                    row.add(root || directory ? 0L : file.length());
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row.add(directory ? DocumentsContract.Document.MIME_TYPE_DIR : getMimeType(file));
                    break;
                case DocumentsContract.Document.COLUMN_FLAGS:
                    int flags = 0;
                    if (directory) {
                        if (root || file.canWrite()) {
                            flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
                        }
                    } else if (file.canWrite()) {
                        flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
                    }
                    if (!root && !isRootDirectory(file)) {
                        File parent = file.getParentFile();
                        if (parent != null && parent.canWrite()) {
                            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                                    | DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
                        }
                    }
                    row.add(flags);
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row.add(root ? 0L : file.lastModified());
                    break;
                default:
                    row.add(null);
                    break;
            }
        }
    }

    private String getRootDisplayName(String documentId, File file) {
        if ((DOCUMENT_ID_ROOT + "/" + TYPE_DATA).equals(documentId)) {
            return TYPE_DATA;
        }
        if ((DOCUMENT_ID_ROOT + "/" + TYPE_ANDROID_DATA).equals(documentId)) {
            return TYPE_ANDROID_DATA;
        }
        if ((DOCUMENT_ID_ROOT + "/" + TYPE_ANDROID_OBB).equals(documentId)) {
            return TYPE_ANDROID_OBB;
        }
        if ((DOCUMENT_ID_ROOT + "/" + TYPE_USER_DE_DATA).equals(documentId)) {
            return TYPE_USER_DE_DATA;
        }
        return file.getName();
    }

    @NonNull
    private String getDocumentIdForFile(@NonNull File file) throws FileNotFoundException {
        String type;
        File base;
        if (isInside(dataDirectory, file)) {
            type = TYPE_DATA;
            base = dataDirectory;
        } else if (isInside(androidDataDirectory, file)) {
            type = TYPE_ANDROID_DATA;
            base = androidDataDirectory;
        } else if (isInside(androidObbDirectory, file)) {
            type = TYPE_ANDROID_OBB;
            base = androidObbDirectory;
        } else if (isInside(userDeDataDirectory, file)) {
            type = TYPE_USER_DE_DATA;
            base = userDeDataDirectory;
        } else {
            throw new FileNotFoundException("File is outside the provider roots");
        }

        // 使用词法路径生成 ID，实际访问时仍会校验真实路径
        String basePath = base.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        String relativePath = basePath.equals(filePath)
                ? ""
                : filePath.substring(basePath.length() + 1);
        if (relativePath.isEmpty()) {
            return DOCUMENT_ID_ROOT + "/" + type;
        }
        String encoded = Base64.encodeToString(
                relativePath.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
        return DOCUMENT_ID_ROOT + "/" + type + "/" + encoded;
    }

    @Nullable
    private File getFileForDocumentId(@NonNull String documentId) throws FileNotFoundException {
        if (DOCUMENT_ID_ROOT.equals(documentId)) {
            return null;
        }
        String prefix = DOCUMENT_ID_ROOT + "/";
        if (!documentId.startsWith(prefix)) {
            throw new FileNotFoundException("Invalid document ID");
        }

        String encodedPath = documentId.substring(prefix.length());
        int separator = encodedPath.indexOf('/');
        String type = separator < 0 ? encodedPath : encodedPath.substring(0, separator);
        String encodedRelativePath = separator < 0 ? "" : encodedPath.substring(separator + 1);
        File base = getBaseDirectory(type);
        if (base == null || !isDirectoryAvailable(base)) {
            throw new FileNotFoundException("Directory is unavailable: " + type);
        }
        if (encodedRelativePath.isEmpty()) {
            return base;
        }

        final String relativePath;
        try {
            relativePath = new String(
                    Base64.decode(encodedRelativePath, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException e) {
            throw new FileNotFoundException("Invalid document path");
        }

        if (relativePath.isEmpty() || relativePath.startsWith("/")) {
            throw new FileNotFoundException("Invalid document path");
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new FileNotFoundException("Invalid document path segment");
            }
        }

        File file = new File(base, relativePath);
        String basePath;
        String filePath;
        try {
            basePath = base.getCanonicalPath();
            filePath = file.getCanonicalPath();
        } catch (IOException e) {
            throw fileNotFound("Unable to resolve document path", e);
        }
        if (!filePath.equals(basePath) && !filePath.startsWith(basePath + File.separator)) {
            throw new FileNotFoundException("Document escapes its provider root");
        }
        return file;
    }

    @Nullable
    private File getBaseDirectory(String type) {
        switch (type) {
            case TYPE_DATA:
                return dataDirectory;
            case TYPE_ANDROID_DATA:
                return androidDataDirectory;
            case TYPE_ANDROID_OBB:
                return androidObbDirectory;
            case TYPE_USER_DE_DATA:
                return userDeDataDirectory;
            default:
                return null;
        }
    }

    private int parseFileMode(String mode) throws FileNotFoundException {
        switch (mode) {
            case "r":
                return ParcelFileDescriptor.MODE_READ_ONLY;
            case "w":
            case "wt":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            case "wa":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_APPEND;
            case "rw":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE;
            case "rwt":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            default:
                throw new FileNotFoundException("Unsupported access mode: " + mode);
        }
    }

    private boolean isInside(@Nullable File base, @NonNull File file) throws FileNotFoundException {
        if (base == null) {
            return false;
        }
        String basePath = base.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        return filePath.equals(basePath) || filePath.startsWith(basePath + File.separator);
    }

    private boolean isRootDirectory(File file) {
        try {
            String path = file.getCanonicalPath();
            return samePath(dataDirectory, path)
                    || samePath(androidDataDirectory, path)
                    || samePath(androidObbDirectory, path)
                    || samePath(userDeDataDirectory, path);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean samePath(@Nullable File file, String canonicalPath) throws IOException {
        return file != null && file.getCanonicalPath().equals(canonicalPath);
    }

    private boolean isDirectoryAvailable(@Nullable File file) {
        return file != null && file.isDirectory();
    }

    private boolean isDirectory(@NonNull File file) {
        return !isSymbolicLink(file) && file.isDirectory();
    }

    private boolean pathExists(@NonNull File file) {
        try {
            Os.lstat(file.getPath());
            return true;
        } catch (ErrnoException e) {
            return false;
        }
    }

    private boolean isSymbolicLink(@NonNull File file) {
        try {
            StructStat stat = Os.lstat(file.getPath());
            // 通过文件类型标记识别符号链接，避免递归删除时误删链接目标
            return (stat.st_mode & 0170000) == 0120000;
        } catch (ErrnoException e) {
            return false;
        }
    }

    private boolean deleteRecursively(@NonNull File file) {
        if (isDirectory(file)) {
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

    private String getMimeType(@NonNull File file) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase(java.util.Locale.ROOT));
            if (mimeType != null) {
                return mimeType;
            }
        }
        return MIME_TYPE_GENERIC;
    }

    private String sanitizeName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        return name.replace("/", "_")
                .replace("\\", "_")
                .trim();
    }

    private File createUniqueFile(@NonNull File parent, @NonNull String name) {
        File candidate = new File(parent, name);
        if (!pathExists(candidate)) {
            return candidate;
        }

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; index < Integer.MAX_VALUE; index++) {
            candidate = new File(parent, base + " (" + index + ")" + extension);
            if (!pathExists(candidate)) {
                return candidate;
            }
        }
        return candidate;
    }

    private void notifyParentChanged(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            try {
                notifyChildrenChanged(getDocumentIdForFile(parent));
            } catch (FileNotFoundException ignored) {
                // 父目录已移动或不再属于 Provider 时无需刷新旧目录
            }
        }
    }

    private void notifyChildrenChanged(@NonNull String documentId) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Uri uri = DocumentsContract.buildChildDocumentsUri(
                BuildConfig.APPLICATION_ID + ".documents",
                documentId
        );
        ContentResolver resolver = context.getContentResolver();
        resolver.notifyChange(uri, null);
    }

    private FileNotFoundException fileNotFound(String message, Exception cause) {
        FileNotFoundException exception = new FileNotFoundException(message + ": " + cause.getMessage());
        exception.initCause(cause);
        return exception;
    }
}
