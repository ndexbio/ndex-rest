/**
 *   Copyright (c) 2013, 2015
 *  	The Regents of the University of California
 *  	The Cytoscape Consortium
 *
 *   Permission to use, copy, modify, and distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.ndexbio.rest.helpers;

import javax.ws.rs.FormParam;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

public class UploadedFile
{
    private byte[] _fileData;
    private String _filename;
    
    

    public byte[] getFileData()
    {
        return _fileData;
    }
    
    @FormParam("fileUpload")
    @PartType("application/octet-stream")
    public void setFileData(byte[] fileData)
    {
        _fileData = fileData;
    }
    
    public String getFilename()
    {
        return _filename;
    }
    
    @FormParam("filename")
    public void setFilename(String filename)
    {
        if (filename.indexOf("\\") < 0)
            _filename = filename;
        else
            _filename = filename.substring(filename.lastIndexOf("\\") + 1);
    }
}
