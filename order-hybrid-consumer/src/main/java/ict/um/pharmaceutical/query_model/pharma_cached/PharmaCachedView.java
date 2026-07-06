package ict.um.pharmaceutical.query_model.pharma_cached;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pharma_cached_view")
public class PharmaCachedView {
    @Id
    private String batchID;
    private String fromEntity;
    private String toEntity;
    private long finalityTimestamp;

    public PharmaCachedView(String batchID, String fromEntity, String toEntity, long finalityTimestamp) {
        this.batchID = batchID;
        this.fromEntity = fromEntity;
        this.toEntity = toEntity;
        this.finalityTimestamp = finalityTimestamp;
    }

    public PharmaCachedView() {
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public String getFromEntity() {
        return fromEntity;
    }

    public void setFromEntity(String fromEntity) {
        this.fromEntity = fromEntity;
    }

    public String getToEntity() {
        return toEntity;
    }

    public void setToEntity(String toEntity) {
        this.toEntity = toEntity;
    }

    public long getFinalityTimestamp() {
        return finalityTimestamp;
    }

    public void setFinalityTimestamp(long finalityTimestamp) {
        this.finalityTimestamp = finalityTimestamp;
    }
}
