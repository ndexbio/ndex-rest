package org.ndexbio.rest.models;

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
        _filename = filename;
    }
}
