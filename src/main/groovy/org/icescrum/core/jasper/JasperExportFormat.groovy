/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Minimal replacement for the Grails 2 jasper plugin's JasperExportFormat,
 * kept API-compatible with how iceScrum uses it (extension, mimeTyp,
 * determineFileFormat).
 */
package org.icescrum.core.jasper

enum JasperExportFormat {

    PDF_FORMAT("pdf", "application/pdf"),
    HTML_FORMAT("html", "text/html"),
    XML_FORMAT("xml", "text/xml"),
    CSV_FORMAT("csv", "text/csv"),
    XLS_FORMAT("xls", "application/vnd.ms-excel"),
    XLSX_FORMAT("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ODT_FORMAT("odt", "application/vnd.oasis.opendocument.text"),
    ODS_FORMAT("ods", "application/vnd.oasis.opendocument.spreadsheet"),
    DOCX_FORMAT("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    RTF_FORMAT("rtf", "application/rtf"),
    TEXT_FORMAT("txt", "text/plain")

    final String extension
    final String mimeTyp // typo kept from the original plugin API

    JasperExportFormat(String extension, String mimeTyp) {
        this.extension = extension
        this.mimeTyp = mimeTyp
    }

    static JasperExportFormat determineFileFormat(String format) {
        JasperExportFormat result = values().find { it.extension == format?.toLowerCase() }
        if (!result) {
            throw new IllegalArgumentException("Unknown report file format: ${format}")
        }
        return result
    }
}
