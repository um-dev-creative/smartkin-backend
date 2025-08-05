package com.umdc.smartkin.jpa.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "points", schema = "smartkin")
public class Point {
    @Id
    @ColumnDefault("smartkin.uuid_generate_v4()")
    @Column(name = "point_id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildEntity child;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Size(max = 50)
    @NotNull
    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @NotNull
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;
    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChildEntity getChild() {
        return child;
    }

    public void setChild(ChildEntity child) {
        this.child = child;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

/*
 TODO [Reverse Engineering] create field to map the 'transaction_type' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @Column(name = "transaction_type", columnDefinition = "transaction_type not null")
    private Object transactionType;
*/
}
