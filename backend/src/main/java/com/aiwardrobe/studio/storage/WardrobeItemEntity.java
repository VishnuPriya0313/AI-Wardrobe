package com.aiwardrobe.studio.storage;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="wardrobe_items") @IdClass(WardrobeItemEntity.Key.class)
public class WardrobeItemEntity {
  @Id private String id;
  @Id @Column(name="user_id") private UUID userId;
  @Column(name="image_fingerprint") private String imageFingerprint;
  @Column(name="original_file_name") private String originalFileName;
  @Column(nullable=false) private String category;
  @Column(name="analysis_json", nullable=false) private String analysisJson;
  @Column(name="image_key", nullable=false) private String imageKey;
  @Column(name="metadata_key", nullable=false) private String metadataKey;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  protected WardrobeItemEntity() {}
  public WardrobeItemEntity(String id, UUID userId, String fingerprint, String filename, String category, String analysisJson, String imageKey, String metadataKey, Instant createdAt) {
    this.id=id; this.userId=userId; this.imageFingerprint=fingerprint; this.originalFileName=filename; this.category=category; this.analysisJson=analysisJson; this.imageKey=imageKey; this.metadataKey=metadataKey; this.createdAt=createdAt;
  }
  public String getId(){return id;} public UUID getUserId(){return userId;} public String getImageFingerprint(){return imageFingerprint;} public String getOriginalFileName(){return originalFileName;} public String getCategory(){return category;} public String getAnalysisJson(){return analysisJson;} public String getImageKey(){return imageKey;} public String getMetadataKey(){return metadataKey;} public Instant getCreatedAt(){return createdAt;}
  public static class Key implements Serializable { public String id; public UUID userId; public Key(){} public boolean equals(Object o){return o instanceof Key k && Objects.equals(id,k.id)&&Objects.equals(userId,k.userId);} public int hashCode(){return Objects.hash(id,userId);} }
}
