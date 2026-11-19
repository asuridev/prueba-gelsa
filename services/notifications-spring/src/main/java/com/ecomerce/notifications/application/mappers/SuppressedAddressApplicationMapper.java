package com.ecomerce.notifications.application.mappers;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.dtos.GetSuppressedAddressResponseDto;
import com.ecomerce.notifications.application.dtos.ListSuppressedAddressesResponseDto;
import com.ecomerce.notifications.application.dtos.ReleaseAddressResponseDto;
import com.ecomerce.notifications.application.dtos.SuppressAddressResponseDto;
import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;

@ApplicationComponent
public class SuppressedAddressApplicationMapper {

    public SuppressAddressResponseDto toSuppressAddressResponseDto(SuppressedAddress entity) {
        return new SuppressAddressResponseDto(
                entity.getId(),
                entity.getAddress(),
                entity.getReason(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getSuppressedAt(),
                entity.getReleasedAt());
    }

    public ReleaseAddressResponseDto toReleaseAddressResponseDto(SuppressedAddress entity) {
        return new ReleaseAddressResponseDto(
                entity.getId(),
                entity.getAddress(),
                entity.getReason(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getSuppressedAt(),
                entity.getReleasedAt());
    }

    public GetSuppressedAddressResponseDto toGetSuppressedAddressResponseDto(SuppressedAddress entity) {
        return new GetSuppressedAddressResponseDto(
                entity.getId(),
                entity.getAddress(),
                entity.getReason(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getSuppressedAt(),
                entity.getReleasedAt());
    }

    public ListSuppressedAddressesResponseDto toListSuppressedAddressesResponseDto(SuppressedAddress entity) {
        return new ListSuppressedAddressesResponseDto(
                entity.getId(),
                entity.getAddress(),
                entity.getReason(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getSuppressedAt(),
                entity.getReleasedAt());
    }
}
