package com.umdc.smartkin.api.v1.to;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/// A page of results with the `PageMeta` fields of the contract.
///
/// @param page          0-based page number
/// @param size          requested page size
/// @param totalElements total number of elements
/// @param totalPages    total number of pages
/// @param content       the elements of this page
/// @param <T>           the element type
public record PageResponse<T>(int page, int size, long totalElements, int totalPages, List<T> content) {

    /// Builds the response from a Spring Data page.
    ///
    /// @param source the page
    /// @param mapper converts each element
    /// @param <E>    the source element type
    /// @param <T>    the response element type
    /// @return the response
    public static <E, T> PageResponse<T> of(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(),
                source.getContent().stream().map(mapper).toList());
    }
}
