package com.mandiconnect.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FileUploadService {

    @Autowired
    private Cloudinary cloudinary;

    public  Map<String , String> uploadFile(MultipartFile file) throws IOException{
        Map<String , String> uploadResult  = cloudinary.uploader().upload(file.getBytes() , ObjectUtils.asMap("folder" , "CropListingImages") );
        return uploadResult;
    }

    public Map<String, Object> deleteFile(String publicId ) throws IOException {
        Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        return result ;
    }
}
