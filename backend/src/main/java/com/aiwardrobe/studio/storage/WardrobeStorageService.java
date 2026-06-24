package com.aiwardrobe.studio.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aiwardrobe.studio.api.dto.WardrobeItemUploadRequest;
import com.aiwardrobe.studio.api.dto.WardrobeItemUploadResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class WardrobeStorageService {

  private static final Pattern DATA_URL = Pattern.compile("^data:(image/(?:png|jpeg|jpg|webp));base64,(.+)$");
  private static final String METADATA_FILE = "metadata.json";

  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final String bucket;
  private final String region;
  private final String keyPrefix;
  private final String publicBaseUrl;
  private final String endpoint;

  public WardrobeStorageService(
      ObjectMapper objectMapper,
      @Value("${app.s3.enabled}") boolean enabled,
      @Value("${app.s3.bucket}") String bucket,
      @Value("${app.s3.region}") String region,
      @Value("${app.s3.key-prefix}") String keyPrefix,
      @Value("${app.s3.public-base-url}") String publicBaseUrl,
      @Value("${app.s3.endpoint:}") String endpoint) {
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.bucket = bucket;
    this.region = region;
    this.keyPrefix = keyPrefix;
    this.publicBaseUrl = publicBaseUrl;
    this.endpoint = endpoint;
  }

  public WardrobeItemUploadResponse storeWardrobeItem(WardrobeItemUploadRequest request) {
    ensureConfigured();

    DataUrlImage image = parseImage(request.image());
    String itemKey = joinKey(keyPrefix, "items", keySegment(request.id()));
    String imageKey = itemKey + "/image." + image.extension();
    String metadataKey = itemKey + "/" + METADATA_FILE;

    try (S3Client s3 = s3Client()) {
      putObject(s3, imageKey, image.contentType(), RequestBody.fromBytes(image.bytes()));
      putObject(s3, metadataKey, "application/json", RequestBody.fromString(metadataJson(request, imageKey, metadataKey)));
    }

    return new WardrobeItemUploadResponse(true, bucket, itemKey, imageKey, metadataKey, publicUrl(imageKey));
  }

  public List<Map<String, Object>> listWardrobeItems() {
    if (!enabled) {
      return List.of();
    }
    ensureConfigured();

    List<Map<String, Object>> items = new ArrayList<>();
    try (S3Client s3 = s3Client()) {
      for (String metadataKey : metadataKeys(s3)) {
        readItem(s3, metadataKey).ifPresent(items::add);
      }
    }

    items.sort(Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.getOrDefault("createdAt", ""))).reversed());
    return items;
  }

  public void deleteWardrobeItem(String itemId) {
    if (!enabled) {
      return;
    }
    ensureConfigured();

    String itemPrefix = joinKey(keyPrefix, "items", keySegment(itemId)) + "/";
    try (S3Client s3 = s3Client()) {
      String token = null;
      do {
        String pageToken = token;
        ListObjectsV2Response page = s3.listObjectsV2(builder -> {
          builder.bucket(bucket).prefix(itemPrefix);
          if (pageToken != null) {
            builder.continuationToken(pageToken);
          }
        });
        for (S3Object object : page.contents()) {
          s3.deleteObject(builder -> builder.bucket(bucket).key(object.key()));
        }
        token = page.nextContinuationToken();
      } while (token != null);
    }
  }

  private List<String> metadataKeys(S3Client s3) {
    List<String> keys = new ArrayList<>();
    String token = null;
    String itemsPrefix = joinKey(keyPrefix, "items") + "/";

    do {
      String pageToken = token;
      ListObjectsV2Response page = s3.listObjectsV2(builder -> {
        builder.bucket(bucket).prefix(itemsPrefix);
        if (pageToken != null) {
          builder.continuationToken(pageToken);
        }
      });
      page.contents().stream()
          .map(S3Object::key)
          .filter(key -> key.endsWith("/" + METADATA_FILE))
          .forEach(keys::add);
      token = page.nextContinuationToken();
    } while (token != null);

    return keys;
  }

  private Optional<Map<String, Object>> readItem(S3Client s3, String metadataKey) {
    try {
      JsonNode metadata = objectMapper.readTree(getObjectBytes(s3, metadataKey).asString(StandardCharsets.UTF_8));
      String imageKey = metadata.path("s3").path("imageKey").asText("");
      if (imageKey.isBlank()) {
        return Optional.empty();
      }

      ResponseBytes<GetObjectResponse> image = getObjectBytes(s3, imageKey);
      String contentType = valueOrDefault(image.response().contentType(), contentTypeFromKey(imageKey));
      String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image.asByteArray());

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", metadata.path("id").asText(""));
      item.put("image", dataUrl);
      item.put("imageFingerprint", metadata.path("imageFingerprint").asText(""));
      item.put("originalFileName", metadata.path("originalFileName").asText(""));
      item.put("category", metadata.path("category").asText(""));
      item.put("createdAt", metadata.path("createdAt").asText(""));
      item.put("analysis", objectMapper.convertValue(metadata.path("analysis"), Map.class));
      item.put("cloudStorage", storageInfo(metadataKey, imageKey));
      return Optional.of(item);
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Map<String, Object> storageInfo(String metadataKey, String imageKey) {
    Map<String, Object> storage = new LinkedHashMap<>();
    storage.put("stored", true);
    storage.put("bucket", bucket);
    storage.put("itemKey", metadataKey.replaceAll("/" + METADATA_FILE + "$", ""));
    storage.put("imageKey", imageKey);
    storage.put("metadataKey", metadataKey);
    storage.put("imageUrl", publicUrl(imageKey));
    return storage;
  }

  private void putObject(S3Client s3, String key, String contentType, RequestBody body) {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        body);
  }

  private ResponseBytes<GetObjectResponse> getObjectBytes(S3Client s3, String key) {
    return s3.getObject(
        GetObjectRequest.builder().bucket(bucket).key(key).build(),
        ResponseTransformer.toBytes());
  }

  private S3Client s3Client() {
    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create());

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }

    return builder.build();
  }

  private void ensureConfigured() {
    if (!enabled) {
      throw new IllegalStateException("S3 wardrobe storage is disabled. Set APP_S3_ENABLED=true to store uploaded items.");
    }
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("APP_S3_BUCKET is missing.");
    }
    if (region == null || region.isBlank()) {
      throw new IllegalStateException("APP_S3_REGION is missing.");
    }
  }

  private DataUrlImage parseImage(String imageDataUrl) {
    Matcher matcher = DATA_URL.matcher(String.valueOf(imageDataUrl));
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Image must be a PNG, JPG, or WEBP data URL.");
    }

    try {
      String contentType = matcher.group(1).replace("image/jpg", "image/jpeg");
      return new DataUrlImage(contentType, Base64.getDecoder().decode(matcher.group(2)));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Image must contain valid base64 data.");
    }
  }

  private String metadataJson(WardrobeItemUploadRequest request, String imageKey, String metadataKey) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "id", request.id(),
          "imageFingerprint", valueOrDefault(request.imageFingerprint(), ""),
          "originalFileName", valueOrDefault(request.originalFileName(), ""),
          "category", request.category(),
          "createdAt", valueOrDefault(request.createdAt(), Instant.now().toString()),
          "analysis", request.analysis(),
          "s3", Map.of(
              "bucket", bucket,
              "region", region,
              "imageKey", imageKey,
              "metadataKey", metadataKey)));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Could not serialize wardrobe item metadata.");
    }
  }

  private String publicUrl(String imageKey) {
    if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
      return "";
    }
    return URI.create(publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/")
        .resolve(imageKey)
        .toString();
  }

  private String contentTypeFromKey(String key) {
    String lower = String.valueOf(key).toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return "image/jpeg";
  }

  private String joinKey(String... parts) {
    List<String> cleanParts = new ArrayList<>();
    for (String part : parts) {
      String clean = String.valueOf(part).trim().replace("\\", "/").replaceAll("^/+|/+$", "");
      if (!clean.isBlank()) {
        cleanParts.add(clean);
      }
    }
    return String.join("/", cleanParts);
  }

  private String keySegment(String value) {
    String clean = String.valueOf(value).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    return clean.isBlank() ? "item-" + Instant.now().toEpochMilli() : clean;
  }

  private String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record DataUrlImage(String contentType, byte[] bytes) {
    String extension() {
      return switch (contentType) {
        case "image/png" -> "png";
        case "image/webp" -> "webp";
        default -> "jpg";
      };
    }
  }
}
