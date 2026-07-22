package com.aiwardrobe.studio.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class R2ConnectivityCheckTest {

  @Test
  @EnabledIfSystemProperty(named = "r2.check", matches = "true")
  void configuredBucketSupportsListWriteReadAndDelete() {
    String bucket = requiredEnvironment("APP_S3_BUCKET");
    String endpoint = requiredEnvironment("APP_S3_ENDPOINT");
    String region = environmentOrDefault("APP_S3_REGION", "auto");
    String accessKeyId = requiredEnvironment("AWS_ACCESS_KEY_ID");
    String secretAccessKey = requiredEnvironment("AWS_SECRET_ACCESS_KEY");
    String keyPrefix = environmentOrDefault("APP_S3_KEY_PREFIX", "wardrobe-uploads");
    String key = keyPrefix.replaceAll("^/+|/+$", "")
        + "/diagnostics/connectivity-" + UUID.randomUUID() + ".txt";
    byte[] expected = "AI Wardrobe R2 connectivity check".getBytes(StandardCharsets.UTF_8);

    try (S3Client s3 = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
        .endpointOverride(URI.create(endpoint))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()) {
      s3.listObjectsV2(builder -> builder.bucket(bucket).maxKeys(1));
      try {
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/plain").build(),
            RequestBody.fromBytes(expected));
        ResponseBytes<GetObjectResponse> stored = s3.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build(),
            ResponseTransformer.toBytes());
        assertThat(stored.asByteArray()).isEqualTo(expected);
      } finally {
        s3.deleteObject(builder -> builder.bucket(bucket).key(key));
      }
    }
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertThat(value).as(name + " must be set").isNotBlank();
    return value;
  }

  private String environmentOrDefault(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
