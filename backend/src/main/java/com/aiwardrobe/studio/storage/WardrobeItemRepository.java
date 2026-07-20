package com.aiwardrobe.studio.storage;
import java.util.List; import java.util.Optional; import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WardrobeItemRepository extends JpaRepository<WardrobeItemEntity, WardrobeItemEntity.Key> {
  List<WardrobeItemEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<WardrobeItemEntity> findByIdAndUserId(String id, UUID userId);
}
