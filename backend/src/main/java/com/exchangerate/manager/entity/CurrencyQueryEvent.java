package com.exchangerate.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Append-only event log of currency queries; unlike CurrencyUsage, currency_code is intentionally
// not unique and queried_at is set explicitly (via a native now() insert) rather than DB-defaulted.
@Entity
@Table(name = "currency_query_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyQueryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Pattern(regexp = "^[A-Z]{3}$")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @NotNull
    @Column(name = "queried_at", nullable = false)
    private Instant queriedAt;
}
