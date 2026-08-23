package com.example.vex360.shared.enums;

/**
 * Kho lưu trữ đang giữ một tệp. Cần biết để xoá đúng chỗ: khoá của R2 đưa cho Cloudinary
 * thì Cloudinary không tìm thấy, không báo lỗi, và tệp nằm lại vĩnh viễn.
 */
public enum StorageProvider {
    CLOUDINARY,
    R2
}
