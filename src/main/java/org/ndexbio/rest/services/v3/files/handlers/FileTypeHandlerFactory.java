package org.ndexbio.rest.services.v3.files.handlers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;

/**
 * Resolves {@link FileType} specific handlers.
 */
public class FileTypeHandlerFactory {

    private final Map<FileType, AbstractFileTypeHandler> handlers;

    public FileTypeHandlerFactory() {
        EnumMap<FileType, AbstractFileTypeHandler> map = new EnumMap<>(FileType.class);
        map.put(FileType.FOLDER, new FolderFileTypeHandler());
        map.put(FileType.NETWORK, new NetworkFileTypeHandler());
        map.put(FileType.SHORTCUT, new ShortcutFileTypeHandler());
        this.handlers = Collections.unmodifiableMap(map);
    }

    public AbstractFileTypeHandler getHandler(FileType type) throws NdexException {
        AbstractFileTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new NdexException("Unknown type: " + type);
        }
        return handler;
    }
}
