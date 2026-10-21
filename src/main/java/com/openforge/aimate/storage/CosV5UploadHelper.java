package com.openforge.aimate.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSStaticCredentialsProvider;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

import java.io.InputStream;

/** 纯工具类：COS SDK 仅在此类加载时进入 JVM（与 NAS / Account 一致）。 */
public final class CosV5UploadHelper {

    private CosV5UploadHelper() {}

    public static void putStream(
            String secretId,
            String secretKey,
            String region,
            String bucket,
            String key,
            InputStream in,
            long contentLength,
            String contentType,
            boolean publicRead
    ) {
        COSClient client = newClient(secretId, secretKey, region);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            if (contentLength >= 0) {
                meta.setContentLength(contentLength);
            }
            meta.setContentType(
                    contentType == null || contentType.isBlank()
                            ? "application/octet-stream"
                            : contentType
            );
            meta.setCacheControl("public, max-age=31536000");
            PutObjectRequest req = new PutObjectRequest(bucket, key, in, meta);
            req.setCannedAcl(publicRead ? CannedAccessControlList.PublicRead : CannedAccessControlList.Private);
            client.putObject(req);
        } finally {
            client.shutdown();
        }
    }

    private static COSClient newClient(String secretId, String secretKey, String region) {
        BasicCOSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(region));
        return new COSClient(new COSStaticCredentialsProvider(cred), config);
    }
}
