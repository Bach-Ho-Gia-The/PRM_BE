package com.structure.springboot.infrastructure.external.cloudinary;

public record ImageUploadResult (
        String url,
        String publicId
) {
}
