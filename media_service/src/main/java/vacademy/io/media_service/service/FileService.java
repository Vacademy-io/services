package vacademy.io.media_service.service;


import vacademy.io.media_service.dto.PreSignedUrlResponse;
import vacademy.io.media_service.exceptions.FileDownloadException;
import vacademy.io.media_service.exceptions.FileUploadException;
import org.springframework.web.multipart.MultipartFile;
import vacademy.io.common.media.dto.FileDetailsDTO;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface FileService {
    String uploadFile(MultipartFile multipartFile) throws FileUploadException, IOException;

    Object downloadFile(String fileName) throws FileDownloadException, IOException;

    PreSignedUrlResponse getPreSignedUrl(String fileName, String fileType, String source, String sourceId);

    String getPublicUrlWithExpiry(String key, Integer days) throws FileDownloadException;

    String getPublicUrlWithExpiryAndId(String id) throws FileDownloadException;

    Boolean acknowledgeClientUpload(String fileId, Long fileSize);

    boolean delete(String fileName);

    String getPublicUrlWithExpiryAndSource(String source, String sourceId, Integer expiryDays) throws FileDownloadException;

    List<Map<String, String>> getMultiplePublicUrlWithExpiryAndId(String fileIds);

    List<Map<String, String>> getMultipleUrlWithExpiryAndId(String fileIds, Integer expiryDays);

    String getUrlWithExpiryAndId(String id, Integer days) throws FileDownloadException;

    FileDetailsDTO getFileDetailsWithExpiryAndId(String id, Integer days) throws FileDownloadException;

    List<FileDetailsDTO> getMultipleFileDetailsWithExpiryAndId(String ids, Integer days) throws FileDownloadException;
}